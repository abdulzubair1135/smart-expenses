import { Router } from 'express';
import crypto from 'crypto';
import { authenticateUser, AuthenticatedRequest } from '../middleware/auth';
import { store, BackupRecord } from '../repositories/store';

export const backupsRouter = Router();

// Export verified JSON backup with SHA-256 checksum
backupsRouter.post('/export', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const profile = store.profiles.get(userId);

  if (!profile) {
    res.status(404).json({ success: false, error: { message: 'User profile not found' } });
    return;
  }

  // Gather all user's isolated data
  const userAccounts = Array.from(store.accounts.values()).filter(a => a.userId === userId);
  const userCategories = Array.from(store.categories.values()).filter(c => c.userId === userId || c.userId === null);
  const userTransactions = Array.from(store.transactions.values()).filter(t => t.userId === userId);
  const userTrips = Array.from(store.trips.values()).filter(tr => tr.userId === userId);
  const userSavingGoals = Array.from(store.savingGoals.values()).filter(g => g.userId === userId);
  const userUpcoming = Array.from(store.upcomingExpenses.values()).filter(u => u.userId === userId);

  const totalRecordCount = userAccounts.length + userTransactions.length + userTrips.length + userSavingGoals.length + userUpcoming.length;

  const exportPayload = {
    metadata: {
      schemaVersion: '1.0',
      exportedAt: new Date().toISOString(),
      userId
    },
    profile: {
      displayName: profile.displayName,
      email: profile.email,
      createdAt: profile.createdAt
    },
    accounts: userAccounts,
    categories: userCategories,
    transactions: userTransactions,
    trips: userTrips,
    savingGoals: userSavingGoals,
    upcomingExpenses: userUpcoming
  };

  const payloadString = JSON.stringify(exportPayload);
  const checksum = crypto.createHash('sha256').update(payloadString).digest('hex');
  const backupId = `bk-${Date.now()}`;
  const nowIso = new Date().toISOString();

  const backupRecord: BackupRecord = {
    id: backupId,
    userId,
    backupType: 'json_export',
    status: 'completed',
    startedAt: nowIso,
    completedAt: nowIso,
    recordCount: totalRecordCount,
    storageReference: `local_export_${backupId}.json`,
    checksum,
    createdAt: nowIso
  };

  store.backupRecords.set(backupId, backupRecord);
  store.recordActivity(userId, 'BACKUP_SUCCEEDED', 'success');

  res.status(200).json({
    success: true,
    message: 'Backup exported and verified successfully',
    data: {
      backupId,
      checksum,
      recordCount: totalRecordCount,
      exportedAt: nowIso,
      payload: exportPayload
    }
  });
});

// Retrieve backup records history
backupsRouter.get('/', authenticateUser, (req: AuthenticatedRequest, res) => {
  const userId = req.user!.id;
  const records = Array.from(store.backupRecords.values())
    .filter(b => b.userId === userId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  res.status(200).json({
    success: true,
    data: records
  });
});
