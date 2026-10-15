import { Router, Request, Response } from 'express';
import { z } from 'zod';
import { dataAdapter } from '../repositories/dataAdapter';
import { MonitoringEvent } from '../repositories/store';
import { probeDatabaseConnectivity } from '../services/supabase';

export const monitoringRouter = Router();

// Middleware to verify admin key for administrative monitoring endpoints
const requireAdminKey = (req: Request, res: Response, next: Function) => {
  const adminKey = req.headers['x-admin-key'] as string;
  const configuredKey = process.env.ADMIN_SECRET_KEY || 'smart-expenses-admin-secret-2026';

  if (!adminKey || adminKey !== configuredKey) {
    return res.status(403).json({
      success: false,
      error: { code: 'FORBIDDEN', message: 'Valid x-admin-key header required for monitoring administration' }
    });
  }
  next();
};

// Sanitization regexes to scrub accidental sensitive tokens/passwords
const SENSITIVE_PATTERNS = [
  /password["':\s]+([^"',\s]+)/gi,
  /bearer\s+([a-zA-Z0-9_\-\.]+)/gi,
  /token["':\s]+([^"',\s]+)/gi,
  /\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b/g // Credit card format
];

function sanitizeString(str: string): string {
  if (!str) return str;
  let sanitized = str;
  // Maximum length restriction for stack traces and messages (32KB max)
  if (sanitized.length > 32768) {
    sanitized = sanitized.substring(0, 32768) + '... [TRUNCATED]';
  }
  for (const pattern of SENSITIVE_PATTERNS) {
    sanitized = sanitized.replace(pattern, '[REDACTED]');
  }
  return sanitized;
}

const singleEventSchema = z.object({
  id: z.string().uuid().or(z.string().min(5)),
  eventType: z.string().min(1).max(64),
  severity: z.enum(['INFO', 'WARNING', 'ERROR', 'CRITICAL']),
  source: z.enum(['ANDROID', 'BACKEND', 'DATABASE', 'SYNC', 'ADMIN']),
  errorCode: z.string().max(64).default('UNKNOWN_ERROR'),
  message: z.string().min(1).max(5000),
  stackTrace: z.string().optional(),
  screenName: z.string().max(128).optional(),
  operationName: z.string().max(128).optional(),
  appVersion: z.string().max(32).default('1.0.0'),
  appBuild: z.string().max(32).default('1'),
  androidVersion: z.string().max(32).optional(),
  deviceModel: z.string().max(128).optional(),
  environment: z.string().max(32).default('production'),
  userId: z.string().nullable().optional(),
  installationId: z.string().min(1).max(64),
  correlationId: z.string().min(1).max(64),
  occurredAt: z.string().datetime().or(z.string().regex(/^\d{4}-\d{2}-\d{2}/)),
  status: z.enum(['NEW', 'ACKNOWLEDGED', 'RESOLVED', 'IGNORED']).default('NEW'),
  occurrenceCount: z.number().int().min(1).default(1),
  metadataJson: z.record(z.any()).default({})
});

const batchEventsSchema = z.object({
  events: z.array(singleEventSchema).min(1).max(50)
});

// 1. POST /api/v1/monitoring/events/batch (Ingestion endpoint for Android & services)
monitoringRouter.post('/events/batch', async (req: Request, res: Response) => {
  const parseResult = batchEventsSchema.safeParse(req.body);
  if (!parseResult.success) {
    return res.status(400).json({
      success: false,
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Invalid events batch',
        details: parseResult.error.errors
      }
    });
  }

  const now = new Date().toISOString();
  const sanitizedEvents: MonitoringEvent[] = parseResult.data.events.map(e => ({
    id: e.id,
    eventType: e.eventType,
    severity: e.severity,
    source: e.source,
    errorCode: e.errorCode,
    message: sanitizeString(e.message),
    stackTrace: e.stackTrace ? sanitizeString(e.stackTrace) : undefined,
    screenName: e.screenName,
    operationName: e.operationName,
    appVersion: e.appVersion,
    appBuild: e.appBuild,
    androidVersion: e.androidVersion,
    deviceModel: e.deviceModel,
    environment: e.environment,
    userId: e.userId || null,
    installationId: e.installationId,
    correlationId: e.correlationId,
    occurredAt: e.occurredAt,
    receivedAt: now,
    status: e.status || 'NEW',
    occurrenceCount: e.occurrenceCount,
    metadataJson: e.metadataJson || {}
  }));

  const result = await dataAdapter.insertMonitoringEvents(sanitizedEvents);

  res.status(200).json({
    success: true,
    data: result
  });
});

// 2. GET /api/v1/admin/monitoring/summary (Admin Overview Metrics)
monitoringRouter.get('/summary', requireAdminKey, async (req: Request, res: Response) => {
  const summary = await dataAdapter.getMonitoringSummary();
  res.json({
    success: true,
    data: summary
  });
});

// 3. GET /api/v1/admin/monitoring/events (Live Event Feed with Filters)
monitoringRouter.get('/events', requireAdminKey, async (req: Request, res: Response) => {
  const severity = req.query.severity as string | undefined;
  const source = req.query.source as string | undefined;
  const status = req.query.status as string | undefined;
  const search = req.query.search as string | undefined;
  const dateFrom = req.query.dateFrom as string | undefined;
  const dateTo = req.query.dateTo as string | undefined;
  const page = Math.max(1, parseInt((req.query.page as string) || '1', 10));
  const limit = Math.min(100, Math.max(1, parseInt((req.query.limit as string) || '20', 10)));
  const offset = (page - 1) * limit;

  const { events, total } = await dataAdapter.getMonitoringEvents({
    severity,
    source,
    status,
    search,
    dateFrom,
    dateTo,
    limit,
    offset
  });

  res.json({
    success: true,
    data: {
      events,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      }
    }
  });
});

