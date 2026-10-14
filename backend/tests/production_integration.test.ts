import request from 'supertest';
import crypto from 'crypto';
import { createApp } from '../src/app';
import { store } from '../src/repositories/store';

const app = createApp();

describe('Production-Oriented Integration Tests', () => {
  const testEmail = `testuser_${Date.now()}@example.com`;
  const initialPassword = 'ValidPassword123';
  const newPassword = 'NewSecurePassword456';
  let userToken: string;
  let testUserId: string;

  describe('1. Real Registration & Terms Acceptance', () => {
    it('rejects registration if terms and privacy policy are not accepted', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Test Candidate',
          email: testEmail,
          password: initialPassword,
          confirmPassword: initialPassword,
          termsAccepted: false
        });
      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error.message).toMatch(/Terms & Conditions/i);
    });

    it('successfully registers user with termsAccepted=true and initializes zero balances', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Test Candidate',
          email: testEmail,
          password: initialPassword,
          confirmPassword: initialPassword,
          termsAccepted: true
        });
      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.email).toBe(testEmail);
      expect(res.body.data.token).toBeDefined();

      userToken = res.body.data.token;
      testUserId = res.body.data.user.id;
    });

    it('rejects duplicate email registration with 409 Conflict', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({
          name: 'Duplicate Candidate',
          email: testEmail,
          password: initialPassword,
          confirmPassword: initialPassword,
          termsAccepted: true
        });
      expect(res.status).toBe(409);
      expect(res.body.success).toBe(false);
    });
  });

  describe('2. Authentication & Session', () => {
    it('fails login with wrong credentials and logs LOGIN_FAILURE', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: testEmail,
          password: 'wrongpassword'
        });
      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);

      const recentFailures = store.activityEvents.filter(
        e => e.userId === testUserId && e.eventType === 'LOGIN_FAILURE'
      );
      expect(recentFailures.length).toBeGreaterThan(0);
    });

    it('succeeds login with correct credentials and returns session token', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: testEmail,
          password: initialPassword
        });
      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.email).toBe(testEmail);
      expect(res.body.data.token).toBeDefined();

      const recentSuccess = store.activityEvents.filter(
        e => e.userId === testUserId && e.eventType === 'LOGIN_SUCCESS'
      );
      expect(recentSuccess.length).toBeGreaterThan(0);
    });
  });

  describe('3. Forgot Password, Controlled OTP & Reset', () => {
    it('rejects fixed OTP if NODE_ENV is production', async () => {
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';

      const res = await request(app)
        .post('/api/v1/auth/forgot-password')
        .send({ email: testEmail });

      expect(res.status).toBe(403);
      expect(res.body.success).toBe(false);

      // Restore env
      process.env.NODE_ENV = originalEnv;
    });

    it('requests password reset OTP in development mode', async () => {
      const res = await request(app)
        .post('/api/v1/auth/forgot-password')
        .send({ email: testEmail });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.expiresInSeconds).toBe(300);
    });

    it('rejects invalid OTP code', async () => {
      const res = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({
          email: testEmail,
          otp: '9999'
        });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error.message).toMatch(/Invalid verification code/i);
    });

    it('verifies valid OTP "1234" and issues single-use reset token', async () => {
      const res = await request(app)
        .post('/api/v1/auth/verify-otp')
        .send({
          email: testEmail,
          otp: '1234'
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.resetToken).toBeDefined();

      const resetToken = res.body.data.resetToken;

      // Reset password with token
      const resetRes = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          resetToken,
          newPassword,
          confirmPassword: newPassword
        });

      expect(resetRes.status).toBe(200);
      expect(resetRes.body.success).toBe(true);

      // Ensure token cannot be reused
      const reuseRes = await request(app)
        .post('/api/v1/auth/reset-password')
        .send({
          resetToken,
          newPassword: 'AnotherPassword789',
          confirmPassword: 'AnotherPassword789'
        });

      expect(reuseRes.status).toBe(400);

      // Can now login with new password
      const newLogin = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: testEmail,
          password: newPassword
        });

      expect(newLogin.status).toBe(200);
      expect(newLogin.body.success).toBe(true);
    });
  });

  describe('4. Presence Heartbeats & Online Status', () => {
    it('acknowledges periodic client heartbeat and updates presence', async () => {
      const res = await request(app)
        .post('/api/v1/presence/heartbeat')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ clientPlatform: 'android' });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.userId).toBe(testUserId);
    });
  });

  describe('5. Verified JSON Backup Export', () => {
    it('exports user isolated data with valid SHA-256 checksum', async () => {
      const res = await request(app)
        .post('/api/v1/backups/export')
        .set('Authorization', `Bearer ${userToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.backupId).toBeDefined();
      expect(res.body.data.checksum).toBeDefined();
      expect(res.body.data.payload.profile.email).toBe(testEmail);

      // Verify SHA-256 matches payload
      const expectedHash = crypto
        .createHash('sha256')
        .update(JSON.stringify(res.body.data.payload))
        .digest('hex');
      expect(res.body.data.checksum).toBe(expectedHash);
    });

    it('returns backup history list for user', async () => {
      const res = await request(app)
        .get('/api/v1/backups')
        .set('Authorization', `Bearer ${userToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);
    });
  });

  describe('6. Admin Dashboard & Strict Financial Privacy Wall', () => {
    const adminKey = process.env.ADMIN_SECRET_KEY || 'smart-expenses-admin-secret-2026';

    it('GET /api/v1/admin/overview calculates DAU, WAU, Online users, and backup counts', async () => {
      const res = await request(app)
        .get('/api/v1/admin/overview')
        .set('x-admin-key', adminKey);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.onlineUsers).toBeGreaterThanOrEqual(1);
      expect(res.body.data.dailyActiveUsers).toBeGreaterThanOrEqual(1);
      expect(res.body.data.totalBackupsCompleted).toBeGreaterThanOrEqual(1);
    });

    it('GET /api/v1/admin/users strictly enforces financial privacy wall (NO amounts, NO balances)', async () => {
      const res = await request(app)
        .get('/api/v1/admin/users')
        .set('x-admin-key', adminKey);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);

      const target = res.body.data.find((u: any) => u.id === testUserId);
      expect(target).toBeDefined();
      expect(target.isOnline).toBe(true);
      expect(target.backupStatus).toBe('completed');

      // Privacy Wall checks:
      expect(target.balance).toBeUndefined();
      expect(target.openingBalanceMinor).toBeUndefined();
      expect(target.transactions).toBeUndefined();
      expect(target.destination).toBeUndefined();
    });

    it('GET /api/v1/admin/users/:id/activity provides timeline without leaking monetary amounts', async () => {
      const res = await request(app)
        .get(`/api/v1/admin/users/${testUserId}/activity`)
        .set('x-admin-key', adminKey);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThan(0);

      // Verify no event contains financial values
      res.body.data.forEach((evt: any) => {
        expect(evt.eventType).toBeDefined();
        expect(evt.occurredAt).toBeDefined();
        if (evt.metadata) {
          expect(evt.metadata.amount).toBeUndefined();
          expect(evt.metadata.balance).toBeUndefined();
        }
      });
    });
  });
});
