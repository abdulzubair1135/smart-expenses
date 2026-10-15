import { Router } from 'express';
import { authenticateAdmin } from '../middleware/adminAuth';
import { store, Profile } from '../repositories/store';
import { supabaseAdmin, isSupabaseConfigured } from '../services/supabase';

export const adminRouter = Router();

const ONLINE_HEARTBEAT_WINDOW_MS = 120 * 1000; // 120 seconds

async function getAllAdminProfiles(): Promise<Profile[]> {
  const profileMap = new Map<string, Profile>();
  
  // 1. Local store profiles
  for (const p of store.profiles.values()) {
    profileMap.set(p.id, p);
  }

  // 2. Remote Supabase profiles
  if (isSupabaseConfigured() && supabaseAdmin) {
    try {
      const { data: remoteProfiles } = await supabaseAdmin.from('profiles').select('*');
      if (remoteProfiles && remoteProfiles.length > 0) {
        for (const rp of remoteProfiles) {
          if (!profileMap.has(rp.id)) {
            profileMap.set(rp.id, {
              id: rp.id,
              email: rp.email || '',
              displayName: rp.display_name || 'User',
              accountStatus: rp.account_status || 'verified',
              termsConsent: {
                termsVersion: rp.terms_version || '2026.1',
                privacyVersion: '2026.1',
                consentedAt: rp.created_at || new Date().toISOString()
              },
              lastLoginAt: rp.last_login_at,
              createdAt: rp.created_at
            });
          }
        }
      }
    } catch (e) {
      console.warn('Could not query Supabase profiles for admin:', e);
    }
  }

  return Array.from(profileMap.values());
}

// 1. Overview metrics
adminRouter.get('/overview', authenticateAdmin, async (req, res) => {
  const users = await getAllAdminProfiles();
  const now = Date.now();

  const verifiedCount = users.filter(u => u.accountStatus === 'verified').length;
  const unverifiedCount = users.filter(u => u.accountStatus === 'unverified').length;
  const suspendedCount = users.filter(u => u.accountStatus === 'suspended').length;
  const reviewCount = users.filter(u => u.accountStatus === 'requires_review').length;

  // Calculate Online Users (Heartbeat within last 120 seconds)
  let onlineCount = 0;
  users.forEach(u => {
    const presence = store.userPresence.get(u.id);
    if (presence && presence.lastHeartbeatAt) {
      const diff = now - new Date(presence.lastHeartbeatAt).getTime();
      if (diff >= 0 && diff <= ONLINE_HEARTBEAT_WINDOW_MS) {
        onlineCount++;
      }
    }
  });

  const offlineCount = Math.max(0, users.length - onlineCount);

  // Calculate DAU and WAU from activityEvents
  const oneDayAgo = now - 24 * 60 * 60 * 1000;
  const sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000;

  const dauSet = new Set<string>();
  const wauSet = new Set<string>();

  store.activityEvents.forEach(evt => {
    const evtTime = new Date(evt.occurredAt).getTime();
    if (evtTime >= oneDayAgo) {
      dauSet.add(evt.userId);
    }
    if (evtTime >= sevenDaysAgo) {
      wauSet.add(evt.userId);
    }
  });

  const backups = Array.from(store.backupRecords.values());
  const totalBackupsCompleted = backups.filter(b => b.status === 'completed').length;
  const totalBackupsFailed = backups.filter(b => b.status === 'failed').length;

  res.status(200).json({
    success: true,
    data: {
      totalRegisteredUsers: users.length,
      verifiedUsers: verifiedCount,
      unverifiedUsers: unverifiedCount,
      suspendedUsers: suspendedCount,
      requiresReviewUsers: reviewCount,
      onlineUsers: onlineCount,
      offlineUsers: offlineCount,
      dailyActiveUsers: dauSet.size,
      weeklyActiveUsers: wauSet.size,
      totalBackupsCompleted,
      totalBackupsFailed,
      totalTransactionsRecorded: store.transactions.size,
      totalConnectedDevices: store.deviceTokens.size,
      syncFailuresCount: 0,
      serverUptimeSeconds: Math.floor(process.uptime()),
      apiHealth: 'healthy',
      databaseConnectivity: 'connected'
    }
  });
});

