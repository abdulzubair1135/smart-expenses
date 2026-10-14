import request from 'supertest';
import { createApp } from '../src/app';
import { store } from '../src/repositories/store';

const app = createApp();
const demoToken = 'demo-test-token';
const demoUserId = 'd0000000-0000-0000-0000-000000000001';

describe('Period-Wise Expenses & Travel Trips API', () => {

  beforeEach(() => {
    // Reset store data to known clean state
    store.seedDefaultData();
  });

  describe('Daily Expenses Summary API', () => {
    it('should return today expenses and category breakdown', async () => {
      const todayStr = new Date().toISOString().split('T')[0];
      const res = await request(app)
        .get(`/api/v1/expenses/summary/daily?date=${todayStr}`)
        .set('Authorization', `Bearer ${demoToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.date).toBe(todayStr);
      expect(typeof res.body.data.totalExpenseMinor).toBe('number');
      expect(res.body.data.totalExpenseMinor).toBeGreaterThan(0);
      expect(res.body.data.categoryBreakdown).toBeDefined();
      expect(res.body.data.comparison).toBeDefined();
    });

    it('should exclude expenses from another day', async () => {
      const res = await request(app)
        .get('/api/v1/expenses/summary/daily?date=2025-01-01')
        .set('Authorization', `Bearer ${demoToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.totalExpenseMinor).toBe(0);
      expect(res.body.data.transactionCount).toBe(0);
    });
  });

  describe('Monthly Expenses Summary API', () => {
    it('should return month expenses and calculate comparison', async () => {
      const currentMonth = new Date().toISOString().slice(0, 7);
      const res = await request(app)
        .get(`/api/v1/expenses/summary/monthly?month=${currentMonth}`)
        .set('Authorization', `Bearer ${demoToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.month).toBe(currentMonth);
      expect(typeof res.body.data.totalExpenseMinor).toBe('number');
      expect(res.body.data.comparison.previousMonth).toBeDefined();
    });
  });

  describe('Travel Trips API', () => {
    it('should list trips with calculated total spending', async () => {
      const res = await request(app)
        .get('/api/v1/trips')
        .set('Authorization', `Bearer ${demoToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThanOrEqual(1);

      const trip = res.body.data.find((t: any) => t.id === 'trip-1');
      expect(trip).toBeDefined();
      expect(trip.name).toBe('Mumbai Visit');
      expect(trip.totalSpentMinor).toBe(150000); // ₹1,500
    });

    it('should create a new trip with valid dates', async () => {
      const res = await request(app)
        .post('/api/v1/trips')
        .set('Authorization', `Bearer ${demoToken}`)
        .send({
          name: 'Goa Holiday',
          destination: 'Goa, India',
          startDate: '2026-12-20',
          endDate: '2026-12-25',
          description: 'Year-end vacation'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.id).toBeDefined();
      expect(res.body.data.status).toBe('active');
    });

    it('should reject trip creation if startDate is after endDate', async () => {
      const res = await request(app)
        .post('/api/v1/trips')
        .set('Authorization', `Bearer ${demoToken}`)
        .send({
          name: 'Invalid Trip',
          destination: 'Delhi',
          startDate: '2026-12-25',
          endDate: '2026-12-20'
        });

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('INVALID_DATE_RANGE');
    });

    it('should safely archive a trip if it has linked transactions', async () => {
      const res = await request(app)
        .delete('/api/v1/trips/trip-1')
        .set('Authorization', `Bearer ${demoToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('archived');
    });
  });

  describe('Special Expenses API', () => {
    it('should return special expenses summary and items', async () => {
      const res = await request(app)
        .get('/api/v1/expenses/summary/special')
        .set('Authorization', `Bearer ${demoToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.totalSpentMinor).toBe(250000); // ₹2,500
      expect(res.body.data.transactions.length).toBe(1);
      expect(res.body.data.transactions[0].isImportant).toBe(true);
    });
  });

  describe('User Ownership Isolation', () => {
    it('should prevent unauthorized access without token', async () => {
      const res = await request(app).get('/api/v1/trips');
      expect(res.status).toBe(401);
    });
  });

});
