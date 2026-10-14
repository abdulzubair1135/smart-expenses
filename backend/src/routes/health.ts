import { Router } from 'express';
import { store } from '../repositories/store';

export const healthRouter = Router();

healthRouter.get('/', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    service: 'smart-expenses-backend',
    version: '1.0.0'
  });
});

healthRouter.get('/ready', (req, res) => {
  // Check repository readiness
  const isReady = store.profiles.size > 0;
  if (isReady) {
    res.status(200).json({
      status: 'ready',
      database: 'connected',
      uptime: process.uptime()
    });
  } else {
    res.status(503).json({
      status: 'degraded',
      database: 'initializing'
    });
  }
});
