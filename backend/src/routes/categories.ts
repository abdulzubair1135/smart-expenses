import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, Category } from '../repositories/store';

export const categoriesRouter = Router();

categoriesRouter.get('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  // Shared categories (userId is null) + user's own custom categories
  const categories = Array.from(store.categories.values()).filter(
    c => c.userId === null || c.userId === userId
  );
  res.status(200).json({ success: true, data: categories });
});

categoriesRouter.post('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const { name, type, iconKey, colorHex } = req.body;

  if (!name || !type) {
    res.status(400).json({ success: false, error: { message: 'Category name and type required' } });
    return;
  }

  const newCategory: Category = {
    id: `cat-${Date.now()}`,
    userId,
    name,
    type,
    iconKey: iconKey || 'ic_other',
    colorHex: colorHex || '#10B981',
    createdAt: new Date().toISOString()
  };

  store.categories.set(newCategory.id, newCategory);
  res.status(201).json({ success: true, data: newCategory });
});
