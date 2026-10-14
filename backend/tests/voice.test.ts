import request from 'supertest';
import { createApp } from '../src/app';

const app = createApp();

describe('Voice Assistant API Tests', () => {
  it('POST /api/v1/voice/parse rejects request without auth', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .send({ transcript: 'Aaj lunch mein 250 rupaye kharch hue' });
    expect(res.status).toBe(401);
  });

  it('POST /api/v1/voice/parse rejects empty transcript', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: '' });
    expect(res.status).toBe(400);
  });

  it('POST /api/v1/voice/parse extracts Hindi/Hinglish expense correctly', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Aaj lunch mein 250 rupaye kharch hue UPI se' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('EXPENSE');
    expect(data.amount_minor).toBe(25000); // ₹250.00
    expect(data.category_name).toBe('Food');
    expect(data.account_name).toBe('UPI');
    expect(data.clarification_required).toBe(false);
  });

  it('POST /api/v1/voice/parse extracts salary/received correctly', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Salary ke 15000 rupaye bank mein aaye' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('RECEIVED');
    expect(data.amount_minor).toBe(1500000); // ₹15,000.00
    expect(data.account_name).toBe('Bank');
  });

  it('POST /api/v1/voice/parse extracts lend transaction', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Amit ko 1000 rupaye udhar diye' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('LEND');
    expect(data.amount_minor).toBe(100000); // ₹1,000.00
    expect(data.recipient).toBe('Amit');
  });

  it('POST /api/v1/voice/parse extracts borrow transaction', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Rahul se 500 rupaye udhar mile' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('BORROW');
    expect(data.amount_minor).toBe(50000); // ₹500.00
    expect(data.recipient).toBe('Rahul');
  });

  it('POST /api/v1/voice/parse identifies balance query', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Mera total balance kitna hai?' });

    expect(res.status).toBe(200);
    expect(res.body.data.intent).toBe('QUERY_BALANCE');
  });

  it('POST /api/v1/voice/parse identifies confirmation and cancellation commands', async () => {
    const confirmRes = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Haan save kar do' });
    expect(confirmRes.status).toBe(200);
    expect(confirmRes.body.data.intent).toBe('CONFIRM_DRAFT');

    const cancelRes = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Cancel kar do' });
    expect(cancelRes.status).toBe(200);
    expect(cancelRes.body.data.intent).toBe('CANCEL_DRAFT');
  });

  // Strict Classification & Bug Fix Tests
  it('correctly classifies "Maine 50 rupaye kamaye." as RECEIVED, not EXPENSE', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine 50 rupaye kamaye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('RECEIVED');
    expect(data.amount_minor).toBe(5000); // 5000 paise = ₹50.00
    expect(data.clarification_required).toBe(false);
  });

  it('correctly classifies "Mujhe 500 rupaye salary mili." as RECEIVED with category Salary', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Mujhe 500 rupaye salary mili.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('RECEIVED');
    expect(data.amount_minor).toBe(50000); // ₹500.00
    expect(data.category_name).toBe('Salary');
  });

  it('correctly classifies "Maine 250 rupaye kharch kiye." as EXPENSE', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine 250 rupaye kharch kiye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('EXPENSE');
    expect(data.amount_minor).toBe(25000);
  });

  it('correctly classifies "Maine Ali ko 200 rupaye udhaar diye." as LEND with counterparty Ali', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine Ali ko 200 rupaye udhaar diye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('LEND');
    expect(data.amount_minor).toBe(20000);
    expect(data.recipient).toBe('Ali');
  });

  it('correctly classifies "Maine Ali se 200 rupaye udhaar liye." as BORROW with counterparty Ali', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine Ali se 200 rupaye udhaar liye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('BORROW');
    expect(data.amount_minor).toBe(20000);
    expect(data.recipient).toBe('Ali');
  });

  it('correctly classifies "Ali ne mere 500 rupaye wapas kiye." as RECEIVED loan repayment', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Ali ne mere 500 rupaye wapas kiye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('RECEIVED');
    expect(data.amount_minor).toBe(50000);
    expect(data.recipient).toBe('Ali');
    expect(data.description).toContain('Loan Repayment');
  });

  it('correctly classifies "Bank se wallet mein 1000 transfer kiye." as TRANSFER', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Bank se wallet mein 1000 transfer kiye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('TRANSFER');
    expect(data.amount_minor).toBe(100000);
  });

  it('flags ambiguous "Maine paise diye." as clarification_required', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine paise diye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.clarification_required).toBe(true);
    expect(data.clarification_question).toContain('kis purpose ke liye');
  });

  it('flags ambiguous "Mujhe paise mile." as clarification_required', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Mujhe paise mile.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.clarification_required).toBe(true);
    expect(data.clarification_question).toContain('kis roop mein mile');
  });

  it('handles negation: "Maine 500 kamaye nahi, kharch kiye." as EXPENSE', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine 500 kamaye nahi, kharch kiye.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.intent).toBe('CREATE_TRANSACTION');
    expect(data.transaction_type).toBe('EXPENSE');
    expect(data.amount_minor).toBe(50000);
  });

  it('does NOT create income for correction: "Maine income record kiya tha, ab use correct karo."', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine income record kiya tha, ab use correct karo.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.clarification_required).toBe(true);
    expect(data.intent).not.toBe('CREATE_TRANSACTION');
  });

  it('flags missing amount in "Maine auto ka kiraya diya."', async () => {
    const res = await request(app)
      .post('/api/v1/voice/parse')
      .set('Authorization', 'Bearer demo-test-token')
      .send({ transcript: 'Maine auto ka kiraya diya.' });

    expect(res.status).toBe(200);
    const data = res.body.data;
    expect(data.clarification_required).toBe(true);
    expect(data.missing_fields).toContain('amount');
  });
});
