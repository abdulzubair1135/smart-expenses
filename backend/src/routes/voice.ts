import { Router, Response } from 'express';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { qwenProvider } from '../services/ai/qwenProvider';

export const voiceRouter = Router();

// POST /api/v1/voice/parse
voiceRouter.post('/parse', authenticateUser, async (req: AuthenticatedRequest, res: Response) => {
  const { transcript, correlationId } = req.body;
  const corrId = correlationId || (req.headers['x-correlation-id'] as string);

  if (!transcript || typeof transcript !== 'string') {
    res.status(400).json({
      success: false,
      error: { message: 'Voice transcript string is required' }
    });
    return;
  }

  try {
    const result = await qwenProvider.parseTranscript(transcript, req.user?.id, corrId);
    res.status(200).json({
      success: true,
      data: result
    });
  } catch (err: any) {
    res.status(500).json({
      success: false,
      error: { message: 'Voice command processing failed', details: err?.message }
    });
  }
});
