import { Router, Response } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Transaction } from '../repositories/store';

export const syncRouter = Router();

// PUSH local offline operations into cloud
syncRouter.post('/push', authenticateUser, (req: AuthenticatedRequest, res: Response) => {

  const userId = req.user!.id;
  const { operations } = req.body; // array of { action: 'insert'|'update'|'delete', data: Transaction }

  if (!Array.isArray(operations)) {
    res.status(400).json({ success: false, error: { message: 'Operations array is required' } });
    return;
  }

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
          createdAt: txData.createdAt || new Date().toISOString()
        };
        store.transactions.set(newTx.id, newTx);
        syncedIds.push(txData.id);
      }
    } catch (e) {
      rejectedIds.push(op.data?.id);
    }
  }

  res.status(200).json({
    success: true,
    data: {
      syncedCount: syncedIds.length,
      syncedIds,
      rejectedIds
    }
  });
});

// PULL latest changes since timestamp
syncRouter.get('/pull', authenticateUser, (req: AuthenticatedRequest, res: Response) => {

  const userId = req.user!.id;
  const since = req.query.since as string;

  let txs = Array.from(store.transactions.values()).filter(t => t.userId === userId);
  if (since) {
    const sinceDate = new Date(since).getTime();
    txs = txs.filter(t => new Date(t.createdAt).getTime() > sinceDate);
  }

  res.status(200).json({
    success: true,
    data: {
      transactions: txs,
      serverTime: new Date().toISOString()
    }
  });
});