// 4. GET /api/v1/admin/monitoring/events/:id (Detailed Event Inspection)
monitoringRouter.get('/events/:id', requireAdminKey, async (req: Request, res: Response) => {
  const eventId = req.params.id as string;
  const result = await dataAdapter.getMonitoringEventById(eventId);

  if (!result) {
    return res.status(404).json({
      success: false,
      error: { code: 'NOT_FOUND', message: 'Monitoring event not found' }
    });
  }

  res.json({
    success: true,
    data: result
  });
});

// 5. PATCH /api/v1/admin/monitoring/events/:id/status (Acknowledge / Resolve / Ignore)
const updateStatusSchema = z.object({
  status: z.enum(['NEW', 'ACKNOWLEDGED', 'RESOLVED', 'IGNORED']),
  reason: z.string().optional()
});

monitoringRouter.patch('/events/:id/status', requireAdminKey, async (req: Request, res: Response) => {
  const eventId = req.params.id as string;
  const parseResult = updateStatusSchema.safeParse(req.body);
  if (!parseResult.success) {
    return res.status(400).json({
      success: false,
      error: { code: 'VALIDATION_ERROR', message: parseResult.error.errors[0].message }
    });
  }

  const { status, reason } = parseResult.data;
  const updated = await dataAdapter.updateMonitoringEventStatus(eventId, status, 'admin_console', reason);

  res.json({
    success: true,
    data: {
      eventId,
      status,
      updated
    }
  });
});

// 6. GET /api/v1/admin/monitoring/health (Component-Level Health Status)
monitoringRouter.get('/health', requireAdminKey, async (req: Request, res: Response) => {
  const dbStatus = await probeDatabaseConnectivity();
  const summary = await dataAdapter.getMonitoringSummary();

  const healthReport = {
    renderApi: {
      status: 'healthy',
      uptimeSeconds: Math.floor(process.uptime()),
      nodeVersion: process.version
    },
    databaseConnectivity: dbStatus,
    monitoringIngestion: {
      status: 'healthy',
      eventsQueuedLocally: summary.totalEvents,
      lastIngestedAt: summary.lastIngestedAt
    },
    authentication: {
      status: 'healthy',
      mode: 'jwt_hs256'
    },
    backgroundSync: {
      status: summary.syncFailuresCount > 5 ? 'degraded' : 'healthy',
      syncFailuresCount: summary.syncFailuresCount
    }
  };

  res.json({
    success: true,
    data: healthReport
  });
});
