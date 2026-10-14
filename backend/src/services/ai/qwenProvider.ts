import http from 'http';
import https from 'https';
import { config } from '../../config';

export interface VoiceCommandResult {
  intent: 'CREATE_TRANSACTION' | 'QUERY_BALANCE' | 'QUERY_EXPENSES' | 'QUERY_INCOME' | 'QUERY_CATEGORY_TOTAL' | 'CONFIRM_DRAFT' | 'CANCEL_DRAFT' | 'UNKNOWN';
  transaction_type?: 'EXPENSE' | 'RECEIVED' | 'LEND' | 'BORROW' | 'TRANSFER' | null;
  amount_minor?: number | null; // In integer paise (e.g. 25000 = ₹250.00)
  currency_code: 'INR';
  description?: string | null;
  category_name?: string | null;
  account_name?: string | null;
  transaction_date?: string | null; // ISO YYYY-MM-DD
  recipient?: string | null;
  source_account?: string | null;
  destination_account?: string | null;
  missing_fields: string[];
  clarification_required: boolean;
  clarification_question?: string | null;
  spoken_response: string;
  confidence?: number;
  ai_provider?: string;
  correlation_id?: string;
}

export class QwenProvider {
  private baseUrl: string;
  private model: string;
  private timeoutMs: number;

  constructor() {
    this.baseUrl = config.ai.ollamaBaseUrl;
    this.model = config.ai.qwenModel;
    this.timeoutMs = config.ai.timeoutMs;
  }

