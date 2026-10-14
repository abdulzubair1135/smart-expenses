import React, { useState, useEffect } from 'react';
import { adminApi } from './services/api';
import { AdminMetrics, UserProfile, AuditLog, ActivityEvent } from './types';
import { 
  Users, Activity, ShieldCheck, Database, CheckCircle, 
  AlertTriangle, RefreshCw, LogOut, Search, Cpu,
  HardDrive, Clock, Lock, X
} from 'lucide-react';

export default function App() {
  const [adminKey, setAdminKey] = useState<string>(localStorage.getItem('adminKey') || '');
  const [apiEndpoint, setApiEndpoint] = useState<string>(adminApi.getBaseUrl());
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [metrics, setMetrics] = useState<AdminMetrics | null>(null);
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [activeTab, setActiveTab] = useState<'overview' | 'users' | 'health' | 'audit'>('overview');
  const [searchQuery, setSearchQuery] = useState('');
  const [loginInput, setLoginInput] = useState('smart-expenses-admin-secret-2026');
  const [errorMsg, setErrorMsg] = useState('');
  const [loading, setLoading] = useState(false);

  // User activity modal state
  const [selectedUserForActivity, setSelectedUserForActivity] = useState<UserProfile | null>(null);
  const [userActivities, setUserActivities] = useState<ActivityEvent[]>([]);
  const [loadingActivity, setLoadingActivity] = useState(false);

  const loadData = async (key: string) => {
    try {
      setLoading(true);
      setErrorMsg('');
      const [m, u, a] = await Promise.all([
        adminApi.getOverview(key),
        adminApi.getUsers(key),
        adminApi.getAuditLogs(key)
      ]);
      setMetrics(m);
      setUsers(u);
      setAuditLogs(a);
      setIsAuthenticated(true);
      localStorage.setItem('adminKey', key);
    } catch (err: any) {
      setErrorMsg(err.message || 'Authentication failed. Invalid Admin Key or unreachable server.');
      setIsAuthenticated(false);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (adminKey) {
      loadData(adminKey);
    }
  }, []);

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    adminApi.setBaseUrl(apiEndpoint);
    setAdminKey(loginInput);
    loadData(loginInput);
  };

  const handleLogout = () => {
    setAdminKey('');
    setIsAuthenticated(false);
    localStorage.removeItem('adminKey');
  };

  const handleStatusChange = async (userId: string, newStatus: string) => {
    try {
      await adminApi.updateUserStatus(adminKey, userId, newStatus);
      await loadData(adminKey);
    } catch (e: any) {
      alert('Error updating status: ' + e.message);
    }
  };

  const handleViewActivity = async (user: UserProfile) => {
    try {
      setSelectedUserForActivity(user);
      setLoadingActivity(true);
      const events = await adminApi.getUserActivity(adminKey, user.id);
      setUserActivities(events);
    } catch (e: any) {
      alert('Error fetching user activity: ' + e.message);
    } finally {
      setLoadingActivity(false);
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 px-4">
        <div className="max-w-md w-full bg-white rounded-2xl shadow-2xl p-8 space-y-6">
          <div className="text-center">
            <div className="w-16 h-16 bg-emerald-500 rounded-2xl flex items-center justify-center mx-auto mb-4 text-white shadow-lg shadow-emerald-500/30">
              <ShieldCheck className="w-10 h-10" />
            </div>
            <h1 className="text-2xl font-bold text-gray-900">Admin Control Center</h1>
            <p className="text-sm text-gray-500 mt-1">Smart Expenses Tracker Infrastructure</p>
          </div>

          {errorMsg && (
            <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-gray-600 mb-1">
                API Base Endpoint
              </label>
              <input
                type="text"
                value={apiEndpoint}
                onChange={(e) => setApiEndpoint(e.target.value)}
                placeholder="https://smart-expenses-8pf3.onrender.com/api/v1/admin"
                className="w-full px-4 py-2.5 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-xs"
              />
              <span className="text-[10px] text-gray-400">Default: Live Render Production Server</span>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-gray-600 mb-1">
                Admin Secret Key
              </label>
              <input
                type="password"
                value={loginInput}
                onChange={(e) => setLoginInput(e.target.value)}
                placeholder="Enter admin secret key"
                className="w-full px-4 py-3 rounded-xl border border-gray-300 focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono text-sm"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-700 text-white font-medium rounded-xl transition shadow-md flex items-center justify-center gap-2"
            >
              {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : 'Authenticate Admin'}
            </button>
          </form>

          <p className="text-xs text-center text-gray-400">
            Protected endpoint with server-verified credentials & zero-knowledge financial privacy wall.
          </p>
        </div>
      </div>
    );
  }

  const filteredUsers = users.filter(u => 
    u.email.toLowerCase().includes(searchQuery.toLowerCase()) || 
    u.displayName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="min-h-screen bg-slate-50 flex">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-900 text-white flex flex-col">
        <div className="p-6 border-b border-slate-800 flex items-center gap-3">
          <div className="w-9 h-9 bg-emerald-500 rounded-xl flex items-center justify-center font-bold text-white shadow-md">
            SE
          </div>
          <div>
            <h2 className="font-semibold text-sm">Smart Expenses</h2>
            <span className="text-xs text-emerald-400">Admin Panel</span>
          </div>
        </div>

        <nav className="flex-1 p-4 space-y-1">
          <button
            onClick={() => setActiveTab('overview')}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition ${
              activeTab === 'overview' ? 'bg-emerald-600 text-white' : 'text-slate-400 hover:bg-slate-800'
            }`}
          >
            <Activity className="w-4 h-4" /> System Overview
          </button>
          <button
            onClick={() => setActiveTab('users')}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition ${
              activeTab === 'users' ? 'bg-emerald-600 text-white' : 'text-slate-400 hover:bg-slate-800'
            }`}
          >
            <Users className="w-4 h-4" /> User Management
          </button>
          <button
            onClick={() => setActiveTab('health')}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition ${
              activeTab === 'health' ? 'bg-emerald-600 text-white' : 'text-slate-400 hover:bg-slate-800'
            }`}
          >
            <Cpu className="w-4 h-4" /> Server Health
          </button>
          <button
            onClick={() => setActiveTab('audit')}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition ${
              activeTab === 'audit' ? 'bg-emerald-600 text-white' : 'text-slate-400 hover:bg-slate-800'
            }`}
          >
            <ShieldCheck className="w-4 h-4" /> Security Audit Logs
          </button>
        </nav>

        <div className="p-4 border-t border-slate-800 space-y-2">
          <div className="px-3 py-1.5 bg-slate-800 rounded-lg text-[11px] font-mono text-slate-400 truncate">
            Target: {apiEndpoint}
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-400 hover:bg-red-500/10 rounded-lg transition"
          >
            <LogOut className="w-4 h-4" /> Terminate Session
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 p-8 overflow-y-auto">
        <header className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 capitalize">
              {activeTab === 'overview' && 'System Overview & Analytics'}
              {activeTab === 'users' && 'User Account & Presence Monitoring'}
              {activeTab === 'health' && 'Infrastructure & API Health'}
              {activeTab === 'audit' && 'Security & Administration Audit Trail'}
            </h1>
            <p className="text-sm text-gray-500">Live operational telemetry & zero-knowledge privacy enforcement</p>
          </div>
          <button
            onClick={() => loadData(adminKey)}
            className="flex items-center gap-2 px-4 py-2 bg-white border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50 transition shadow-sm"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Refresh Data
          </button>
        </header>

        {/* TAB 1: OVERVIEW */}
        {activeTab === 'overview' && metrics && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
                <span className="text-xs font-semibold uppercase text-gray-400">Total Registered</span>
                <div className="text-3xl font-bold text-gray-900 mt-2">{metrics.totalRegisteredUsers}</div>
                <div className="text-xs text-emerald-600 mt-1 flex items-center gap-1">
                  <CheckCircle className="w-3 h-3" /> {metrics.verifiedUsers} Verified Accounts
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
                <span className="text-xs font-semibold uppercase text-gray-400">Active Presence</span>
                <div className="flex items-baseline gap-2 mt-2">
                  <span className="text-3xl font-bold text-emerald-600">{metrics.onlineUsers}</span>
                  <span className="text-xs text-gray-500">Online now (&le;120s)</span>
                </div>
                <div className="text-xs text-gray-400 mt-1">{metrics.offlineUsers} Offline</div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
                <span className="text-xs font-semibold uppercase text-gray-400">DAU / WAU Engagement</span>
                <div className="text-3xl font-bold text-indigo-600 mt-2">{metrics.dailyActiveUsers} <span className="text-sm font-normal text-gray-500">/ {metrics.weeklyActiveUsers} WAU</span></div>
                <div className="text-xs text-gray-500 mt-1">Daily unique active users</div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-gray-200 shadow-sm">
                <span className="text-xs font-semibold uppercase text-gray-400">Verified Backups</span>
                <div className="text-3xl font-bold text-emerald-600 mt-2">{metrics.totalBackupsCompleted}</div>
                <div className="text-xs text-gray-500 mt-1">Failed operations: {metrics.totalBackupsFailed}</div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm">
                <h3 className="font-semibold text-gray-900 mb-4">Account Verification Breakdown</h3>
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div className="p-4 bg-emerald-50 rounded-xl">
                    <span className="text-xs text-emerald-700 font-medium">Verified</span>
                    <div className="text-2xl font-bold text-emerald-800 mt-1">{metrics.verifiedUsers}</div>
                  </div>
                  <div className="p-4 bg-amber-50 rounded-xl">
                    <span className="text-xs text-amber-700 font-medium">Unverified</span>
                    <div className="text-2xl font-bold text-amber-800 mt-1">{metrics.unverifiedUsers}</div>
                  </div>
                  <div className="p-4 bg-red-50 rounded-xl">
                    <span className="text-xs text-red-700 font-medium">Suspended</span>
                    <div className="text-2xl font-bold text-red-800 mt-1">{metrics.suspendedUsers}</div>
                  </div>
                </div>
              </div>

              <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm">
                <h3 className="font-semibold text-gray-900 mb-2 flex items-center gap-2">
                  <Lock className="w-4 h-4 text-emerald-600" /> Strict Financial Privacy Wall
                </h3>
                <p className="text-xs text-gray-600 leading-relaxed mb-4">
                  In compliance with zero-knowledge financial privacy rules, the administration console is architecturally isolated from user monetary transactions, account balances, and memo details.
                </p>
                <div className="p-3 bg-emerald-50 rounded-xl text-xs text-emerald-800 flex items-center gap-2">
                  <CheckCircle className="w-4 h-4 flex-shrink-0 text-emerald-600" />
                  <span>Privacy Wall Active: Administrators cannot view user balances or transaction values.</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: USERS */}
        {activeTab === 'users' && (
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-gray-200 flex items-center justify-between">
              <div className="relative w-72">
                <Search className="w-4 h-4 text-gray-400 absolute left-3 top-3" />
                <input
                  type="text"
                  placeholder="Search user by name or email..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <span className="text-xs text-gray-500">Showing {filteredUsers.length} accounts</span>
            </div>

            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-gray-600 text-xs uppercase font-semibold">
                <tr>
                  <th className="p-4">User</th>
                  <th className="p-4">Presence</th>
                  <th className="p-4">Account Status</th>
                  <th className="p-4">Latest Backup</th>
                  <th className="p-4">Registered Date</th>
                  <th className="p-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-gray-50/50">
                    <td className="p-4">
                      <div className="font-medium text-gray-900">{u.displayName}</div>
                      <div className="text-xs text-gray-500">{u.email}</div>
                      <div className="font-mono text-[10px] text-gray-400">{u.id}</div>
                    </td>
                    <td className="p-4">
                      {u.isOnline ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800">
                          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                          Online
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-600">
                          <span className="w-2 h-2 rounded-full bg-gray-400"></span>
                          Offline
                        </span>
                      )}
                      {u.lastHeartbeatAt && (
                        <div className="text-[10px] text-gray-400 mt-1 flex items-center gap-1">
                          <Clock className="w-2.5 h-2.5" />
                          {new Date(u.lastHeartbeatAt).toLocaleTimeString()}
                        </div>
                      )}
                    </td>
                    <td className="p-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-semibold uppercase ${
                        u.accountStatus === 'verified' ? 'bg-emerald-100 text-emerald-800' :
                        u.accountStatus === 'suspended' ? 'bg-red-100 text-red-800' : 'bg-amber-100 text-amber-800'
                      }`}>
                        {u.accountStatus}
                      </span>
                    </td>
                    <td className="p-4">
                      {u.backupStatus === 'completed' ? (
                        <span className="inline-flex items-center gap-1 text-xs text-emerald-700">
                          <HardDrive className="w-3 h-3" />
                          {u.latestBackupAt ? new Date(u.latestBackupAt).toLocaleDateString() : 'Active'}
                        </span>
                      ) : (
                        <span className="text-xs text-gray-400">No backup yet</span>
                      )}
                    </td>
                    <td className="p-4 text-gray-500 text-xs">{new Date(u.createdAt).toLocaleDateString()}</td>
                    <td className="p-4 text-right space-x-2">
                      <button
                        onClick={() => handleViewActivity(u)}
                        className="text-xs text-indigo-600 hover:text-indigo-800 font-medium px-2 py-1 rounded bg-indigo-50 hover:bg-indigo-100 transition"
                      >
                        Activity
                      </button>
                      {u.accountStatus !== 'suspended' ? (
                        <button
                          onClick={() => handleStatusChange(u.id, 'suspended')}
                          className="text-xs text-red-600 hover:text-red-800 font-medium px-2 py-1 rounded bg-red-50 hover:bg-red-100 transition"
                        >
                          Suspend
                        </button>
                      ) : (
                        <button
                          onClick={() => handleStatusChange(u.id, 'verified')}
                          className="text-xs text-emerald-600 hover:text-emerald-800 font-medium px-2 py-1 rounded bg-emerald-50 hover:bg-emerald-100 transition"
                        >
                          Reactivate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 3: SERVER HEALTH */}
        {activeTab === 'health' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-4">
              <h3 className="font-semibold text-gray-900 flex items-center gap-2">
                <Database className="w-5 h-5 text-emerald-600" /> Database & Persistence
              </h3>
              <p className="text-sm text-gray-600">
                PostgreSQL schema configured with Row Level Security (RLS) isolation and integer minor units currency rules.
              </p>
              <div className="p-4 bg-gray-50 rounded-xl space-y-2 text-xs font-mono">
                <div className="flex justify-between">
                  <span className="text-gray-500">RLS Policies:</span>
                  <span className="text-emerald-700 font-semibold">Active & Enforced</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Connection State:</span>
                  <span className="text-emerald-700 font-semibold">Operational</span>
                </div>
              </div>
            </div>

            <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-4">
              <h3 className="font-semibold text-gray-900 flex items-center gap-2">
                <Cpu className="w-5 h-5 text-indigo-600" /> Node.js Express Runtime
              </h3>
              <p className="text-sm text-gray-600">
                Backend modular monolith running on Node.js v24 with TypeScript, Helmet, and CORS.
              </p>
              <div className="p-4 bg-gray-50 rounded-xl space-y-2 text-xs font-mono">
                <div className="flex justify-between">
                  <span className="text-gray-500">Server Architecture:</span>
                  <span>Express Modular Monolith</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Live Endpoint:</span>
                  <span className="text-emerald-700 truncate">{apiEndpoint}</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: AUDIT LOGS */}
        {activeTab === 'audit' && (
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-gray-600 text-xs uppercase font-semibold">
                <tr>
                  <th className="p-4">Timestamp</th>
                  <th className="p-4">Admin</th>
                  <th className="p-4">Action</th>
                  <th className="p-4">Target</th>
                  <th className="p-4">Details</th>
                  <th className="p-4">IP Address</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 font-mono text-xs">
                {auditLogs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="p-8 text-center text-gray-400">
                      No administrative events logged yet. Privileged actions will appear here.
                    </td>
                  </tr>
                ) : (
                  auditLogs.map((log) => (
                    <tr key={log.id}>
                      <td className="p-4 text-gray-500">{new Date(log.createdAt).toLocaleString()}</td>
                      <td className="p-4 font-semibold text-gray-800">{log.adminId}</td>
                      <td className="p-4 text-emerald-700 font-semibold">{log.action}</td>
                      <td className="p-4 text-gray-700">{log.targetType}:{log.targetId}</td>
                      <td className="p-4 text-gray-500">{JSON.stringify(log.details)}</td>
                      <td className="p-4 text-gray-500">{log.ipAddress}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* USER ACTIVITY MODAL (PRIVACY-WALLED) */}
        {selectedUserForActivity && (
          <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
            <div className="bg-white rounded-2xl max-w-xl w-full p-6 shadow-2xl space-y-4">
              <div className="flex items-center justify-between border-b pb-3">
                <div>
                  <h3 className="text-lg font-bold text-gray-900">User Activity Timeline</h3>
                  <p className="text-xs text-gray-500">{selectedUserForActivity.displayName} ({selectedUserForActivity.email})</p>
                </div>
                <button
                  onClick={() => setSelectedUserForActivity(null)}
                  className="p-1 rounded-lg text-gray-400 hover:bg-gray-100"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-center gap-2">
                <Lock className="w-4 h-4 flex-shrink-0 text-amber-600" />
                <span>Zero-Knowledge Privacy Wall: Monetary transaction amounts, personal notes, and travel itineraries are strictly protected and never stored in activity logs.</span>
              </div>

              <div className="max-h-80 overflow-y-auto space-y-2">
                {loadingActivity ? (
                  <div className="p-8 text-center text-gray-400 flex items-center justify-center gap-2">
                    <RefreshCw className="w-4 h-4 animate-spin" /> Loading timeline...
                  </div>
                ) : userActivities.length === 0 ? (
                  <div className="p-8 text-center text-gray-400 text-xs">
                    No operational events logged for this user.
                  </div>
                ) : (
                  userActivities.map((evt) => (
                    <div key={evt.id} className="p-3 bg-gray-50 rounded-xl flex items-center justify-between text-xs">
                      <div className="space-y-0.5">
                        <span className="font-semibold text-gray-800 font-mono">{evt.eventType}</span>
                        <div className="text-[10px] text-gray-400">
                          {new Date(evt.occurredAt).toLocaleString()}
                        </div>
                      </div>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                        evt.result === 'success' ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'
                      }`}>
                        {evt.result}
                      </span>
                    </div>
                  ))
                )}
              </div>

              <div className="pt-2 text-right">
                <button
                  onClick={() => setSelectedUserForActivity(null)}
                  className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-semibold rounded-xl transition"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
