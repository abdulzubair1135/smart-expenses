import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store } from '../repositories/store';
import { calculateUserTotalBalance } from '../services/balance';

export const dashboardRouter = Router();

dashboardRouter.get('/summary', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const userAccounts = Array.from(store.accounts.values()).filter(a => a.userId === userId);
  const userTxs = Array.from(store.transactions.values()).filter(t => t.userId === userId);

  const balance = calculateUserTotalBalance(userAccounts, userTxs);

  // Today's summary
  const todayStr = new Date().toISOString().slice(0, 10);
  let todayExpenseMinor = 0;
  let todayIncomeMinor = 0;

  for (const tx of userTxs) {
    if (tx.status === 'completed' && tx.transactionDate.startsWith(todayStr)) {
      if (tx.type === 'expense') todayExpenseMinor += tx.amountMinor;
      if (tx.type === 'received') todayIncomeMinor += tx.amountMinor;
    }
  }

  // Recent transactions (limit 5)
  const recent = [...userTxs]
    .sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime())
    .slice(0, 5);

  // Active upcoming expense
  const upcoming = Array.from(store.upcomingExpenses.values()).find(u => u.userId === userId && u.status === 'pending');

  // Active goal
  const goal = Array.from(store.savingGoals.values()).find(g => g.userId === userId);

  res.status(200).json({
    success: true,
    data: {
      availableBalanceMinor: balance.totalAvailableMinor,
      todayExpenseMinor,
      todayIncomeMinor,
      totalIncomeMinor: balance.totalIncomeMinor,
      totalExpenseMinor: balance.totalExpenseMinor,
      recentTransactions: recent,
      upcomingPayment: upcoming || null,
      activeGoal: goal || null
    }
  });
});
