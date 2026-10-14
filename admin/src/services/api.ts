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
  }
};
