import request from 'supertest';
import { createApp } from '../src/app';

const app = createApp();

describe('Backend API Integration Tests', () => {
  it('GET /health returns 200 and healthy status', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('healthy');
  });

  it('GET /health/ready returns 200 ready', async () => {
    const res = await request(app).get('/health/ready');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ready');
  });

  it('Protected route rejects request without token', async () => {
    const res = await request(app).get('/api/v1/accounts');
    expect(res.status).toBe(401);
  });

  it('Protected route succeeds with valid demo token', async () => {
    const res = await request(app)
      .get('/api/v1/accounts')
      .set('Authorization', 'Bearer demo-test-token');
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.data)).toBe(true);
  });

  it('POST /api/v1/transactions creates a transaction with integer minor units', async () => {
    const res = await request(app)
      .post('/api/v1/transactions')
      .set('Authorization', 'Bearer demo-test-token')
      .send({
        accountId: 'acc-cash',
        type: 'expense',
        amountMinor: 15000, // ₹150
        description: 'Snacks'
      });
    expect(res.status).toBe(201);
    expect(res.body.data.amountMinor).toBe(15000);
  });

  it('GET /api/v1/dashboard/summary calculates actual values', async () => {
    const res = await request(app)
      .get('/api/v1/dashboard/summary')
      .set('Authorization', 'Bearer demo-test-token');
    expect(res.status).toBe(200);
    expect(res.body.data.availableBalanceMinor).toBeGreaterThan(0);
  });

  it('Admin route requires valid x-admin-key header', async () => {
    const unauthorizedRes = await request(app).get('/api/v1/admin/overview');
    expect(unauthorizedRes.status).toBe(403);

    const authorizedRes = await request(app)
      .get('/api/v1/admin/overview')
      .set('x-admin-key', 'smart-expenses-admin-secret-2026');
    expect(authorizedRes.status).toBe(200);
    expect(authorizedRes.body.data.totalRegisteredUsers).toBeGreaterThanOrEqual(1);
  });

  it('POST /api/v1/auth/register rejects when termsAccepted is not true', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({
        name: 'New User',
        email: 'newuser@example.com',
        password: 'password123',
        confirmPassword: 'password123',
        termsAccepted: false
      });
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  it('POST /api/v1/auth/register succeeds when terms are accepted', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({
        name: 'Test Candidate',
        email: 'candidate@example.com',
        password: 'securePassword123',
        confirmPassword: 'securePassword123',
        termsAccepted: true
      });
    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);
    expect(res.body.data.user.termsConsent.termsVersion).toBe('2026.1');
    expect(res.body.data.token).toBeDefined();
  });

  it('POST /api/v1/auth/logout succeeds for authenticated user', async () => {
    const res = await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', 'Bearer demo-test-token');
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });
});
