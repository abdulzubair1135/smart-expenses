import { Router } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store } from '../repositories/store';

export const presenceRouter = Router();

// Periodic heartbeat sent by the Android client / web client
presenceRouter.post('/heartbeat', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const clientPlatform = (req.body && req.body.clientPlatform) ? String(req.body.clientPlatform) : 'android';

  store.updatePresence(userId, clientPlatform);

  res.status(200).json({
    success: true,
    message: 'Heartbeat acknowledged',
    data: {
      userId,
      clientPlatform,
      timestamp: new Date().toISOString()
    }
  });
});
