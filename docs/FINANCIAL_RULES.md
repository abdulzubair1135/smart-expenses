# SMART EXPENSES TRACKER - FINANCIAL BUSINESS RULES

## 1. Monetary Storage Rules
- **No Floating Point Arithmetic:** All currency values are strictly stored as integer minor units (paise in INR).
  - ₹ 250.00 = `25000` paise
  - ₹ 10,500.00 = `1050000` paise
  - ₹ 0.50 = `50` paise
- Calculations (additions, subtractions, aggregations) must execute with 64-bit integer precision (`long` in Java, `bigint` in PostgreSQL, `number` in TypeScript).

## 2. Transaction Type Semantics

### EXPENSE
- **Effect on Account Balance:** Decreases available account balance (`balance = balance - amount`).
- **Effect on Monthly Totals:** Counts towards total spending / expense.

### RECEIVED
- **Effect on Account Balance:** Increases available account balance (`balance = balance + amount`).
- **Effect on Monthly Totals:** Counts towards income / savings.

### LEND
- **Effect on Account Balance:** Decreases available liquid funds (`balance = balance - amount`).
- **Semantics:** Records an asset receivable from a borrower/friend. Does not count as consumable expense.

### BORROW
- **Effect on Account Balance:** Increases available liquid funds (`balance = balance + amount`).
- **Semantics:** Records a liability payable to a lender. Does not count as earned income.

### TRANSFER
- **Effect on Account Balance:** Atomic operation: Decreases source account by `amount`, increases destination account by `amount`.
- **Effect on Net Worth:** Zero net change (`delta = 0`). Transfers do not count towards income or expense.

## 3. Account Balance Formula
$$\text{Calculated Balance} = \text{Opening Balance} + \sum \text{Received} + \sum \text{Borrow} - \sum \text{Expense} - \sum \text{Lend} + \Delta\text{Transfers}$$
- The dashboard balance is computed directly from completed transactions; it is never hardcoded.
