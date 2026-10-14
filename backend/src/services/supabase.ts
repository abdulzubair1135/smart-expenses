import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { config } from '../config';

const isMockUrl = (url: string): boolean => {
  return !url || url.includes('mock-supabase-project') || url.includes('your-project-id');
};

export const isSupabaseConfigured = (): boolean => {
  return !isMockUrl(config.supabase.url) && Boolean(config.supabase.anonKey);
};

// Administrative Supabase Client (Service Role - bypasses RLS for backend management)
export const supabaseAdmin: SupabaseClient | null = isSupabaseConfigured()
  ? createClient(config.supabase.url, config.supabase.serviceRoleKey || config.supabase.anonKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false
      }
    })
  : null;

// User Scoped Supabase Client (Enforces Row Level Security via user token)
export const getSupabaseUserClient = (token?: string): SupabaseClient | null => {
  if (!isSupabaseConfigured()) return null;

  return createClient(config.supabase.url, config.supabase.anonKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false
    },
    global: {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    }
  });
};

export interface DatabaseProbeResult {
  connected: boolean;
  configured: boolean;
  latencyMs?: number;
  message: string;
}

// Probes Supabase PostgreSQL database connectivity without leaking credentials
export async function probeDatabaseConnectivity(): Promise<DatabaseProbeResult> {
  if (!isSupabaseConfigured()) {
    return {
      connected: false,
      configured: false,
      message: 'Supabase URL or keys are not yet configured in environment variables'
    };
  }

  const startTime = Date.now();
  try {
    const client = supabaseAdmin || getSupabaseUserClient();
    if (!client) {
      return { connected: false, configured: false, message: 'Database client initialization failed' };
    }

    // Query categories table count (lightweight head request)
    const { error } = await client
      .from('categories')
      .select('count', { count: 'exact', head: true });

    const latencyMs = Date.now() - startTime;

    if (error) {
      return {
        connected: false,
        configured: true,
        latencyMs,
        message: error.message || 'Database query error'
      };
    }

    return {
      connected: true,
      configured: true,
      latencyMs,
      message: 'PostgreSQL database connected and operational'
    };
  } catch (err: any) {
    return {
      connected: false,
      configured: true,
      latencyMs: Date.now() - startTime,
      message: err.message || 'Network timeout or unreachable database host'
    };
  }
}
