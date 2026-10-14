import express, { Application, Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import { healthRouter } from './routes/health';
import { authRouter } from './routes/auth';
import { accountsRouter } from './routes/accounts';
import { categoriesRouter } from './routes/categories';
import { transactionsRouter } from './routes/transactions';
import { dashboardRouter } from './routes/dashboard';
import { insightsRouter } from './routes/insights';
import { syncRouter } from './routes/sync';
import { adminRouter } from './routes/admin';
import { voiceRouter } from './routes/voice';
import { tripsRouter } from './routes/trips';
import { expensesRouter } from './routes/expenses';
import { presenceRouter } from './routes/presence';
import { backupsRouter } from './routes/backups';

export function createApp(): Application {
  const app = express();

  app.use(helmet());
  app.use(cors());
  app.use(express.json());
  app.use(morgan('dev'));

  // Route mounting
  app.use('/health', healthRouter);
  app.use('/api/v1/auth', authRouter);
  app.use('/api/v1/profile', authRouter);
  app.use('/api/v1/accounts', accountsRouter);
  app.use('/api/v1/categories', categoriesRouter);
  app.use('/api/v1/transactions', transactionsRouter);
  app.use('/api/v1/dashboard', dashboardRouter);
  app.use('/api/v1/insights', insightsRouter);
  app.use('/api/v1/sync', syncRouter);
  app.use('/api/v1/admin', adminRouter);
  app.use('/api/v1/voice', voiceRouter);
  app.use('/api/v1/trips', tripsRouter);
  app.use('/api/v1/expenses', expensesRouter);
  app.use('/api/v1/presence', presenceRouter);
  app.use('/api/v1/backups', backupsRouter);

  // Central error handler
  app.use((err: any, req: Request, res: Response, next: NextFunction) => {
    console.error('API Error:', err);
    res.status(err.status || 500).json({
      success: false,
      error: {
        code: err.code || 'INTERNAL_SERVER_ERROR',
        message: err.message || 'An unexpected error occurred'
      }
    });
  });

  return app;
}
