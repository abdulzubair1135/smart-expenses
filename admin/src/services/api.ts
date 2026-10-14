import { AdminMetrics, UserProfile, AuditLog } from '../types';

const BASE_URL = 'http://localhost:4000/api/v1/admin';

export const adminApi = {
  async getOverview(adminKey: string): Promise<AdminMetrics> {
    const res = await fetch(`${BASE_URL}/overview`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Unauthorized or failed to fetch metrics');
    const json = await res.json();
    return json.data;
  },

  async getUsers(adminKey: string): Promise<UserProfile[]> {
    const res = await fetch(`${BASE_URL}/users`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch users');
    const json = await res.json();
    return json.data;
  },

  async updateUserStatus(adminKey: string, userId: string, status: string): Promise<void> {
    const res = await fetch(`${BASE_URL}/users/${userId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-key': adminKey
      },
      body: JSON.stringify({ status })
    });
    if (!res.ok) throw new Error('Failed to update status');
  },

  async getAuditLogs(adminKey: string): Promise<AuditLog[]> {
    const res = await fetch(`${BASE_URL}/audit-logs`, {
      headers: { 'x-admin-key': adminKey }
    });
    if (!res.ok) throw new Error('Failed to fetch audit logs');
    const json = await res.json();
    return json.data;
  }
};
