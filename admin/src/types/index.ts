export interface AdminMetrics {
  totalRegisteredUsers: number;
  verifiedUsers: number;
  unverifiedUsers: number;
  suspendedUsers: number;
  requiresReviewUsers: number;
  onlineUsers: number;
  offlineUsers: number;
  dailyActiveUsers: number;
  weeklyActiveUsers: number;
  totalBackupsCompleted: number;
  totalBackupsFailed: number;
  totalTransactionsRecorded: number;
  totalConnectedDevices: number;
  syncFailuresCount: number;
  serverUptimeSeconds: number;
  apiHealth: string;
  databaseConnectivity: string;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  accountStatus: 'verified' | 'unverified' | 'suspended' | 'requires_review';
  isOnline: boolean;
  lastSeenAt: string | null;
  lastHeartbeatAt: string | null;
  latestBackupAt: string | null;
  backupStatus: 'completed' | 'started' | 'failed' | 'none';
  createdAt: string;
}

export interface ActivityEvent {
  id: string;
  eventType: string;
  occurredAt: string;
  result: 'success' | 'failure' | 'pending';
  metadata?: Record<string, any>;
}

export interface AuditLog {
  id: string;
  adminId: string;
  action: string;
  targetType: string;
  targetId: string;
  details: any;
  ipAddress: string;
  createdAt: string;
}
