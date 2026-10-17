import { Router, Response } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Transaction } from '../repositories/store';
import { dataAdapter } from '../repositories/dataAdapter';

export const syncRouter = Router();

// PUSH local offline operations / entities into cloud
syncRouter.post('/push', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const { operations, accounts, categories, transactions, trips, savingGoals, upcomingExpenses, deletedTransactionIds } = req.body;

  // Process any deleted transaction IDs
  if (Array.isArray(deletedTransactionIds)) {
    for (const delId of deletedTransactionIds) {
      try {
        await dataAdapter.deleteTransaction(delId, userId);
      } catch (e) {
        console.warn('Sync delete transaction error:', delId, e);
      }
    }
  }

  // Support multi-entity push
  if (accounts || categories || transactions || trips || savingGoals || upcomingExpenses || deletedTransactionIds) {
    const result = await dataAdapter.pushMultiEntity(userId, {
      accounts,
      categories,
      transactions,
      trips,
      savingGoals,
      upcomingExpenses
    });

    return res.status(200).json({
      success: true,
      data: result,
      deletedCount: Array.isArray(deletedTransactionIds) ? deletedTransactionIds.length : 0
    });
  }

  // Support legacy operations array format
  if (Array.isArray(operations)) {
    const syncedIds: string[] = [];
    const rejectedIds: string[] = [];

    for (const op of operations) {
      try {
        if (op.action === 'insert' || op.action === 'upsert') {
          const txData = op.data;
          // Verify idempotency
          if (txData.idempotencyKey) {
            const existing = Array.from(store.transactions.values()).find(t => t.idempotencyKey === txData.idempotencyKey);
            if (existing) {
              syncedIds.push(txData.id);
              continue;
            }
          }

          const newTx: Transaction = {
            id: txData.id || `tx-${Date.now()}`,
            userId,
            accountId: txData.accountId,
            type: txData.type,
            amountMinor: txData.amountMinor,
            currencyCode: txData.currencyCode || 'INR',
            categoryId: txData.categoryId || null,
            description: txData.description || '',
            transactionDate: txData.transactionDate || new Date().toISOString(),
            status: 'completed',
            idempotencyKey: txData.idempotencyKey,
            relatedPerson: txData.relatedPerson,
            destinationAccountId: txData.destinationAccountId,
            expenseGroup: txData.expenseGroup || 'GENERAL',
            tripId: txData.tripId || null,
            isImportant: txData.isImportant || false,
            createdAt: txData.createdAt || new Date().toISOString()
          };
          await dataAdapter.createTransaction(newTx);
          syncedIds.push(txData.id);
        }
      } catch (e) {
        rejectedIds.push(op.data?.id);
      }
    }

    return res.status(200).json({
      success: true,
      data: {
        syncedCount: syncedIds.length,
        syncedIds,
        rejectedIds
      }
    });
  }

  return res.status(400).json({
    success: false,
    error: { message: 'Invalid payload: either entities object or operations array required' }
  });
});

// PULL latest changes since timestamp
syncRouter.get('/pull', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const since = req.query.since as string | undefined;

  const pullResult = await dataAdapter.pullMultiEntity(userId, since);

  res.status(200).json({
    success: true,
    data: pullResult
  });
});
