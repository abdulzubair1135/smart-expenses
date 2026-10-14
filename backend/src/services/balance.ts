import { Transaction, Account } from '../repositories/store';

export interface CalculatedBalanceResult {
  openingBalanceMinor: number;
  totalIncomeMinor: number;
  totalExpenseMinor: number;
  netMovementMinor: number;
  currentBalanceMinor: number;
}

/**
 * Calculates current balance based on documented financial rules:
 * - EXPENSE: reduces balance
 * - RECEIVED: increases balance
 * - LEND: reduces available funds (records receivable)
 * - BORROW: increases available funds (records payable)
 * - TRANSFER: decreases source, increases destination
 */
export function calculateAccountBalance(account: Account, transactions: Transaction[]): CalculatedBalanceResult {
  let income = 0;
  let expense = 0;

  for (const tx of transactions) {
    if (tx.status !== 'completed') continue;

    if (tx.type === 'received' && tx.accountId === account.id) {
      income += tx.amountMinor;
    } else if (tx.type === 'expense' && tx.accountId === account.id) {
      expense += tx.amountMinor;
    } else if (tx.type === 'borrow' && tx.accountId === account.id) {
      income += tx.amountMinor;
    } else if (tx.type === 'lend' && tx.accountId === account.id) {
      expense += tx.amountMinor;
    } else if (tx.type === 'transfer') {
      if (tx.accountId === account.id) {
        expense += tx.amountMinor;
      } else if (tx.destinationAccountId === account.id) {
        income += tx.amountMinor;
      }
    }
  }

  const netMovementMinor = income - expense;
  const currentBalanceMinor = account.openingBalanceMinor + netMovementMinor;

  return {
    openingBalanceMinor: account.openingBalanceMinor,
    totalIncomeMinor: income,
    totalExpenseMinor: expense,
    netMovementMinor,
    currentBalanceMinor
  };
}

export function calculateUserTotalBalance(accounts: Account[], transactions: Transaction[]): {
  totalAvailableMinor: number;
  totalIncomeMinor: number;
  totalExpenseMinor: number;
} {
  let totalAvailableMinor = 0;
  let totalIncomeMinor = 0;
  let totalExpenseMinor = 0;

  for (const acc of accounts) {
    const res = calculateAccountBalance(acc, transactions);
    totalAvailableMinor += res.currentBalanceMinor;
  }

  for (const tx of transactions) {
    if (tx.status !== 'completed') continue;
    if (tx.type === 'received') {
      totalIncomeMinor += tx.amountMinor;
    } else if (tx.type === 'expense') {
      totalExpenseMinor += tx.amountMinor;
    }
  }

  return {
    totalAvailableMinor,
    totalIncomeMinor,
    totalExpenseMinor
  };
}
