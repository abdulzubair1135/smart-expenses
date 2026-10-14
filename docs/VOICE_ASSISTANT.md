# INTELLIGENT VOICE ASSISTANT SPECIFICATION

## Smart Expenses Tracker — Speech, NLP & Financial Execution Architecture

---

## 1. Executive Architecture Overview

The Intelligent Voice Assistant in Smart Expenses Tracker allows users to speak commands and questions in **Hindi, Hinglish, or Indian English**. It bridges real-time spoken utterances to structured financial mutations and queries through an offline-first, local-first architecture.

```
                    ┌──────────────────────────────────────────────┐
                    │               Android Client                 │
                    │         (100% Pure Java & XML Layout)        │
                    └──────────────────────┬───────────────────────┘
                                           │
                    ┌──────────────────────▼───────────────────────┐
                    │     SpeechRecognitionManager (hi-IN, en-IN)   │
                    │        Android SpeechRecognizer API          │
                    └──────────────────────┬───────────────────────┘
                                           │
                                  [Recognized Transcript]
                                           │
                 ┌─────────────────────────┴────────────────────────┐
                 │                                                  │
                 ▼                                                  ▼
      [Online Mode: Authenticated]                       [Offline Mode: Local]
   Backend Express /api/v1/voice/parse                LocalPatternLanguageParser (Java)
                 │                                                  │
                 ▼                                                  │
      Qwen AI Provider (Ollama:11434)                               │
          Model: qwen3.5:4b                                         │
                 │                                                  │
                 ▼                                                  │
      Strict JSON Schema Validator                                  │
                 │                                                  │
                 └─────────────────────────┬────────────────────────┘
                                           │
                               [Structured Voice Draft]
                                           │
                    ┌──────────────────────▼───────────────────────┐
                    │         VoiceAssistantManager & UI           │
                    │   • Interactive Draft Preview Card           │
                    │   • Clarification Questions (if ambiguous)   │
                    │   • Android TextToSpeech (Hinglish/English)  │
                    │   • State: AWAITING_CONFIRMATION             │
                    └──────────────────────┬───────────────────────┘
                                           │
                                  [User Explicit Action]
                           [Confirm & Save] / [Edit] / [Cancel]
                                           │
                    ┌──────────────────────▼───────────────────────┐
                    │          Authoritative Data Layer            │
                    │       Room Database / SQLite (Local)         │
                    │      FinancialEngine (64-bit paise long)     │
                    │       TransactionRepository.insert()         │
                    └──────────────────────────────────────────────┘
```

---

## 2. Voice Command Contract & Schema

### Intent Hierarchy
- `CREATE_TRANSACTION`: Create Expense, Received, Lend, Borrow, or Transfer.
- `QUERY_BALANCE`: Retrieve live balance across accounts.
- `QUERY_EXPENSES`: Calculate total expenses for Today, Yesterday, or Current Month.
- `QUERY_INCOME`: Calculate total income / money received for specified timeframe.
- `QUERY_CATEGORY_TOTAL`: Calculate sum spent in a particular category (e.g., Food, Travel).
- `CONFIRM_DRAFT`: Spoken confirmation ("Haan", "Save kar do", "Confirm").
- `CANCEL_DRAFT`: Spoken cancellation ("Cancel kar do", "Nahi chahiye").
- `UNKNOWN`: Unrecognized or out-of-domain phrase.

### JSON Contract
```json
{
  "intent": "CREATE_TRANSACTION",
  "transaction_type": "EXPENSE",
  "amount_minor": 25000,
  "currency_code": "INR",
  "description": "Lunch with friends",
  "category_name": "Food",
  "account_name": "UPI",
  "transaction_date": "2026-10-13",
  "recipient": null,
  "source_account": null,
  "destination_account": null,
  "missing_fields": [],
  "clarification_required": false,
  "clarification_question": null,
  "spoken_response": "250 rupaye ka food expense UPI se save karne ke liye ready hoon."
}
```

---

## 3. Natural Hindi & Hinglish Speech Understanding

| Spoken Utterance | Resolved Intent | Resolved Type | Amount (paise) | Category / Note | Account |
|---|---|---|---|---|---|
| *"Aaj lunch mein 250 rupaye kharch hue UPI se"* | `CREATE_TRANSACTION` | `EXPENSE` | `25000` | Food (Lunch) | UPI |
| *"Kal petrol ke 400 rs diye cash se"* | `CREATE_TRANSACTION` | `EXPENSE` | `40000` | Travel (Petrol) | Cash |
| *"Salary ke 15000 rupaye bank mein aaye"* | `CREATE_TRANSACTION` | `RECEIVED` | `1500000` | Salary | Bank |
| *"Rahul se 500 rupaye udhar mile"* | `CREATE_TRANSACTION` | `BORROW` | `50000` | Counterparty: Rahul | Cash / Default |
| *"Amit ko 1000 rupaye udhar diye"* | `CREATE_TRANSACTION` | `LEND` | `100000` | Counterparty: Amit | Cash / Default |
| *"Cash se bank account mein 2000 transfer karna hai"* | `CREATE_TRANSACTION` | `TRANSFER` | `200000` | From: Cash, To: Bank | Bank |
| *"Aaj kitna kharch kiya?"* | `QUERY_EXPENSES` | - | Calculated from DB | Timeframe: Today | - |
| *"Mera total balance kitna hai?"* | `QUERY_BALANCE` | - | Calculated from DB | All accounts | - |
| *"Food par is mahine kitna kharch hua?"* | `QUERY_CATEGORY_TOTAL` | - | Calculated from DB | Category: Food | - |

---

## 4. Qwen AI Provider & Backend Integration

1. **Local Ollama Integration**:
   - Backend queries local Ollama daemon at `http://127.0.0.1:11434`.
   - Model: `qwen3.5:4b` with JSON schema enforcement mode (`format: "json"`).
2. **Strict Validation Middleware**:
   - Rejects missing or out-of-range financial units.
   - Converts all rupees to 64-bit integer paise (`long` minor units) avoiding floating point decimals.
   - Validates that accounts and categories belong to the authenticated user.
3. **Dual Online/Offline Resilience**:
   - When the backend or Ollama is unreachable (e.g. Android phone disconnected from dev LAN), Android executes its built-in `LocalPatternLanguageParser`.
   - Never fails silently: user can always edit or confirm draft values.

---

## 5. Duplicate Prevention & Transaction Safety

1. **Lifecycle State Machine**:
   - `IDLE` → `LISTENING` → `TRANSCRIBING` → `PARSING` → `AWAITING_CONFIRMATION` → `SAVING` → `SAVED`
2. **Idempotency Key**:
   - Every draft generates a `UUID.randomUUID().toString()`.
   - If a confirmation is triggered multiple times (double-tap or repeat speech), the second attempt is ignored.
3. **Explicit User Confirmation**:
   - Voice drafts are **NEVER** saved automatically without explicit user action or voice confirmation.
   - User can tap **[Confirm & Save]**, **[Edit]** (pre-fills standard entry form), or **[Cancel]**.
