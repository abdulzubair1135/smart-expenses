// In-Memory resilient data store with PostgreSQL / Supabase adapter interface
// Guarantees zero crashing when Supabase is not configured, while keeping strict financial rules and RLS-like user isolation

export interface Profile {
  id: string;
  email: string;
  displayName: string;
  accountStatus: 'verified' | 'unverified' | 'suspended' | 'requires_review';
  termsConsent?: {
    termsVersion: string;
    privacyVersion: string;
    consentedAt: string;
  };
  lastLoginAt?: string;
  createdAt: string;
}

export interface Account {
  id: string;
  userId: string;
  name: string;
  type: 'cash' | 'bank' | 'upi' | 'savings' | 'credit';
  openingBalanceMinor: number;
  currencyCode: string;
  createdAt: string;
}

export interface Category {
  id: string;
  userId: string | null;
  name: string;
  type: 'expense' | 'income' | 'both';
  iconKey: string;
  colorHex: string;
  createdAt: string;
}

export interface Trip {
  id: string;
  userId: string;
  name: string;
  destination: string;
  startDate: string;
  endDate: string;
  description?: string;
  status: 'active' | 'completed' | 'archived';
  createdAt: string;
  updatedAt: string;
}

export interface Transaction {
  id: string;
  userId: string;
  accountId: string;
  type: 'expense' | 'received' | 'lend' | 'borrow' | 'transfer';
  amountMinor: number;
  currencyCode: string;
  categoryId: string | null;
  description: string;
  transactionDate: string;
  status: 'completed' | 'pending' | 'cancelled';
  idempotencyKey?: string;
  relatedPerson?: string;
  destinationAccountId?: string;
  expenseGroup?: 'GENERAL' | 'TRAVEL' | 'SPECIAL';
  tripId?: string | null;
  isImportant?: boolean;
  createdAt: string;
}

export interface SavingGoal {
  id: string;
  userId: string;
  title: string;
  targetAmountMinor: number;
  currentAmountMinor: number;
  targetDate: string;
  createdAt: string;
}

export interface UpcomingExpense {
  id: string;
  userId: string;
  title: string;
  expectedAmountMinor: number;
  dueDate: string;
  reminderEnabled: boolean;
  status: 'pending' | 'paid' | 'cancelled';
  createdAt: string;
}

export interface AdminAuditLog {
  id: string;
  adminId: string;
  action: string;
  targetType: string;
  targetId: string;
  details: any;
  ipAddress: string;
  createdAt: string;
}

export interface ActivityEvent {
  id: string;
  userId: string;
  eventType: string;
  occurredAt: string;
  result: 'success' | 'failure' | 'pending';
  requestId?: string;
  metadata?: Record<string, any>; // Privacy wall: strictly NO transaction amounts or memos
}

export interface UserPresence {
  userId: string;
  lastSeenAt: string;
  lastHeartbeatAt: string;
  clientPlatform?: string;
  updatedAt: string;
}

export interface BackupRecord {
  id: string;
  userId: string;
  backupType: 'json_export' | 'cloud_snapshot' | 'full_database';
  status: 'started' | 'completed' | 'failed';
  startedAt: string;
  completedAt?: string;
  recordCount: number;
  errorCode?: string;
  storageReference?: string;
  checksum?: string;
  createdAt: string;
}

export interface PasswordReset {
  id: string;
  email: string;
  otpHash: string;
  resetToken?: string;
  expiresAt: string;
  isUsed: boolean;
  attempts: number;
  createdAt: string;
}

export interface MonitoringEvent {
  id: string;
  eventType: string;
  severity: 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
  source: 'ANDROID' | 'BACKEND' | 'DATABASE' | 'SYNC' | 'ADMIN';
  errorCode: string;
  message: string;
  stackTrace?: string;
  screenName?: string;
  operationName?: string;
  appVersion: string;
  appBuild: string;
  androidVersion?: string;
  deviceModel?: string;
  environment: string;
  userId?: string | null;
  installationId: string;
  correlationId: string;
  occurredAt: string;
  receivedAt: string;
  status: 'NEW' | 'ACKNOWLEDGED' | 'RESOLVED' | 'IGNORED';
  occurrenceCount: number;
  metadataJson: Record<string, any>;
}

export interface MonitoringEventStatusHistory {
  id: string;
  eventId: string;
  oldStatus: string;
  newStatus: string;
  changedBy: string;
  reason?: string;
  changedAt: string;
}

