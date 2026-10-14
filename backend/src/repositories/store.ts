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

  constructor() {
    this.seedDefaultData();
  }

  seedDefaultData() {
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

    // Seed default categories
    const defaultCats: Category[] = [
      { id: 'cat-food', userId: null, name: 'Food', type: 'expense', iconKey: 'ic_food', colorHex: '#EF4444', createdAt: new Date().toISOString() },
      { id: 'cat-travel', userId: null, name: 'Travel', type: 'expense', iconKey: 'ic_travel', colorHex: '#F59E0B', createdAt: new Date().toISOString() },
      { id: 'cat-shopping', userId: null, name: 'Shopping', type: 'expense', iconKey: 'ic_shopping', colorHex: '#3B82F6', createdAt: new Date().toISOString() },
      { id: 'cat-bills', userId: null, name: 'Bills', type: 'expense', iconKey: 'ic_bills', colorHex: '#8B5CF6', createdAt: new Date().toISOString() },
      { id: 'cat-others', userId: null, name: 'Others', type: 'expense', iconKey: 'ic_other', colorHex: '#6B7280', createdAt: new Date().toISOString() },
      { id: 'cat-salary', userId: null, name: 'Salary', type: 'income', iconKey: 'ic_salary', colorHex: '#10B981', createdAt: new Date().toISOString() }
    ];
    defaultCats.forEach(c => this.categories.set(c.id, c));

    // Seed demo accounts matching UI Reference
    // Cash: ₹2,000 (200000 paise), Bank: ₹8,000 (800000 paise), UPI: ₹1,500 (150000 paise), Savings: ₹5,000
    const demoAccounts: Account[] = [
      { id: 'acc-cash', userId: demoUserId, name: 'Cash', type: 'cash', openingBalanceMinor: 200000, currencyCode: 'INR', createdAt: new Date().toISOString() },
      { id: 'acc-bank', userId: demoUserId, name: 'Bank', type: 'bank', openingBalanceMinor: 800000, currencyCode: 'INR', createdAt: new Date().toISOString() },
      { id: 'acc-upi', userId: demoUserId, name: 'UPI', type: 'upi', openingBalanceMinor: 150000, currencyCode: 'INR', createdAt: new Date().toISOString() },
      { id: 'acc-savings', userId: demoUserId, name: 'Savings', type: 'savings', openingBalanceMinor: 500000, currencyCode: 'INR', createdAt: new Date().toISOString() }
    ];
    demoAccounts.forEach(a => this.accounts.set(a.id, a));

    // Seed Saving Goal (Target: ₹30,000, Saved: ₹12,000 = 40%)
    this.savingGoals.set('goal-1', {
      id: 'goal-1',
      userId: demoUserId,
      title: 'New Phone',
      targetAmountMinor: 3000000,
      currentAmountMinor: 1200000,
      targetDate: '2026-12-31',
      createdAt: new Date().toISOString()
    });

    // Seed Upcoming Expense (Electricity Bill: ₹1,200 due 5 Oct)
    this.upcomingExpenses.set('up-1', {
      id: 'up-1',
      userId: demoUserId,
      title: 'Electricity Bill',
      expectedAmountMinor: 120000,
      dueDate: '2026-10-25',
      reminderEnabled: true,
      status: 'pending',
      createdAt: new Date().toISOString()
    });

    // Seed Transactions matching Screen 3 & 12
    const now = new Date();
    this.transactions.set('tx-1', {
      id: 'tx-1',
      userId: demoUserId,
      accountId: 'acc-upi',
      type: 'expense',
      amountMinor: 25000, // ₹250
      currencyCode: 'INR',
      categoryId: 'cat-food',
      description: 'Lunch',
      transactionDate: now.toISOString(),
      status: 'completed',
      createdAt: now.toISOString()
    });

    this.transactions.set('tx-2', {
      id: 'tx-2',
      userId: demoUserId,
      accountId: 'acc-cash',
      type: 'received',
      amountMinor: 50000, // ₹500
      currencyCode: 'INR',
      categoryId: null,
      description: 'Rahul - Received',
      relatedPerson: 'Rahul',
      transactionDate: now.toISOString(),
      status: 'completed',
      createdAt: now.toISOString()
    });

    // Seed Trip
    this.trips.set('trip-1', {
      id: 'trip-1',
      userId: demoUserId,
      name: 'Mumbai Visit',
      destination: 'Mumbai, Maharashtra',
      startDate: '2026-11-12',
      endDate: '2026-11-15',
      description: 'Business Trip & Client Meetings',
      status: 'active',
      createdAt: now.toISOString(),
      updatedAt: now.toISOString()
    });

    // Seed Travel Expense linked to trip-1
    this.transactions.set('tx-3', {
      id: 'tx-3',
      userId: demoUserId,
      accountId: 'acc-bank',
      type: 'expense',
      amountMinor: 150000, // ₹1,500
      currencyCode: 'INR',
      categoryId: 'cat-travel',
      description: 'Train Ticket to Mumbai',
      transactionDate: now.toISOString(),
      status: 'completed',
      expenseGroup: 'TRAVEL',
      tripId: 'trip-1',
      createdAt: now.toISOString()
    });

    // Seed Special Expense
    this.transactions.set('tx-4', {
      id: 'tx-4',
      userId: demoUserId,
      accountId: 'acc-upi',
      type: 'expense',
      amountMinor: 250000, // ₹2,500
      currencyCode: 'INR',
      categoryId: 'cat-bills',
      description: 'Emergency Phone Screen Repair',
      transactionDate: now.toISOString(),
      status: 'completed',
      expenseGroup: 'SPECIAL',
      isImportant: true,
      createdAt: now.toISOString()
    });
  }
}

export const store = new MemoryStore();
