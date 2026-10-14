# Root-Cause Audit: Qwen Integration & Transaction Classification

## Smart Expenses Tracker — Phase 1 Audit

---

### 1. Root Cause Analysis: Why Qwen was Bypassed / Ignored

| Component | File & Location | Audit Finding | Consequence |
|---|---|---|---|
| **Android Client** | `VoiceAssistantBottomSheetDialog.java` (L188-190) | `handleSpeechResult(transcript)` directly called `LocalPatternLanguageParser.parse(transcript)` synchronously on the Android main thread. No Retrofit/OkHttp network call to `POST /api/v1/voice/parse` was executed. | **Android completely bypassed the backend AI endpoint**; speech recognition transcripts never reached Node.js or Qwen. |
| **Backend AI Service** | `backend/src/services/ai/qwenProvider.ts` (L53-55) | `if (config.nodeEnv === 'test') return quickResult;` | Test suites never exercised Ollama integration. |
| **Backend AI Timeout** | `backend/src/config/index.ts` (L23) & `qwenProvider.ts` (L120) | `AI_REQUEST_TIMEOUT_MS` was set to `4000ms` (4s). When Ollama runs `qwen3.5:4b` on CPU/hybrid, the thinking process requires ~15-35s. | Whenever contacted, the HTTP request timed out at 4000ms and aborted, silently falling back to rule-based parsing. |
| **Ollama Response Parsing** | `qwenProvider.ts` (L128-133) | `parsedRes.response` was read directly. In `qwen3.5:4b`, Ollama returns generated output in `message.content` (for `/api/chat`) or splits reasoning into `thinking` and `response` (for `/api/generate`). Because `num_predict` was truncated, `response` was empty string `""`. | `qwenProvider.ts` resolved `null` on every Ollama invocation. |
| **Network Client Missing** | Android package `com.smarttracker.data.remote` | No `VoiceApiClient.java` or Retrofit interface existed to connect to `/api/v1/voice/parse`. | The Android client had no conduit to send transcripts to the backend. |

---

### 2. Root Cause Analysis: Why Transactions Defaulted to EXPENSE

| Component | File & Location | Audit Finding | Consequence |
|---|---|---|---|
| **Android Fallback Parser** | `LocalPatternLanguageParser.java` (L133) | The final branch of the type switch was `else { model.transactionType = "expense"; }`. | Any phrase not matching the explicit checks for `aaye`, `mile`, `salary`, `credit`, `udhar`, or `transfer` was unconditionally assigned type `expense`. |
| **Missing Income Vocabulary** | `LocalPatternLanguageParser.java` (L127) | Only searched `aaye`, `mile`, `salary`, `received`, `credit`. Hindi roots for earning (`kamaye`, `kamaya`, `kamaaye`, `kamai`, `earned`, `income`, `munafa`, `bachat`) were **completely absent**. | Common expressions like *"Maine 50 rupaye kamaye"* fell through directly to `model.transactionType = "expense"`. |
| **Spelling Variants in Lending/Borrowing** | `LocalPatternLanguageParser.java` (L111, L119) | Only checked single 'a' `udhar`. Common spellings `udhaar`, `loan`, `karz`, or Devanagari `उधार` were missing. | Lending/borrowing statements with alternate spellings defaulted to `expense`. |
| **Ignored Negation** | `LocalPatternLanguageParser.java` & `qwenProvider.ts` | Neither parser handled negation words like `nahi`, `mat`, `kamaye nahi kharch kiye`. | Reversals and corrections were misinterpreted or classified under negated intents. |
| **Ambiguity Handling** | `LocalPatternLanguageParser.java` | Ambiguous phrases like *"Maine paise diye"* or *"Mujhe paise mile"* without direction/context defaulted to `expense` instead of flagging `clarificationRequired = true`. | Ambiguous inputs created unsafe transactions without user clarification. |
| **Backend Rule Parser** | `qwenProvider.ts` (L248, L299) | `let txType: ... = 'EXPENSE';` was initialized to `'EXPENSE'`, and the default `else` branch explicitly assigned `txType = 'EXPENSE'`. | Fallback on the server had the identical defect, defaulting to `EXPENSE`. |

---

### 3. Concrete Fix Roadmap

1. **Android Remote AI Client (`VoiceApiClient.java`):**
   - Implement Retrofit/OkHttp client connecting to `http://10.0.2.2:4000/api/v1/voice/parse` (or device reverse port `127.0.0.1:4000`).
   - Pass Bearer token from `SessionManager` and correlation ID.
   - Include asynchronous callback with progress status ("Connecting to AI...").
2. **Unified Deterministic Classification Pipeline (`FinancialLanguageEngine`):**
   - Create a shared, rigorous classification pipeline implemented identically in Android (`LocalPatternLanguageParser.java`) and Backend (`qwenProvider.ts`).
   - Stages:
     1. Text normalization (lowercase, trim, whitespace collapse).
     2. Negation & correction detection (e.g., `kamaye nahi, kharch kiye` -> `EXPENSE`).
     3. Conflict detection (if two conflicting intents exist without correction -> ask clarification).
     4. Directional intent identification:
        - `RECEIVED`: `kamaye`, `kamaya`, `kamaaye`, `kamai`, `earned`, `income`, `salary`, `paise aaye`, `credit hua`, `remittance`, `munafa`.
        - `LEND`: `udhaar diye`, `udhar diya`, `lent`, `paise diye udhar`, `karz diya`, `loan diya`.
        - `BORROW`: `udhaar liye`, `udhar liya`, `borrowed`, `loan liya`, `karz liya`.
        - `TRANSFER`: `transfer kiya`, `shift kiya`, `bank se cash`, `account se wallet`.
        - `EXPENSE`: `kharch`, `spent`, `paid`, `kharida`, `bill bhara`, `chuka diya`.
     5. Ambiguity guard: If phrase is purely *"Maine paise diye"* or *"Mujhe paise mile"* or *"Kal wale paise ka hisaab kar dena"*, return `clarification_required: true`.
     6. Amount extraction with word support (`sau`, `hazar`, `k`, etc.).
     7. Explicit type validation (no blind fallback to `EXPENSE`).
3. **Robust Backend Qwen Provider:**
   - Add correlation ID and development diagnostic logging (request duration, provider, status, fallback reason).
   - Use Ollama `/api/chat` with structured messages and clean timeout management.
   - If Ollama fails, times out, or returns invalid schema, activate the deterministic fallback parser with explicit diagnostic logs.
   - Return clean response under the unified schema.
4. **Android UI & Error Integration:**
   - In `VoiceAssistantBottomSheetDialog.java`, call backend `VoiceApiClient` first.
   - If backend succeeds with validated draft, use it.
   - If backend network fails, use the updated deterministic fallback parser seamlessly, updating the UI with transparent status ("Parsed offline (Local Engine)").
   - Never overwrite the returned `transactionType` with `expense`.
