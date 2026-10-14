import { Request, Response, NextFunction } from 'express';
import { config } from '../config';

export const authenticateAdmin = (req: Request, res: Response, next: NextFunction): void => {
  const adminSecret = req.headers['x-admin-key'] || req.headers['authorization']?.replace('Bearer ', '');

  if (!adminSecret || adminSecret !== config.admin.secretKey) {
    res.status(403).json({
      success: false,
      error: { code: 'FORBIDDEN', message: 'Administrator credentials required' }
    });
    return;
  }

  next();
};
