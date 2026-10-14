export interface AdminMetrics {
  totalRegisteredUsers: number;
  verifiedUsers: number;
  unverifiedUsers: number;
  suspendedUsers: number;
  requiresReviewUsers: number;
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
  createdAt: string;
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
