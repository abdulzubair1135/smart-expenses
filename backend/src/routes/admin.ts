import { Router } from 'express';
import { authenticateAdmin } from '../middleware/adminAuth';
import { store } from '../repositories/store';

export const adminRouter = Router();

// 1. Overview metrics
adminRouter.get('/overview', authenticateAdmin, (req, res) => {
  const users = Array.from(store.profiles.values());
  const verifiedCount = users.filter(u => u.accountStatus === 'verified').length;
  const unverifiedCount = users.filter(u => u.accountStatus === 'unverified').length;
  const suspendedCount = users.filter(u => u.accountStatus === 'suspended').length;
  const reviewCount = users.filter(u => u.accountStatus === 'requires_review').length;

  res.status(200).json({
    success: true,
    data: {
      totalRegisteredUsers: users.length,
      verifiedUsers: verifiedCount,
      unverifiedUsers: unverifiedCount,
      suspendedUsers: suspendedCount,
      requiresReviewUsers: reviewCount,
      totalTransactionsRecorded: store.transactions.size,
      totalConnectedDevices: store.deviceTokens.size,
      syncFailuresCount: 0,
      serverUptimeSeconds: Math.floor(process.uptime()),
      apiHealth: 'healthy',
      databaseConnectivity: 'connected'
    }
  });
});

// 2. User list
adminRouter.get('/users', authenticateAdmin, (req, res) => {
  const users = Array.from(store.profiles.values());
  res.status(200).json({ success: true, data: users });
});

// 3. User status modification
adminRouter.patch('/users/:id/status', authenticateAdmin, (req, res) => {
  const id = req.params.id as string;
  const { status } = req.body;

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

// 4. Audit logs
adminRouter.get('/audit-logs', authenticateAdmin, (req, res) => {
  res.status(200).json({ success: true, data: store.auditLogs });
});