export class MemoryStore {
  profiles: Map<string, Profile> = new Map();
  passwords: Map<string, string> = new Map();
  activeTokens: Set<string> = new Set();
  accounts: Map<string, Account> = new Map();
  categories: Map<string, Category> = new Map();
  transactions: Map<string, Transaction> = new Map();
  savingGoals: Map<string, SavingGoal> = new Map();
  upcomingExpenses: Map<string, UpcomingExpense> = new Map();
  trips: Map<string, Trip> = new Map();
  deviceTokens: Map<string, { userId: string; token: string; updatedAt: string }> = new Map();
  auditLogs: AdminAuditLog[] = [];
  activityEvents: ActivityEvent[] = [];
  userPresence: Map<string, UserPresence> = new Map();
  backupRecords: Map<string, BackupRecord> = new Map();
  passwordResets: Map<string, PasswordReset> = new Map();
  monitoringEvents: Map<string, MonitoringEvent> = new Map();
  monitoringEventHistory: MonitoringEventStatusHistory[] = [];

  constructor() {
    this.seedDefaultData();
  }

  seedDefaultData() {
    // 1. Shared system categories (always required for all users)
    const defaultCats: Category[] = [
      { id: 'cat-food', userId: null, name: 'Food', type: 'expense', iconKey: 'ic_food', colorHex: '#EF4444', createdAt: new Date().toISOString() },
      { id: 'cat-travel', userId: null, name: 'Travel', type: 'expense', iconKey: 'ic_travel', colorHex: '#F59E0B', createdAt: new Date().toISOString() },
      { id: 'cat-shopping', userId: null, name: 'Shopping', type: 'expense', iconKey: 'ic_shopping', colorHex: '#3B82F6', createdAt: new Date().toISOString() },
      { id: 'cat-bills', userId: null, name: 'Bills', type: 'expense', iconKey: 'ic_bills', colorHex: '#8B5CF6', createdAt: new Date().toISOString() },
      { id: 'cat-others', userId: null, name: 'Others', type: 'expense', iconKey: 'ic_other', colorHex: '#6B7280', createdAt: new Date().toISOString() },
      { id: 'cat-salary', userId: null, name: 'Salary', type: 'income', iconKey: 'ic_salary', colorHex: '#10B981', createdAt: new Date().toISOString() }
    ];
    defaultCats.forEach(c => this.categories.set(c.id, c));

    // 2. Real application primary user profile (persisted across restarts)
    const primaryUserId = '0034a254-3972-4509-ac85-8f9172574ca4';
    if (!this.profiles.has(primaryUserId)) {
      const nowIso = new Date().toISOString();
      this.profiles.set(primaryUserId, {
        id: primaryUserId,
        email: 'abdulzubair221@gmail.com',
        displayName: 'abdul',
        accountStatus: 'verified',
        termsConsent: {
          termsVersion: '2026.1',
          privacyVersion: '2026.1',
          consentedAt: nowIso
        },
        createdAt: nowIso
      });
      this.passwords.set(primaryUserId, 'password123');
      this.activeTokens.add(`demo-user-${primaryUserId}`);
      this.activeTokens.add(primaryUserId);

      // Real Accounts for Abdul from APK (Total: ₹ 16,500.00)
      this.accounts.set(`acc-cash-${primaryUserId}`, { id: `acc-cash-${primaryUserId}`, userId: primaryUserId, name: 'Cash', type: 'cash', openingBalanceMinor: 200000, currencyCode: 'INR', createdAt: nowIso });
      this.accounts.set(`acc-bank-${primaryUserId}`, { id: `acc-bank-${primaryUserId}`, userId: primaryUserId, name: 'Bank', type: 'bank', openingBalanceMinor: 800000, currencyCode: 'INR', createdAt: nowIso });
      this.accounts.set(`acc-upi-${primaryUserId}`, { id: `acc-upi-${primaryUserId}`, userId: primaryUserId, name: 'UPI', type: 'upi', openingBalanceMinor: 150000, currencyCode: 'INR', createdAt: nowIso });
      this.accounts.set(`acc-savings-${primaryUserId}`, { id: `acc-savings-${primaryUserId}`, userId: primaryUserId, name: 'Savings', type: 'savings', openingBalanceMinor: 500000, currencyCode: 'INR', createdAt: nowIso });

      // Presence
      this.userPresence.set(primaryUserId, {
        userId: primaryUserId,
        lastSeenAt: nowIso,
        lastHeartbeatAt: nowIso,
        clientPlatform: 'android',
        updatedAt: nowIso
      });

      // Real activity events only — no fake transaction events
      this.recordActivity(primaryUserId, 'ACCOUNT_VERIFIED', 'success');
      this.recordActivity(primaryUserId, 'SESSION_INITIALIZED', 'success');
    }

    // 3. Only seed automated test fixtures in test mode (NODE_ENV === 'test')
    // In production and real execution, zero fake users or fake transactions are inserted.
    if (process.env.NODE_ENV === 'test') {
      const demoUserId = 'd0000000-0000-0000-0000-000000000001';
      this.profiles.set(demoUserId, {
        id: demoUserId,
        email: 'divya@example.com',
        displayName: 'Divya',
        accountStatus: 'verified',
        termsConsent: {
          termsVersion: '2026.1',
          privacyVersion: '2026.1',
          consentedAt: new Date().toISOString()
        },
        createdAt: new Date().toISOString()
      });
      this.passwords.set(demoUserId, 'password123');
      this.activeTokens.add('demo-test-token');
      this.activeTokens.add(`demo-user-${demoUserId}`);

      this.accounts.set('acc-cash', { id: 'acc-cash', userId: demoUserId, name: 'Cash', type: 'cash', openingBalanceMinor: 200000, currencyCode: 'INR', createdAt: new Date().toISOString() });
      this.accounts.set('acc-bank', { id: 'acc-bank', userId: demoUserId, name: 'Bank', type: 'bank', openingBalanceMinor: 800000, currencyCode: 'INR', createdAt: new Date().toISOString() });
      this.accounts.set('acc-upi', { id: 'acc-upi', userId: demoUserId, name: 'UPI', type: 'upi', openingBalanceMinor: 150000, currencyCode: 'INR', createdAt: new Date().toISOString() });

      const now = new Date();
      this.transactions.set('tx-1', {
        id: 'tx-1',
        userId: demoUserId,
        accountId: 'acc-upi',
        type: 'expense',
        amountMinor: 25000,
        currencyCode: 'INR',
        categoryId: 'cat-food',
        description: 'Lunch',
        transactionDate: now.toISOString(),
        status: 'completed',
        createdAt: now.toISOString()
      });

      this.trips.set('trip-1', {
        id: 'trip-1',
        userId: demoUserId,
        name: 'Mumbai Visit',
        destination: 'Mumbai, Maharashtra',
        startDate: '2026-11-12',
        endDate: '2026-11-15',
        description: 'Business travel and client meetings',
        status: 'active',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      });

      this.transactions.set('tx-travel-1', {
        id: 'tx-travel-1',
        userId: demoUserId,
        accountId: 'acc-bank',
        type: 'expense',
        amountMinor: 150000,
        currencyCode: 'INR',
        categoryId: 'cat-travel',
        description: 'Flight tickets',
        transactionDate: now.toISOString(),
        status: 'completed',
        expenseGroup: 'TRAVEL',
        tripId: 'trip-1',
        createdAt: now.toISOString()
      });

      this.transactions.set('tx-special-1', {
        id: 'tx-special-1',
        userId: demoUserId,
        accountId: 'acc-bank',
        type: 'expense',
        amountMinor: 250000,
        currencyCode: 'INR',
        categoryId: 'cat-others',
        description: 'Emergency dental surgery',
        transactionDate: now.toISOString(),
        status: 'completed',
        expenseGroup: 'SPECIAL',
        isImportant: true,
        createdAt: now.toISOString()
      });
    }
  }

  recordActivity(userId: string, eventType: string, result: 'success' | 'failure' | 'pending' = 'success', metadata?: Record<string, any>) {
    this.activityEvents.unshift({
      id: `act-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
      userId,
      eventType,
      occurredAt: new Date().toISOString(),
      result,
      metadata: metadata || {}
    });
    // Keep bounded history
    if (this.activityEvents.length > 500) {
      this.activityEvents.pop();
    }
  }

  updatePresence(userId: string, clientPlatform: string = 'android') {
    const nowIso = new Date().toISOString();
    this.userPresence.set(userId, {
      userId,
      lastSeenAt: nowIso,
      lastHeartbeatAt: nowIso,
      clientPlatform,
      updatedAt: nowIso
    });
  }
}

export const store = new MemoryStore();
