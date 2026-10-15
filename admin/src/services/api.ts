import { AdminMetrics, UserProfile, AuditLog, ActivityEvent } from '../types';

const resolveBaseUrl = (): string => {
  if (typeof window !== 'undefined' && localStorage.getItem('adminApiBaseUrl')) {
    return localStorage.getItem('adminApiBaseUrl')!;
  }
  return 'https://smart-expenses-8pf3.onrender.com/api/v1/admin';
};

export const adminApi = {
  getBaseUrl(): string {
    return resolveBaseUrl();
  },

  setBaseUrl(url: string) {
    if (typeof window !== 'undefined') {
      localStorage.setItem('adminApiBaseUrl', url);
    }
  },

  async getOverview(adminKey: string): Promise<AdminMetrics> {
    const res = await fetch(`${resolveBaseUrl()}/overview`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Unauthorized or failed to fetch metrics');
    const json = await res.json();
    return json.data;
  },

  async getUsers(adminKey: string): Promise<UserProfile[]> {
    const res = await fetch(`${resolveBaseUrl()}/users`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch users');
    const json = await res.json();
    return json.data;
  },

  async updateUserStatus(adminKey: string, userId: string, status: string): Promise<void> {
    const res = await fetch(`${resolveBaseUrl()}/users/${userId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-key': adminKey
      },
      body: JSON.stringify({ status })
    });
    if (!res.ok) throw new Error('Failed to update status');
  },

  async getUserActivity(adminKey: string, userId: string): Promise<ActivityEvent[]> {
    const res = await fetch(`${resolveBaseUrl()}/users/${userId}/activity`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch user activity');
    const json = await res.json();
    return json.data;
  },

  async getAuditLogs(adminKey: string): Promise<AuditLog[]> {
    const res = await fetch(`${resolveBaseUrl()}/audit-logs`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch audit logs');
    const json = await res.json();
    return json.data;
  },

  async getMonitoringSummary(adminKey: string): Promise<import('../types').MonitoringSummary> {
    const res = await fetch(`${resolveBaseUrl()}/monitoring/summary`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch monitoring summary');
    const json = await res.json();
    return json.data;
  },

  async getMonitoringEvents(adminKey: string, params: Record<string, string>): Promise<{ events: import('../types').MonitoringEvent[]; pagination: any }> {
    const searchParams = new URLSearchParams(params);
    const res = await fetch(`${resolveBaseUrl()}/monitoring/events?${searchParams.toString()}`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch monitoring events');
    const json = await res.json();
    return json.data;
  },

  async getMonitoringEventById(adminKey: string, id: string): Promise<{ event: import('../types').MonitoringEvent; history: import('../types').MonitoringHistory[] }> {
    const res = await fetch(`${resolveBaseUrl()}/monitoring/events/${id}`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch monitoring event details');
    const json = await res.json();
    return json.data;
  },

  async updateMonitoringEventStatus(adminKey: string, id: string, status: string, reason?: string): Promise<void> {
    const res = await fetch(`${resolveBaseUrl()}/monitoring/events/${id}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-key': adminKey
      },
      body: JSON.stringify({ status, reason })
    });
    if (!res.ok) throw new Error('Failed to update event status');
  },

  async getMonitoringHealth(adminKey: string): Promise<import('../types').ServiceHealth> {
    const res = await fetch(`${resolveBaseUrl()}/monitoring/health`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch service health');
    const json = await res.json();
    return json.data;
  },

  async getUserTransactions(adminKey: string, userId: string): Promise<import('../types').UserTransactionLedger> {
    const res = await fetch(`${resolveBaseUrl()}/users/${userId}/transactions`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch user transactions');
    const json = await res.json();
    return json.data;
  }
};
