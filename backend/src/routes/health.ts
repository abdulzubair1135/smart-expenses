import { Router, Request, Response } from 'express';
import { probeDatabaseConnectivity } from '../services/supabase';

export const healthRouter = Router();

// Liveness check: Verifies backend process is running and accepting HTTP requests
healthRouter.get('/', (req: Request, res: Response) => {
  res.status(200).json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    service: 'smart-expenses-backend',
    version: '1.0.0',
    uptimeSeconds: Math.floor(process.uptime())
  });
});

// Readiness check: Probes live database connectivity without leaking credentials
healthRouter.get('/ready', async (req: Request, res: Response) => {
  try {
    const dbProbe = await probeDatabaseConnectivity();

    if (dbProbe.connected) {
      res.status(200).json({
        status: 'ready',
        backend: 'operational',
        database: {
          status: 'connected',
          latencyMs: dbProbe.latencyMs
        },
        uptimeSeconds: Math.floor(process.uptime())
      });
      return;
    }

    if (!dbProbe.configured) {
      // Backend is ready to serve requests using resilient storage
      res.status(200).json({
        status: 'ready',
        backend: 'operational',
        database: {
          status: 'unconfigured',
          mode: 'resilient_in_memory'
        },
        uptimeSeconds: Math.floor(process.uptime())
      });
      return;
    }

    // Configured database is unreachable
    res.status(503).json({
      status: 'degraded',
      backend: 'operational',
      database: {
        status: 'disconnected',
        error: dbProbe.message
      },
      uptimeSeconds: Math.floor(process.uptime())
    });
  } catch (error: any) {
    res.status(503).json({
      status: 'degraded',
      backend: 'operational',
      database: {
        status: 'disconnected',
        error: error.message || 'Readiness probe failed'
      }
    });
  }
});