  /**
   * Authoritative entry point for parsing transcripts.
   * Tries local Qwen via Ollama, with instant deterministic high-precision fallback.
   */
  public async parseTranscript(transcript: string, userId?: string, correlationId?: string): Promise<VoiceCommandResult> {
    const corrId = correlationId || `corr_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const startTime = Date.now();

    const trimmed = (transcript || '').trim();
    if (!trimmed) {
      return {
        intent: 'UNKNOWN',
        currency_code: 'INR',
        missing_fields: ['transcript'],
        clarification_required: true,
        clarification_question: 'Kuchh sunai nahi diya. Kripya dobara boliye.',
        spoken_response: 'Kuchh sunai nahi diya. Kripya dobara boliye.',
        confidence: 0.0,
        ai_provider: 'deterministic-fallback',
        correlation_id: corrId
      };
    }

    // Fast-path: check for quick intents (confirm, cancel, query, correction)
    const quickResult = this.parseRuleBased(trimmed);
    quickResult.correlation_id = corrId;

    if (config.nodeEnv === 'test') {
      return quickResult;
    }

    if (quickResult.intent === 'CONFIRM_DRAFT' || quickResult.intent === 'CANCEL_DRAFT' || quickResult.intent.startsWith('QUERY_') || quickResult.intent === 'UNKNOWN') {
      return quickResult;
    }

    // Diagnostic log: AI request initiated (sanitized, no sensitive financial transcripts in production)
    if (config.nodeEnv !== 'production') {
      console.log(`[AI-DIAGNOSTIC] [${corrId}] Initiating Qwen AI request. Model: ${this.model}, Timeout: ${this.timeoutMs}ms`);
    }

    try {
      const ollamaResponse = await this.queryOllama(trimmed, corrId);
      const duration = Date.now() - startTime;

      if (ollamaResponse && this.validateOllamaResult(ollamaResponse)) {
        if (config.nodeEnv !== 'production') {
          console.log(`[AI-DIAGNOSTIC] [${corrId}] Qwen response received & validated in ${duration}ms. Type: ${ollamaResponse.transaction_type}`);
        }
        ollamaResponse.ai_provider = this.model;
        ollamaResponse.correlation_id = corrId;
        return ollamaResponse;
      } else {
        if (config.nodeEnv !== 'production') {
          console.log(`[AI-DIAGNOSTIC] [${corrId}] Qwen response invalid or empty in ${duration}ms. Activating deterministic fallback.`);
        }
      }
    } catch (err: any) {
      const duration = Date.now() - startTime;
      if (config.nodeEnv !== 'production') {
        console.log(`[AI-DIAGNOSTIC] [${corrId}] Qwen request failed in ${duration}ms (${err?.message || 'unknown'}). Activating deterministic fallback.`);
      }
    }

    quickResult.ai_provider = 'deterministic-fallback';
    return quickResult;
  }

  /**
   * Queries local Ollama daemon
   */
  private queryOllama(transcript: string, correlationId: string): Promise<VoiceCommandResult | null> {
    return new Promise((resolve) => {
      const systemPrompt = `You are a financial voice parser for Smart Expenses Tracker.
Extract transaction details into structured JSON.
Supported transaction_type values: "EXPENSE", "RECEIVED", "LEND", "BORROW", "TRANSFER".
If ambiguous or intent unclear, set clarification_required: true, intent: "UNKNOWN".
Return raw JSON only without commentary.`;

      const prompt = `Input: "${transcript}"
Output JSON schema:
{
  "intent": "CREATE_TRANSACTION" | "QUERY_BALANCE" | "QUERY_EXPENSES" | "QUERY_INCOME" | "UNKNOWN",
  "transaction_type": "EXPENSE" | "RECEIVED" | "LEND" | "BORROW" | "TRANSFER",
  "amount_minor": integer paise (e.g. 50 rs -> 5000),
  "currency_code": "INR",
  "description": string,
  "category_name": "Food" | "Salary" | "Travel" | "Shopping" | "Bills" | "Others",
  "account_name": "Cash" | "Bank" | "UPI" | "Savings",
  "transaction_date": "YYYY-MM-DD",
  "recipient": string or null,
  "source_account": string or null,
  "destination_account": string or null,
  "missing_fields": [],
  "clarification_required": boolean,
  "clarification_question": string or null,
  "spoken_response": string
}`;

      const payload = JSON.stringify({
        model: this.model,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: prompt }
        ],
        stream: false,
        format: 'json',
        options: {
          temperature: 0.1,
          num_predict: 250
        }
      });

      const urlObj = new URL(`${this.baseUrl}/api/chat`);
      const isHttps = urlObj.protocol === 'https:';
      const transport = isHttps ? https : http;

      const req = transport.request(
        {
          hostname: urlObj.hostname,
          port: urlObj.port,
          path: urlObj.pathname,
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payload)
          },
          timeout: this.timeoutMs
        },
        (res) => {
          let data = '';
          res.on('data', (chunk) => (data += chunk));
          res.on('end', () => {
            try {
              const parsedRes = JSON.parse(data);
              const content = parsedRes?.message?.content || parsedRes?.response;
              if (content && typeof content === 'string') {
                const innerJson = JSON.parse(content);
                resolve(innerJson);
              } else {
                resolve(null);
              }
            } catch {
              resolve(null);
            }
          });
        }
      );

      req.on('error', (err) => {
        if (config.nodeEnv !== 'production') {
          console.log(`[AI-DIAGNOSTIC] [${correlationId}] Ollama network error: ${err.message}`);
        }
        resolve(null);
      });

      req.on('timeout', () => {
        if (config.nodeEnv !== 'production') {
          console.log(`[AI-DIAGNOSTIC] [${correlationId}] Ollama request timed out at ${this.timeoutMs}ms`);
        }
        req.destroy();
        resolve(null);
      });

      req.write(payload);
      req.end();
    });
  }

  /**
   * Validates Ollama JSON response against strict financial schema
   */
  public validateOllamaResult(result: any): result is VoiceCommandResult {
    if (!result || typeof result !== 'object') return false;

    const validIntents = [
      'CREATE_TRANSACTION',
      'QUERY_BALANCE',
      'QUERY_EXPENSES',
      'QUERY_INCOME',
      'QUERY_CATEGORY_TOTAL',
      'CONFIRM_DRAFT',
      'CANCEL_DRAFT',
      'UNKNOWN'
    ];
    if (!validIntents.includes(result.intent)) return false;

    if (result.intent === 'CREATE_TRANSACTION') {
      const validTypes = ['EXPENSE', 'RECEIVED', 'LEND', 'BORROW', 'TRANSFER'];
      if (!validTypes.includes(result.transaction_type)) return false;

      // Validate amount is positive integer
      if (typeof result.amount_minor !== 'number' || isNaN(result.amount_minor) || result.amount_minor <= 0) {
        return false;
      }
    }

    if (result.currency_code !== 'INR') return false;
    if (typeof result.spoken_response !== 'string' || !result.spoken_response.trim()) return false;

    return true;
  }

  /**
   * Deterministic high-precision natural language parser for Hindi, Hinglish and English.
   * Multi-stage pipeline: Normalization -> Negation/Correction -> Direction -> Amount -> Validation.
   * Never defaults to EXPENSE unconditionally.
   */
  public parseRuleBased(rawInput: string): VoiceCommandResult {
    const todayStr = new Date().toISOString().split('T')[0];

    // Stage 1: Text Normalization (including Devanagari numerals and keywords)
    let text = (rawInput || '')
      .replace(/[०]/g, '0').replace(/[१]/g, '1').replace(/[२]/g, '2').replace(/[३]/g, '3')
      .replace(/[४]/g, '4').replace(/[५]/g, '5').replace(/[६]/g, '6').replace(/[७]/g, '7')
      .replace(/[८]/g, '8').replace(/[९]/g, '9')
      .replace(/रुपये|रुपया|रूपये|रूपया|₹/g, ' rupaye ')
      .replace(/कमाए|कमाया|कमाई|कमाये/g, ' kamaye ')
      .replace(/खर्च|खर्चा/g, ' kharch ')
      .replace(/उधार/g, ' udhaar ')
      .replace(/दिए|दिया/g, ' diye ')
      .replace(/लिए|लिया/g, ' liye ')
      .replace(/मिले|मिला/g, ' mile ')
      .replace(/वापस/g, ' wapas ')
      .replace(/नहीं|ना/g, ' nahi ')
      .toLowerCase()
      .trim()
      .replace(/\s+/g, ' ');

    // Stage 2: Confirmations & Cancellations
    if (/(^|\s)(haan|ha|yes|yep|confirm|save|theek hai|karo|done)(\s|$)/i.test(text) &&
        !/(kharch|rupaye|rs|diye|aaye|transfer|kamaye|udhaar|udhar)/i.test(text)) {
      return {
        intent: 'CONFIRM_DRAFT',
        currency_code: 'INR',
        missing_fields: [],
        clarification_required: false,
        spoken_response: 'Transaction save kar raha hoon.',
        confidence: 0.98
      };
    }

    if (/(^|\s)(cancel|mat karo|cancel kar do|hatao|hata do|discard|stop)(\s|$)/i.test(text) &&
        !/(kharch|rupaye|rs|diye|aaye|transfer|kamaye)/i.test(text)) {
      return {
        intent: 'CANCEL_DRAFT',
        currency_code: 'INR',
        missing_fields: [],
        clarification_required: false,
        spoken_response: 'Entry cancel kar di gayi.',
        confidence: 0.98
      };
    }

    // Stage 3: Financial Queries
    if (text.includes('balance') || text.includes('kitne paise') || text.includes('kitna balance')) {
      return {
        intent: 'QUERY_BALANCE',
        currency_code: 'INR',
        missing_fields: [],
        clarification_required: false,
        spoken_response: 'Aapka balance check kar raha hoon.',
        confidence: 0.95
      };
    }

    if (text.includes('food') && (text.includes('kharch') || text.includes('spent') || text.includes('kitna'))) {
      return {
        intent: 'QUERY_CATEGORY_TOTAL',
        category_name: 'Food',
        currency_code: 'INR',
        missing_fields: [],
        clarification_required: false,
        spoken_response: 'Food category ka total kharcha dekh raha hoon.',
        confidence: 0.95
      };
    }

    if (text.includes('kitna kharch') || text.includes('total expense') || text.includes('kharcha batao')) {
      return {
        intent: 'QUERY_EXPENSES',
        currency_code: 'INR',
        missing_fields: [],
        clarification_required: false,
        spoken_response: 'Aapke kharche ka total calculate kar raha hoon.',
        confidence: 0.95
      };
    }

    // Stage 4: Correction / Edit Requests (Must not blindly create a transaction)
    if (/(correct karo|theek karo|badal do|change karo|pehle wala|hisaab kar dena)/i.test(text)) {
      return {
        intent: 'UNKNOWN',
        currency_code: 'INR',
        missing_fields: ['clarification'],
        clarification_required: true,
        clarification_question: 'Aap kaun sa record update ya clarify karna chahte hain?',
        spoken_response: 'Aap kaun sa record update ya clarify karna chahte hain?',
        confidence: 0.8
      };
    }

    // Stage 5: Amount Extraction (Rupees to integer minor units: paise)
    let amountMinor = 0;
    const amountMatch = text.match(/(?:rs\.?|inr|₹)?\s*(\d+(?:\.\d{1,2})?)\s*(?:rupaye|rupayee|rupees|rs|inr|k)?/i);
    if (amountMatch) {
      let rawNum = parseFloat(amountMatch[1]);
      if (text.includes(`${amountMatch[1]}k`) || text.includes(`${amountMatch[1]} hazar`)) {
        rawNum = rawNum * 1000;
      } else if (text.includes(`${amountMatch[1]} lakh`)) {
        rawNum = rawNum * 100000;
      }
      amountMinor = Math.round(rawNum * 100);
    } else {
      // Word amounts
      if (text.includes('dedh sau') || text.includes('150')) amountMinor = 15000;
      else if (text.includes('dhai sau') || text.includes('250')) amountMinor = 25000;
      else if (text.includes('paanch sau') || text.includes('500')) amountMinor = 50000;
      else if (text.includes('hazar') || text.includes('1000')) amountMinor = 100000;
    }

    // Stage 6: Date Extraction
    let txDate = todayStr;
    if (text.includes('kal') || text.includes('yesterday')) {
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      txDate = yesterday.toISOString().split('T')[0];
    }

    // Stage 7: Negation & Correction Clauses
    const hasNegatedIncome = /(kamaye nahi|nahi kamaye|income nahi|earned nahi)/i.test(text);
    const hasNegatedExpense = /(kharch nahi|nahi kharch|expense nahi|kharcha nahi)/i.test(text);

    // Stage 8: Directional Intent Identification
    let txType: 'EXPENSE' | 'RECEIVED' | 'LEND' | 'BORROW' | 'TRANSFER' | null = null;
    let description = 'Transaction';
    let categoryName = 'Others';
    let accountName = 'UPI';
    let recipient: string | null = null;
    let sourceAccount: string | null = null;
    let destinationAccount: string | null = null;

    const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();

    // Check Transfer
    if (/(transfer|shift|bhejo|bheje|account se|wallet mein)/i.test(text) && !/(udhaar|udhar)/i.test(text)) {
      txType = 'TRANSFER';
      description = 'Account Transfer';
      sourceAccount = text.includes('cash') ? 'Cash' : 'Bank';
      destinationAccount = text.includes('bank') ? 'Bank' : text.includes('wallet') ? 'Cash' : 'Savings';
      categoryName = 'Others';
      accountName = sourceAccount;
    }
    // Check Loan Repayment Received (e.g. "Ali ne mere 500 wapas kiye")
    else if (/(wapas kiye|wapas diye|wapas mile|lautaye|repayment|udhar wapas)/i.test(text)) {
      txType = 'RECEIVED';
      const nameMatch = text.match(/([a-zA-Z\u0900-\u097F]+)\s+(?:ne|se)/i);
      recipient = nameMatch ? capitalize(nameMatch[1]) : 'Friend';
      description = `${recipient} - Loan Repayment Received`;
      categoryName = 'Others';
      accountName = 'Bank';
    }
    // Check Lending (User gave loan to counterparty)
    else if (/(udhaar diye|udhar diye|udhar diya|udhaar diya|lent|karz diya|loan diya|diye udhar|diye udhaar)/i.test(text)) {
      txType = 'LEND';
      const nameMatch = text.match(/([a-zA-Z\u0900-\u097F]+)\s+ko/i);
      recipient = nameMatch ? capitalize(nameMatch[1]) : 'Friend';
      description = `${recipient} - Udhar Diye`;
      categoryName = 'Others';
      accountName = 'Cash';
    }
    // Check Borrowing (User took loan from counterparty)
    else if (/(udhaar liye|udhar liye|udhar liya|udhaar liya|borrowed|karz liya|loan liya|liye udhar|liye udhaar|udhar mile|udhaar mile|udhar mila|udhaar mila)/i.test(text)) {
      txType = 'BORROW';
      const nameMatch = text.match(/([a-zA-Z\u0900-\u097F]+)\s+se/i);
      recipient = nameMatch ? capitalize(nameMatch[1]) : 'Friend';
      description = `${recipient} - Udhar Liye`;
      categoryName = 'Others';
      accountName = 'Cash';
    }
    // Check Income / Earning (when income is not negated)
    else if (!hasNegatedIncome && /(kamaye|kamaya|kamaaye|kamai|earned|earning|salary|paise aaye|payment aayi|credit|munafa|bachat|received|income)/i.test(text)) {
      txType = 'RECEIVED';
      if (text.includes('salary')) {
        description = 'Salary Income';
        categoryName = 'Salary';
        accountName = 'Bank';
      } else {
        description = 'Earnings';
        categoryName = 'Salary';
        accountName = text.includes('cash') ? 'Cash' : text.includes('bank') ? 'Bank' : 'UPI';
      }
    }
    // Check Expense (either explicit expense words or correction clause "kamaye nahi, kharch kiye")
    else if (!hasNegatedExpense && (hasNegatedIncome || /(kharch|kharcha|spent|kharida|kharidi|paid|payment ki|bill bhara|chuka diya|petrol|diesel|travel|fuel|cab|auto|shopping|kapde|clothes|food|lunch|dinner|chai|nashta|khana|bijli|electricity|recharge|wifi)/i.test(text))) {
      txType = 'EXPENSE';
      if (/(petrol|diesel|travel|fuel|auto|cab)/i.test(text)) {
        categoryName = 'Travel';
        description = 'Travel Expense';
      } else if (/(shopping|kapde|clothes|flipkart|amazon)/i.test(text)) {
        categoryName = 'Shopping';
        description = 'Shopping Expense';
      } else if (/(bill|bijli|electricity|recharge|wifi)/i.test(text)) {
        categoryName = 'Bills';
        description = 'Utility Bill';
      } else if (/(food|lunch|dinner|chai|nashta|khana)/i.test(text)) {
        categoryName = 'Food';
        description = text.includes('lunch') ? 'Lunch' : text.includes('dinner') ? 'Dinner' : 'Food Expense';
      } else {
        categoryName = 'Others';
        description = 'Expense';
      }

      if (text.includes('cash') || text.includes('nagad')) {
        accountName = 'Cash';
      } else if (text.includes('bank') || text.includes('card') || text.includes('debit')) {
        accountName = 'Bank';
      } else {
        accountName = 'UPI';
      }
    }

    // Stage 9: Ambiguity Guard (e.g. "Maine paise diye" or "Mujhe paise mile" without context)
    if (!txType) {
      if (/(paise diye|payment diya|rupaye diye)/i.test(text)) {
        const amtStr = amountMinor > 0 ? `₹${amountMinor / 100}` : 'paise';
        return {
          intent: 'UNKNOWN',
          currency_code: 'INR',
          missing_fields: amountMinor <= 0 ? ['amount', 'purpose'] : ['purpose'],
          clarification_required: true,
          clarification_question: `Ye ${amtStr} kis purpose ke liye diye: kharch, udhaar diya, ya transfer?`,
          spoken_response: `Ye ${amtStr} kis purpose ke liye diye: kharch, udhaar diya, ya transfer?`,
          confidence: 0.4
        };
      }

      if (/(paise mile|payment mila|rupaye mile)/i.test(text)) {
        const amtStr = amountMinor > 0 ? `₹${amountMinor / 100}` : 'paise';
        return {
          intent: 'UNKNOWN',
          currency_code: 'INR',
          missing_fields: amountMinor <= 0 ? ['amount', 'purpose'] : ['purpose'],
          clarification_required: true,
          clarification_question: `Ye ${amtStr} kis roop mein mile: kamai/income, gift, ya udhaar wapas?`,
          spoken_response: `Ye ${amtStr} kis roop mein mile: kamai/income, gift, ya udhaar wapas?`,
          confidence: 0.4
        };
      }

      // Completely unknown intent: NEVER default to EXPENSE!
      return {
        intent: 'UNKNOWN',
        currency_code: 'INR',
        missing_fields: ['intent'],
        clarification_required: true,
        clarification_question: 'Ye transaction samajh nahi aaya. Kripya clear boliye (jaise: ₹50 kharch ya ₹50 kamaye).',
        spoken_response: 'Ye transaction samajh nahi aaya. Kripya clear boliye.',
        confidence: 0.0
      };
    }

    // Stage 10: Validation Layer (Missing Amount Guard)
    const missingFields: string[] = [];
    if (amountMinor <= 0) {
      missingFields.push('amount');
    }

    const clarificationRequired = missingFields.length > 0;
    const clarificationQuestion = clarificationRequired ? 'Kitne rupaye ka transaction tha?' : null;

    let spokenResponse = '';
    if (clarificationRequired) {
      spokenResponse = 'Kitne rupaye ka transaction record karna hai?';
    } else {
      const rupees = amountMinor / 100;
      if (txType === 'EXPENSE') {
        spokenResponse = `${rupees} rupaye ka ${categoryName} expense ${accountName} se save kar doon?`;
      } else if (txType === 'RECEIVED') {
        spokenResponse = `${rupees} rupaye ka payment ${accountName} mein record kar doon?`;
      } else if (txType === 'LEND') {
        spokenResponse = `${recipient || 'Friend'} ko ${rupees} rupaye udhar diye, record kar doon?`;
      } else if (txType === 'BORROW') {
        spokenResponse = `${recipient || 'Friend'} se ${rupees} rupaye udhar mile, record kar doon?`;
      } else if (txType === 'TRANSFER') {
        spokenResponse = `₹${rupees} transfer karne ke liye confirm kijiye.`;
      }
    }

    return {
      intent: 'CREATE_TRANSACTION',
      transaction_type: txType,
      amount_minor: amountMinor,
      currency_code: 'INR',
      description,
      category_name: categoryName,
      account_name: accountName,
      transaction_date: txDate,
      recipient,
      source_account: sourceAccount,
      destination_account: destinationAccount,
      missing_fields: missingFields,
      clarification_required: clarificationRequired,
      clarification_question: clarificationQuestion,
      spoken_response: spokenResponse,
      confidence: 0.95
    };
  }
}

export const qwenProvider = new QwenProvider();
