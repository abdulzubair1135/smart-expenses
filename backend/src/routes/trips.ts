import { Router, Response } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Trip } from '../repositories/store';
import { dataAdapter } from '../repositories/dataAdapter';
import { z } from 'zod';
import crypto from 'crypto';

export const tripsRouter = Router();

const createTripSchema = z.object({
  name: z.string().min(1, 'Trip name is required').max(150),
  destination: z.string().min(1, 'Destination is required').max(150),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Start date must be YYYY-MM-DD'),
  endDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'End date must be YYYY-MM-DD'),
  description: z.string().optional()
});

const updateTripSchema = createTripSchema.partial().extend({
  status: z.enum(['active', 'completed', 'archived']).optional()
});

// 1. GET /api/v1/trips
tripsRouter.get('/', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const statusFilter = req.query.status as string | undefined;

  let trips = await dataAdapter.getTrips(userId);
  if (statusFilter) {
    trips = trips.filter(t => t.status === statusFilter);
  }

  // Calculate total spending for each trip dynamically from completed transactions
  const transactions = await dataAdapter.getTransactions(userId);
  const tripsWithMetrics = trips.map(trip => {
    const tripTransactions = transactions.filter(
      tx => tx.tripId === trip.id && tx.status === 'completed' && tx.type === 'expense'
    );
    const totalSpentMinor = tripTransactions.reduce((sum, tx) => sum + tx.amountMinor, 0);

    return {
      ...trip,
      totalSpentMinor,
      transactionCount: tripTransactions.length
    };
  });

  // Sort by startDate descending
  tripsWithMetrics.sort((a, b) => b.startDate.localeCompare(a.startDate));

  res.json({
    success: true,
    data: tripsWithMetrics
  });
});

// 2. POST /api/v1/trips
tripsRouter.post('/', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const parseResult = createTripSchema.safeParse(req.body);
  if (!parseResult.success) {
    return res.status(400).json({
      success: false,
      error: { code: 'VALIDATION_ERROR', message: parseResult.error.errors[0].message }
    });
  }

  const userId = req.user!.id;
  const { name, destination, startDate, endDate, description } = parseResult.data;

  if (startDate > endDate) {
    return res.status(400).json({
      success: false,
      error: { code: 'INVALID_DATE_RANGE', message: 'Start date cannot be after end date' }
    });
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  const newTrip: Trip = {
    id,
    userId,
    name,
    destination,
    startDate,
    endDate,
    description: description || '',
    status: 'active',
    createdAt: now,
    updatedAt: now
  };

  await dataAdapter.createTrip(newTrip);
  await dataAdapter.recordActivity(userId, 'TRIP_CREATED', 'success', { tripId: id, name });

  res.status(201).json({
    success: true,
    data: newTrip
  });
});

// 3. GET /api/v1/trips/:id
tripsRouter.get('/:id', authenticateUser, (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const tripId = req.params.id as string;
  const trip = store.trips.get(tripId);

  if (!trip || trip.userId !== userId) {
    return res.status(404).json({
      success: false,
      error: { code: 'TRIP_NOT_FOUND', message: 'Trip not found or unauthorized' }
    });
  }

  const tripTransactions = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId && tx.tripId === trip.id && tx.status === 'completed' && tx.type === 'expense'
  );
  const totalSpentMinor = tripTransactions.reduce((sum, tx) => sum + tx.amountMinor, 0);

  // Category breakdown
  const categoryBreakdown: Record<string, number> = {};
  tripTransactions.forEach(tx => {
    const catId = tx.categoryId || 'cat-others';
    categoryBreakdown[catId] = (categoryBreakdown[catId] || 0) + tx.amountMinor;
  });

  res.json({
    success: true,
    data: {
      ...trip,
      totalSpentMinor,
      transactionCount: tripTransactions.length,
      categoryBreakdown
    }
  });
});

// 4. PATCH /api/v1/trips/:id
tripsRouter.patch('/:id', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const tripId = req.params.id as string;
  const trip = store.trips.get(tripId);

  if (!trip || trip.userId !== userId) {
    return res.status(404).json({
      success: false,
      error: { code: 'TRIP_NOT_FOUND', message: 'Trip not found or unauthorized' }
    });
  }

  const parseResult = updateTripSchema.safeParse(req.body);
  if (!parseResult.success) {
    return res.status(400).json({
      success: false,
      error: { code: 'VALIDATION_ERROR', message: parseResult.error.errors[0].message }
    });
  }

  const updates = parseResult.data;
  const updatedTrip: Trip = {
    ...trip,
    ...updates,
    updatedAt: new Date().toISOString()
  };

  await dataAdapter.updateTrip(updatedTrip);

  res.json({
    success: true,
    data: updatedTrip
  });
});

// 5. DELETE /api/v1/trips/:id (Safe archive if transactions linked)
tripsRouter.delete('/:id', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const tripId = req.params.id as string;
  const trip = store.trips.get(tripId);

  if (!trip || trip.userId !== userId) {
    return res.status(404).json({
      success: false,
      error: { code: 'TRIP_NOT_FOUND', message: 'Trip not found or unauthorized' }
    });
  }

  const linkedTransactions = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId && tx.tripId === trip.id
  );

  if (linkedTransactions.length > 0) {
    // Preserve financial history by marking as archived rather than deleting
    trip.status = 'archived';
    trip.updatedAt = new Date().toISOString();
    await dataAdapter.updateTrip(trip);
    return res.json({
      success: true,
      message: 'Trip has linked financial transactions and was safely archived rather than deleted',
      data: trip
    });
  }

  await dataAdapter.deleteTrip(trip.id);
  res.json({
    success: true,
    message: 'Trip deleted successfully'
  });
});

// 6. GET /api/v1/trips/:id/transactions
tripsRouter.get('/:id/transactions', authenticateUser, (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const tripId = req.params.id as string;
  const trip = store.trips.get(tripId);

  if (!trip || trip.userId !== userId) {
    return res.status(404).json({
      success: false,
      error: { code: 'TRIP_NOT_FOUND', message: 'Trip not found or unauthorized' }
    });
  }

  const transactions = Array.from(store.transactions.values())
    .filter(tx => tx.userId === userId && tx.tripId === trip.id)
    .sort((a, b) => b.transactionDate.localeCompare(a.transactionDate));

  res.json({
    success: true,
    data: transactions
  });
});
