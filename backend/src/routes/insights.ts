import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store } from '../repositories/store';

export const insightsRouter = Router();

insightsRouter.get('/summary', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const userTxs = Array.from(store.transactions.values()).filter(t => t.userId === userId && t.status === 'completed');

  let totalReceivedMinor = 0;
  let totalExpenseMinor = 0;
  const categoryTotals: { [key: string]: number } = {};

  for (const tx of userTxs) {
    if (tx.type === 'received') {
      totalReceivedMinor += tx.amountMinor;
    } else if (tx.type === 'expense') {
      totalExpenseMinor += tx.amountMinor;
      const catId = tx.categoryId || 'uncategorized';
      categoryTotals[catId] = (categoryTotals[catId] || 0) + tx.amountMinor;
    }
  }

  const categoryBreakdown = Object.keys(categoryTotals).map(catId => {
    const cat = store.categories.get(catId);
    const amountMinor = categoryTotals[catId];
    const percentage = totalExpenseMinor > 0 ? Math.round((amountMinor / totalExpenseMinor) * 100) : 0;
    return {
      categoryId: catId,
      categoryName: cat ? cat.name : 'Others',
      colorHex: cat ? cat.colorHex : '#6B7280',
      amountMinor,
      percentage
    };
  });

  res.status(200).json({
    success: true,
    data: {
      totalReceivedMinor,
      totalExpenseMinor,
      totalSavingsMinor: totalReceivedMinor - totalExpenseMinor,
      categoryBreakdown
    }
  });
});
