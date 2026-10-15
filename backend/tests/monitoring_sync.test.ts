import request from 'supertest';
import { createApp } from '../src/app';

const app = createApp();
const ADMIN_KEY = 'smart-expenses-admin-secret-2026';

describe('Global Monitoring & Multi-Entity Sync Integration Tests', () => {
  const testEventId = 'e0000000-0000-0000-0000-000000000001';

  it('rejects unauthenticated admin access to monitoring summary', async () => {
    const res = await request(app).get('/api/v1/admin/monitoring/summary');
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });

  it('accepts batch of monitoring events and sanitizes sensitive data', async () => {
    const res = await request(app)
      .post('/api/v1/monitoring/events/batch')
      .send({
        events: [
          {
            id: testEventId,
            eventType: 'UI_RENDER_FAILURE',
            severity: 'ERROR',
            source: 'ANDROID',
            errorCode: 'ERR_VIEW_BINDING',
            message: 'Failed to bind layout for user password "super_secret_123"',
            stackTrace: 'at com.smarttracker.ui.HomeFragment (Home.java:42) with bearer eyJhbGciOi...',
            screenName: 'HomeFragment',
            operationName: 'loadDashboard',
            appVersion: '1.0.0',
            appBuild: '1',
            installationId: 'inst-12345',
            correlationId: 'corr-99999',
            occurredAt: new Date().toISOString()
          }
        ]
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.insertedCount).toBe(1);
  });

  it('deduplicates events when same ID is uploaded again', async () => {
    const res = await request(app)
      .post('/api/v1/monitoring/events/batch')
      .send({
        events: [
          {
            id: testEventId,
            eventType: 'UI_RENDER_FAILURE',
            severity: 'ERROR',
            source: 'ANDROID',
            errorCode: 'ERR_VIEW_BINDING',
            message: 'Retry upload',
            installationId: 'inst-12345',
            correlationId: 'corr-99999',
            occurredAt: new Date().toISOString()
          }
        ]
      });

    expect(res.status).toBe(200);
    expect(res.body.data.duplicatesIgnored).toBe(1);
    expect(res.body.data.insertedCount).toBe(0);
  });

  it('allows authorized admin to fetch monitoring summary', async () => {
    const res = await request(app)
      .get('/api/v1/admin/monitoring/summary')
      .set('x-admin-key', ADMIN_KEY);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.totalEvents).toBeGreaterThanOrEqual(1);
    expect(res.body.data.errorEvents).toBeGreaterThanOrEqual(1);
  });

  it('allows admin to query events with filter and verifies sanitized content', async () => {
    const res = await request(app)
      .get('/api/v1/admin/monitoring/events?severity=ERROR')
      .set('x-admin-key', ADMIN_KEY);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.events.length).toBeGreaterThanOrEqual(1);
    const event = res.body.data.events.find((e: any) => e.id === testEventId);
    expect(event).toBeDefined();
    expect(event.message).not.toContain('super_secret_123');
    expect(event.message).toContain('[REDACTED]');
  });

  it('allows admin to acknowledge and update event status with audit history', async () => {
    const patchRes = await request(app)
      .patch(`/api/v1/admin/monitoring/events/${testEventId}/status`)
      .set('x-admin-key', ADMIN_KEY)
      .send({
        status: 'ACKNOWLEDGED',
        reason: 'Investigating view binding on Android 14'
      });

    expect(patchRes.status).toBe(200);
    expect(patchRes.body.data.status).toBe('ACKNOWLEDGED');

    const getRes = await request(app)
      .get(`/api/v1/admin/monitoring/events/${testEventId}`)
      .set('x-admin-key', ADMIN_KEY);

    expect(getRes.status).toBe(200);
    expect(getRes.body.data.event.status).toBe('ACKNOWLEDGED');
    expect(getRes.body.data.history.length).toBeGreaterThanOrEqual(1);
    expect(getRes.body.data.history[0].newStatus).toBe('ACKNOWLEDGED');
  });

  it('returns component-level monitoring health status', async () => {
    const res = await request(app)
      .get('/api/v1/admin/monitoring/health')
      .set('x-admin-key', ADMIN_KEY);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.renderApi.status).toBe('healthy');
    expect(res.body.data.monitoringIngestion.status).toBe('healthy');
  });

  it('performs multi-entity sync push and pull correctly', async () => {
    // 1. Authenticate user
    const loginRes = await request(app)
      .post('/api/v1/auth/login')
      .send({
        email: 'divya@example.com',
        password: 'password123'
      });
    expect(loginRes.status).toBe(200);
    const token = loginRes.body.data.token;

    const syncAccId = 'a1000000-0000-0000-0000-000000000001';
    const syncTxId = 'b1000000-0000-0000-0000-000000000001';
    const syncTripId = 'c1000000-0000-0000-0000-000000000001';

    // 2. Push multi-entity
    const pushRes = await request(app)
      .post('/api/v1/sync/push')
      .set('Authorization', `Bearer ${token}`)
      .send({
        accounts: [
          {
            id: syncAccId,
            name: 'Paytm Wallet',
            type: 'wallet',
            openingBalanceMinor: 50000,
            currencyCode: 'INR'
          }
        ],
        transactions: [
          {
            id: syncTxId,
            accountId: syncAccId,
            type: 'expense',
            amountMinor: 15000,
            currencyCode: 'INR',
            description: 'Local sync tea',
            idempotencyKey: 'idemp-sync-999'
          }
        ],
        trips: [
          {
            id: syncTripId,
            name: 'Goa Weekend',
            destination: 'Goa',
            startDate: '2026-11-01',
            endDate: '2026-11-04'
          }
        ]
      });

    expect(pushRes.status).toBe(200);
    expect(pushRes.body.data.syncedCounts.accounts).toBe(1);
    expect(pushRes.body.data.syncedCounts.transactions).toBe(1);
    expect(pushRes.body.data.syncedCounts.trips).toBe(1);

    // 3. Pull multi-entity
    const pullRes = await request(app)
      .get('/api/v1/sync/pull')
      .set('Authorization', `Bearer ${token}`);

    expect(pullRes.status).toBe(200);
    expect(pullRes.body.data.accounts.some((a: any) => a.id === syncAccId || a.name === 'Paytm Wallet')).toBe(true);
    expect(pullRes.body.data.transactions.some((t: any) => t.id === syncTxId || t.description === 'Local sync tea')).toBe(true);
    expect(pullRes.body.data.trips.some((tr: any) => tr.id === syncTripId || tr.name === 'Goa Weekend')).toBe(true);
  });
});
