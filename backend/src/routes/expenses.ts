import { Router, Response } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store } from '../repositories/store';

export const expensesRouter = Router();

// 1. GET /api/v1/expenses/summary/daily?date=YYYY-MM-DD
expensesRouter.get('/summary/daily', authenticateUser, (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const targetDate = (req.query.date as string) || new Date().toISOString().split('T')[0];

  const dailyExpenses = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId &&
          tx.type === 'expense' &&
          tx.status === 'completed' &&
          tx.transactionDate &&
          tx.transactionDate.startsWith(targetDate)
  );

  const totalExpenseMinor = dailyExpenses.reduce((sum, tx) => sum + tx.amountMinor, 0);

  // Category breakdown
  const categoryBreakdown: Record<string, number> = {};
  dailyExpenses.forEach(tx => {
    const cat = tx.categoryId || 'cat-others';
    categoryBreakdown[cat] = (categoryBreakdown[cat] || 0) + tx.amountMinor;
  });

  // Calculate previous day for comparison
  const prevDateObj = new Date(targetDate);
  prevDateObj.setDate(prevDateObj.getDate() - 1);
  const prevDateStr = prevDateObj.toISOString().split('T')[0];

  const prevDayExpenses = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId &&
          tx.type === 'expense' &&
          tx.status === 'completed' &&
          tx.transactionDate &&
          tx.transactionDate.startsWith(prevDateStr)
  );
  const prevDayTotalMinor = prevDayExpenses.reduce((sum, tx) => sum + tx.amountMinor, 0);

  res.json({
    success: true,
    data: {
      date: targetDate,
      totalExpenseMinor,
      transactionCount: dailyExpenses.length,
      categoryBreakdown,
      transactions: dailyExpenses,
      comparison: {
        previousDate: prevDateStr,
        previousDayTotalMinor: prevDayTotalMinor,
        differenceMinor: totalExpenseMinor - prevDayTotalMinor
      }
    }
  });
});

// 2. GET /api/v1/expenses/summary/monthly?month=YYYY-MM
expensesRouter.get('/summary/monthly', authenticateUser, (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const targetMonth = (req.query.month as string) || new Date().toISOString().slice(0, 7); // e.g. "2026-10"

  const monthlyExpenses = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId &&
          tx.type === 'expense' &&
          tx.status === 'completed' &&
          tx.transactionDate &&
          tx.transactionDate.startsWith(targetMonth)
  );

  const totalExpenseMinor = monthlyExpenses.reduce((sum, tx) => sum + tx.amountMinor, 0);

  // Category breakdown
  const categoryBreakdown: Record<string, number> = {};
  monthlyExpenses.forEach(tx => {
    const cat = tx.categoryId || 'cat-others';
    categoryBreakdown[cat] = (categoryBreakdown[cat] || 0) + tx.amountMinor;
  });

  // Previous month calculation
  const [yearStr, monthStr] = targetMonth.split('-');
  let prevYear = parseInt(yearStr, 10);
  let prevMonth = parseInt(monthStr, 10) - 1;
  if (prevMonth < 1) {
    prevMonth = 12;
    prevYear -= 1;
  }
  const prevMonthStr = `${prevYear}-${String(prevMonth).padStart(2, '0')}`;

  const prevMonthExpenses = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId &&
          tx.type === 'expense' &&
          tx.status === 'completed' &&
          tx.transactionDate &&
          tx.transactionDate.startsWith(prevMonthStr)
  );
  const prevMonthTotalMinor = prevMonthExpenses.reduce((sum, tx) => sum + tx.amountMinor, 0);

  res.json({
    success: true,
    data: {
      month: targetMonth,
      totalExpenseMinor,
      transactionCount: monthlyExpenses.length,
      categoryBreakdown,
      comparison: {
        previousMonth: prevMonthStr,
        previousMonthTotalMinor: prevMonthTotalMinor,
        differenceMinor: totalExpenseMinor - prevMonthTotalMinor
      }
    }
  });
});

// 3. GET /api/v1/expenses/summary/travel
expensesRouter.get('/summary/travel', authenticateUser, (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;

  const travelExpenses = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId &&
          tx.type === 'expense' &&
          tx.status === 'completed' &&
          (tx.expenseGroup === 'TRAVEL' || tx.tripId != null)
  );

  const totalSpentMinor = travelExpenses.reduce((sum, tx) => sum + tx.amountMinor, 0);

  const trips = Array.from(store.trips.values()).filter(t => t.userId === userId);

  res.json({
    success: true,
    data: {
      totalSpentMinor,
      transactionCount: travelExpenses.length,
      tripCount: trips.length,
      activeTripsCount: trips.filter(t => t.status === 'active').length,
      completedTripsCount: trips.filter(t => t.status === 'completed').length,
      transactions: travelExpenses
    }
  });
});

// 4. GET /api/v1/expenses/summary/special
expensesRouter.get('/summary/special', authenticateUser, (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;

  const specialExpenses = Array.from(store.transactions.values()).filter(
    tx => tx.userId === userId &&
          tx.type === 'expense' &&
          tx.status === 'completed' &&
          tx.expenseGroup === 'SPECIAL'
  );

  const totalSpentMinor = specialExpenses.reduce((sum, tx) => sum + tx.amountMinor, 0);

  const categoryBreakdown: Record<string, number> = {};
  specialExpenses.forEach(tx => {
    const cat = tx.categoryId || 'cat-others';
    categoryBreakdown[cat] = (categoryBreakdown[cat] || 0) + tx.amountMinor;
  });

  res.json({
    success: true,
    data: {
      totalSpentMinor,
      transactionCount: specialExpenses.length,
      categoryBreakdown,
      transactions: specialExpenses
    }
  });
});
