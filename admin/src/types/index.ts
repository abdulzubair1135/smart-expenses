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

export interface MonitoringSummary {
  totalEvents: number;
  newEvents: number;
  criticalEvents: number;
  errorEvents: number;
  warningEvents: number;
  resolvedEvents: number;
  eventsToday: number;
  syncFailuresCount: number;
  lastIngestedAt: string | null;
}

export interface MonitoringEvent {
  id: string;
  eventType: string;
  severity: 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
  source: 'ANDROID' | 'BACKEND' | 'DATABASE' | 'SYNC' | 'ADMIN';
  errorCode: string;
  message: string;
  stackTrace?: string;
  screenName?: string;
  operationName?: string;
  appVersion: string;
  appBuild: string;
  androidVersion?: string;
  deviceModel?: string;
  environment: string;
  userId?: string | null;
  installationId: string;
  correlationId: string;
  occurredAt: string;
  receivedAt: string;
  status: 'NEW' | 'ACKNOWLEDGED' | 'RESOLVED' | 'IGNORED';
  occurrenceCount: number;
  metadataJson: Record<string, any>;
}

export interface MonitoringHistory {
  id: string;
  eventId: string;
  oldStatus: string;
  newStatus: string;
  changedBy: string;
  reason?: string;
  changedAt: string;
}

export interface ServiceHealth {
  renderApi: {
    status: string;
    uptimeSeconds: number;
    nodeVersion: string;
  };
  databaseConnectivity: string | {
    connected: boolean;
    configured?: boolean;
    latencyMs?: number;
    message?: string;
  };
  monitoringIngestion: {
    status: string;
    eventsQueuedLocally: number;
    lastIngestedAt: string | null;
  };
  authentication: {
    status: string;
    mode: string;
  };
  backgroundSync: {
    status: string;
    syncFailuresCount: number;
  };
}

export interface UserTransaction {
  id: string;
  userId: string;
  type: 'expense' | 'received' | 'lend' | 'borrow' | 'transfer';
  amountMinor: number;
  amountFormatted: string;
  currencyCode: string;
  categoryName: string;
  categoryIcon: string;
  categoryColor: string;
  accountName: string;
  accountType: string;
  description: string;
  transactionDate: string;
  status: string;
  relatedPerson: string | null;
  createdAt: string;
}

export interface UserTransactionLedger {
  userId: string;
  totalCount: number;
  totalSpentMinor: number;
  totalSpentFormatted: string;
  totalReceivedMinor: number;
  totalReceivedFormatted: string;
  transactions: UserTransaction[];
}

