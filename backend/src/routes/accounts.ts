import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Account } from '../repositories/store';
import { calculateAccountBalance } from '../services/balance';

export const accountsRouter = Router();

// GET all accounts for user with live calculated balance
accountsRouter.get('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const userAccounts = Array.from(store.accounts.values()).filter(a => a.userId === userId);
  const userTxs = Array.from(store.transactions.values()).filter(t => t.userId === userId);

  const result = userAccounts.map(acc => {
    const calc = calculateAccountBalance(acc, userTxs);
    return {
      ...acc,
      currentBalanceMinor: calc.currentBalanceMinor
    };
  });

  res.status(200).json({ success: true, data: result });
});

// POST create account
accountsRouter.post('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const { name, type, openingBalanceMinor, currencyCode } = req.body;

  if (!name || !type) {
    res.status(400).json({ success: false, error: { message: 'Name and type are required' } });
    return;
  }

  const newAccount: Account = {
    id: `acc-${Date.now()}`,
    userId,
    name,
    type,
    openingBalanceMinor: openingBalanceMinor || 0,
    currencyCode: currencyCode || 'INR',
    createdAt: new Date().toISOString()
  };

  store.accounts.set(newAccount.id, newAccount);
  res.status(201).json({ success: true, data: newAccount });
});
