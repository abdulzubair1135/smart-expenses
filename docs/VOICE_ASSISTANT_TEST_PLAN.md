# VOICE ASSISTANT TEST PLAN

## Smart Expenses Tracker — Verification Suite

---

## 1. Unit & Schema Parsing Tests

| Test ID | Input Utterance | Expected Intent | Expected Type | Expected Amount | Expected Fields |
|---|---|---|---|---|---|
| VP-001 | *"Aaj lunch mein 250 rupaye kharch hue"* | `CREATE_TRANSACTION` | `EXPENSE` | `25000` (₹250.00) | Category: Food |
| VP-002 | *"Kal petrol ke 400 rs diye"* | `CREATE_TRANSACTION` | `EXPENSE` | `40000` (₹400.00) | Category: Travel/Fuel |
| VP-003 | *"Salary ke 15000 rupaye bank mein aaye"* | `CREATE_TRANSACTION` | `RECEIVED` | `1500000` (₹15,000.00) | Category: Salary, Account: Bank |
| VP-004 | *"Rahul se 500 rupaye udhar mile"* | `CREATE_TRANSACTION` | `BORROW` | `50000` (₹500.00) | Counterparty: Rahul |
| VP-005 | *"Amit ko 1000 rupaye udhar diye"* | `CREATE_TRANSACTION` | `LEND` | `100000` (₹1,000.00) | Counterparty: Amit |
| VP-006 | *"Cash se bank mein 2000 transfer karo"* | `CREATE_TRANSACTION` | `TRANSFER` | `200000` (₹2,000.00) | Source: Cash, Dest: Bank |
| VP-007 | *"Mera total balance kitna hai?"* | `QUERY_BALANCE` | - | Live Calculation | Aggregate all active accounts |
| VP-008 | *"Aaj kitna kharch kiya?"* | `QUERY_EXPENSES` | - | Live Calculation | Today's expenses from Room DB |
| VP-009 | *"Cancel kar do"* | `CANCEL_DRAFT` | - | - | Discard active draft |
| VP-010 | *"Save kar do"* | `CONFIRM_DRAFT` | - | - | Trigger authoritative DB save |

---

## 2. Validation & Edge Cases

| Test ID | Scenario | Input | Expected System Behavior |
|---|---|---|---|
| VV-001 | Zero Amount | *"Kharcha hua"* (no number) | Asks clarification: *"Aapne kitna rupaye kharch kiya?"* |
| VV-002 | Negative Amount | Negative values | Rejected; converted to positive minor units or reprompted |
| VV-003 | Unknown Account | *"Bitcoin se pay kiya"* | Maps to fallback / requests selection among user's actual accounts |
| VV-004 | Unrecognized Speech | Random noise / gibberish | Helpful error: *"Main samajh nahi paya. Kripya dobara boliye."* |
| VV-005 | Idempotent Save | Double-tap Confirm button | Exactly 1 transaction recorded in Room SQLite |

---

## 3. Physical Device Hardware Tests (`UT03032274562709251`)

| Test ID | Target Component | Verification Method |
|---|---|---|
| VH-001 | Microphone Button | Tap mic icon on Home screen; voice bottom sheet smoothly animates up |
| VH-002 | Audio Permission | Triggers `RECORD_AUDIO` prompt if not granted; proceeds when granted |
| VH-003 | Speech Recognition | User speech or simulated speech updates transcript in real time |
| VH-004 | Draft Card Display | Shows Type, Amount, Category, Account, Date clearly |
| VH-005 | Text-To-Speech (TTS) | Speaks confirmation prompt in Hindi/English; safe cleanup on close |
| VH-006 | Confirm & Save Action | Inserts transaction; updates Available Balance & Recent Transactions |
| VH-007 | Edit Action | Transfers draft data into Add Transaction sheet for manual editing |
| VH-008 | Cancel Action | Discards draft without modifying database |
