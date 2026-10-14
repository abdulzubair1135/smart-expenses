import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Transaction } from '../repositories/store';

export const transactionsRouter = Router();

// GET transactions with search, filter by type/category/account
transactionsRouter.get('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const { type, accountId, categoryId, search } = req.query;

  let txs = Array.from(store.transactions.values()).filter(t => t.userId === userId);

  if (type && typeof type === 'string') {
    txs = txs.filter(t => t.type === type);
  }
  if (accountId && typeof accountId === 'string') {
    txs = txs.filter(t => t.accountId === accountId || t.destinationAccountId === accountId);
  }
  if (categoryId && typeof categoryId === 'string') {
    txs = txs.filter(t => t.categoryId === categoryId);
  }
  if (search && typeof search === 'string') {
    const q = search.toLowerCase();
    txs = txs.filter(t => (t.description && t.description.toLowerCase().includes(q)) || (t.relatedPerson && t.relatedPerson.toLowerCase().includes(q)));
  }

  // Sort by date descending
  txs.sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime());

  res.status(200).json({ success: true, data: txs });
});

// POST create transaction
transactionsRouter.post('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const {
    accountId,
    type,
    amountMinor,
    currencyCode,
    categoryId,
    description,
    transactionDate,
    relatedPerson,
    destinationAccountId,
    idempotencyKey
  } = req.body;

  if (!accountId || !type || !amountMinor || amountMinor <= 0) {
    res.status(400).json({ success: false, error: { message: 'Valid accountId, type, and positive amountMinor are required' } });
    return;
  }

  // Check idempotency if key provided
  if (idempotencyKey) {
    const existing = Array.from(store.transactions.values()).find(t => t.idempotencyKey === idempotencyKey);
    if (existing) {
      res.status(200).json({ success: true, data: existing, idempotent: true });
      return;
    }
  }

  const newTx: Transaction = {
    id: `tx-${Date.now()}`,
    userId,
    accountId,
    type,
    amountMinor,
    currencyCode: currencyCode || 'INR',
    categoryId: categoryId || null,
    description: description || '',
    transactionDate: transactionDate || new Date().toISOString(),
    status: 'completed',
    idempotencyKey,
    relatedPerson,
    destinationAccountId,
    createdAt: new Date().toISOString()
  };

  store.transactions.set(newTx.id, newTx);
  res.status(201).json({ success: true, data: newTx });
});

// DELETE transaction
transactionsRouter.delete('/:id', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const id = req.params.id as string;

  const tx = store.transactions.get(id);
  if (!tx || tx.userId !== userId) {
    res.status(404).json({ success: false, error: { message: 'Transaction not found or unauthorized' } });
    return;
  }

  store.transactions.delete(id);
  res.status(200).json({ success: true, message: 'Transaction deleted' });
});
