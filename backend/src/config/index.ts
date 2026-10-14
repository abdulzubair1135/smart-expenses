import dotenv from 'dotenv';
dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '4000', 10),
  nodeEnv: process.env.NODE_ENV || 'development',
  corsOrigin: process.env.CORS_ORIGIN || '*',
  supabase: {
    url: process.env.SUPABASE_URL || 'https://mock-supabase-project.supabase.co',
    anonKey: process.env.SUPABASE_ANON_KEY || 'mock-anon-key',
    serviceRoleKey: process.env.SUPABASE_SERVICE_ROLE_KEY || 'mock-service-role-key',
  },
  admin: {
    secretKey: process.env.ADMIN_SECRET_KEY || 'smart-expenses-admin-secret-2026',
  },
  fcm: {
    serverKey: process.env.FCM_SERVER_KEY || '',
  },
  ai: {
    provider: process.env.AI_PROVIDER || 'ollama',
    ollamaBaseUrl: process.env.OLLAMA_BASE_URL || 'http://127.0.0.1:11434',
    qwenModel: process.env.QWEN_MODEL || 'qwen3.5:4b',
    timeoutMs: parseInt(process.env.AI_REQUEST_TIMEOUT_MS || '4000', 10),
    maxRetries: parseInt(process.env.AI_MAX_RETRIES || '1', 10),
  }
};