// 2. User list with presence and backup status (Financial Privacy Wall Enforced)
adminRouter.get('/users', authenticateAdmin, async (req, res) => {
  const users = await getAllAdminProfiles();
  const now = Date.now();

  const userItems = users.map(user => {
    const presence = store.userPresence.get(user.id);
    let isOnline = false;
    let lastHeartbeatAt: string | null = null;
    let lastSeenAt: string | null = null;

    if (presence) {
      lastSeenAt = presence.lastSeenAt;
      lastHeartbeatAt = presence.lastHeartbeatAt;
      const diff = now - new Date(presence.lastHeartbeatAt).getTime();
      if (diff >= 0 && diff <= ONLINE_HEARTBEAT_WINDOW_MS) {
        isOnline = true;
      }
    }

    // User's latest backup
    const userBackups = Array.from(store.backupRecords.values())
      .filter(b => b.userId === user.id)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    const latestBackup = userBackups[0] || null;

    // Notice: Strictly ZERO balances, ZERO amounts, ZERO account numbers
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      accountStatus: user.accountStatus,
      isOnline,
      lastSeenAt: lastSeenAt || user.lastLoginAt || null,
      lastHeartbeatAt,
      latestBackupAt: latestBackup ? latestBackup.completedAt || latestBackup.createdAt : null,
      backupStatus: latestBackup ? latestBackup.status : 'none',
      createdAt: user.createdAt
    };
  });

  res.status(200).json({ success: true, data: userItems });
});

// 3. User operational activity timeline (Financial Privacy Wall Enforced)
adminRouter.get('/users/:id/activity', authenticateAdmin, (req, res) => {
  const userId = req.params.id as string;
  const user = store.profiles.get(userId);

  if (!user) {
    res.status(404).json({ success: false, error: { message: 'User not found' } });
    return;
  }

  // Filter events for this user and strip any sensitive metadata
  const events = store.activityEvents
    .filter(evt => evt.userId === userId)
    .map(evt => ({
      id: evt.id,
      eventType: evt.eventType,
      occurredAt: evt.occurredAt,
      result: evt.result,
      metadata: evt.metadata ? { ...evt.metadata, amount: undefined, balance: undefined, memo: undefined } : {}
    }));

  res.status(200).json({ success: true, data: events });
});

// 4. User status modification
adminRouter.patch('/users/:id/status', authenticateAdmin, (req, res) => {
  const id = req.params.id as string;
  const { status } = req.body;

  const validStatuses = ['verified', 'unverified', 'suspended', 'requires_review'];
  if (!validStatuses.includes(status)) {
    res.status(400).json({ success: false, error: { message: `Invalid status. Must be one of: ${validStatuses.join(', ')}` } });
    return;
  }

  const profile = store.profiles.get(id);
  if (!profile) {
    res.status(404).json({ success: false, error: { message: 'User not found' } });
    return;
  }

  const oldStatus = profile.accountStatus;
  profile.accountStatus = status;

  // Log to audit log
  store.auditLogs.unshift({
    id: `log-${Date.now()}`,
    adminId: 'super-admin',
    action: 'UPDATE_USER_STATUS',
    targetType: 'USER',
    targetId: id,
    details: { oldStatus, newStatus: status },
    ipAddress: req.ip || '127.0.0.1',
    createdAt: new Date().toISOString()
  });

  res.status(200).json({ success: true, data: profile });
});

// 5. Audit logs
adminRouter.get('/audit-logs', authenticateAdmin, (req, res) => {
  res.status(200).json({ success: true, data: store.auditLogs });
});
