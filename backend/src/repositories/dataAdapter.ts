import { store, Account, Transaction, Trip, Category, ActivityEvent, UserPresence, BackupRecord, Profile, MonitoringEvent, MonitoringEventStatusHistory, SavingGoal, UpcomingExpense } from './store';
import { supabaseAdmin, isSupabaseConfigured } from '../services/supabase';

/**
 * Unified Production Data Adapter:
 * Directly interfaces with Supabase PostgreSQL tables when configured,
 * while seamlessly maintaining local resilient in-memory state for offline reliability and unit tests.
 */
export class DataAdapter {

  // ===================== PROFILES =====================
  async getProfile(userId: string): Promise<Profile | null> {
    if (isSupabaseConfigured() && supabaseAdmin) {
      const { data, error } = await supabaseAdmin
        .from('profiles')
        .select('*')
        .eq('id', userId)
        .single();

      if (!error && data) {
        return {
          id: data.id,
          email: data.email || '',
          displayName: data.display_name,
          accountStatus: data.account_status,
          termsConsent: {
            termsVersion: data.terms_version || '2026.1',
            privacyVersion: '2026.1',
            consentedAt: data.created_at
          },
          lastLoginAt: data.last_login_at,
          createdAt: data.created_at
        };
      }
    }
    return store.profiles.get(userId) || null;
  }

  // ===================== ACCOUNTS =====================
  async getAccounts(userId: string): Promise<Account[]> {
    const memoryAccounts = Array.from(store.accounts.values()).filter(a => a.userId === userId);
    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        const { data, error } = await supabaseAdmin
          .from('accounts')
          .select('*')
          .eq('user_id', userId)
          .is('archived_at', null);

        if (!error && data && data.length > 0) {
          const map = new Map<string, Account>();
          memoryAccounts.forEach(a => map.set(a.id, a));
          data.forEach(d => {
            map.set(d.id, {
              id: d.id,
              userId: d.user_id,
              name: d.name,
              type: d.type,
              openingBalanceMinor: Number(d.opening_balance_minor),
              currencyCode: d.currency_code,
              createdAt: d.created_at
            });
          });
          return Array.from(map.values());
        }
      } catch (e) {
        // Fallback to memory
      }
    }
    return memoryAccounts;
  }

  async createAccount(acc: Account): Promise<Account> {
    store.accounts.set(acc.id, acc);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('accounts').insert({
          id: acc.id.startsWith('acc-') ? undefined : acc.id,
          user_id: acc.userId,
          name: acc.name,
          type: acc.type,
          opening_balance_minor: acc.openingBalanceMinor,
          currency_code: acc.currencyCode,
          created_at: acc.createdAt
        });
      } catch (e) {
        console.warn('Supabase account insert error (cached locally):', e);
      }
    }
    return acc;
  }

  // ===================== CATEGORIES =====================
  async getCategories(userId: string): Promise<Category[]> {
    if (isSupabaseConfigured() && supabaseAdmin) {
      const { data, error } = await supabaseAdmin
        .from('categories')
        .select('*')
        .or(`user_id.is.null,user_id.eq.${userId}`);

      if (!error && data) {
        return data.map(c => ({
          id: c.id,
          userId: c.user_id,
          name: c.name,
          type: c.type,
          iconKey: c.icon_key,
          colorHex: c.color_hex,
          createdAt: c.created_at
        }));
      }
    }
    return Array.from(store.categories.values()).filter(c => c.userId === userId || c.userId === null);
  }

  // ===================== TRANSACTIONS =====================
  async getTransactions(userId: string): Promise<Transaction[]> {
    const memoryTransactions = Array.from(store.transactions.values()).filter(t => t.userId === userId);
    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        const { data, error } = await supabaseAdmin
          .from('transactions')
          .select('*')
          .eq('user_id', userId)
          .is('deleted_at', null)
          .order('transaction_date', { ascending: false });

        if (!error && data && data.length > 0) {
          const map = new Map<string, Transaction>();
          memoryTransactions.forEach(t => map.set(t.id, t));
          data.forEach(t => {
            map.set(t.id, {
              id: t.id,
              userId: t.user_id,
              accountId: t.account_id,
              type: t.type,
              amountMinor: Number(t.amount_minor),
              currencyCode: t.currency_code,
              categoryId: t.category_id,
              description: t.description || '',
              transactionDate: t.transaction_date,
              status: t.status,
              idempotencyKey: t.idempotency_key,
              relatedPerson: t.related_person,
              destinationAccountId: t.destination_account_id,
              expenseGroup: t.expense_group,
              tripId: t.trip_id,
              isImportant: t.is_important,
              createdAt: t.created_at
            });
          });
          return Array.from(map.values());
        }
      } catch (e) {
        // Fallback
      }
    }
    return memoryTransactions;
  }

  async createTransaction(tx: Transaction): Promise<Transaction> {
    store.transactions.set(tx.id, tx);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

        // 1. Resolve account_id to valid Supabase UUID
        let resolvedAccountId = tx.accountId && isUuid.test(tx.accountId) ? tx.accountId : null;
        if (!resolvedAccountId && tx.accountId) {
          const accLower = tx.accountId.toLowerCase();
          const targetType = accLower.includes('cash') ? 'cash'
            : accLower.includes('bank') ? 'bank'
            : accLower.includes('savings') ? 'savings'
            : 'upi';

          const { data: matchedAcc } = await supabaseAdmin
            .from('accounts')
            .select('id')
            .eq('user_id', tx.userId)
            .eq('type', targetType)
            .limit(1)
            .maybeSingle();

          if (matchedAcc?.id) {
            resolvedAccountId = matchedAcc.id;
          } else {
            const { data: anyAcc } = await supabaseAdmin
              .from('accounts')
              .select('id')
              .eq('user_id', tx.userId)
              .limit(1)
              .maybeSingle();
            resolvedAccountId = anyAcc?.id || null;
          }
        }

        // 2. Resolve category_id to valid Supabase UUID
        let resolvedCategoryId = tx.categoryId && isUuid.test(tx.categoryId) ? tx.categoryId : null;
        if (!resolvedCategoryId && tx.categoryId) {
          const catLower = tx.categoryId.toLowerCase();
          const targetCat = catLower.includes('food') ? 'Food'
            : catLower.includes('travel') ? 'Travel'
            : catLower.includes('salary') ? 'Salary'
            : catLower.includes('shop') ? 'Shopping'
            : catLower.includes('bill') ? 'Bills'
            : 'Others';

          const { data: matchedCat } = await supabaseAdmin
            .from('categories')
            .select('id')
            .ilike('name', targetCat)
            .limit(1)
            .maybeSingle();

          if (matchedCat?.id) {
            resolvedCategoryId = matchedCat.id;
          }
        }

        const txUuid = isUuid.test(tx.id) ? tx.id : undefined;

        await supabaseAdmin.from('transactions').insert({
          id: txUuid,
          user_id: tx.userId,
          account_id: resolvedAccountId || tx.accountId,
          type: tx.type,
          amount_minor: tx.amountMinor,
          currency_code: tx.currencyCode || 'INR',
          category_id: resolvedCategoryId,
          description: tx.description,
          transaction_date: tx.transactionDate,
          status: tx.status || 'completed',
          idempotency_key: tx.idempotencyKey,
          related_person: tx.relatedPerson,
          destination_account_id: tx.destinationAccountId && isUuid.test(tx.destinationAccountId) ? tx.destinationAccountId : null,
          expense_group: tx.expenseGroup || 'GENERAL',
          trip_id: tx.tripId && isUuid.test(tx.tripId) ? tx.tripId : null,
          is_important: tx.isImportant || false,
          created_at: tx.createdAt
        });
      } catch (e) {
        console.warn('Supabase transaction insert error (cached locally):', e);
      }
    }
    return tx;
  }

  // ===================== TRIPS =====================
  async getTrips(userId: string): Promise<Trip[]> {
    const memoryTrips = Array.from(store.trips.values()).filter(tr => tr.userId === userId && tr.status !== 'archived');
    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        const { data, error } = await supabaseAdmin
          .from('trips')
          .select('*')
          .eq('user_id', userId)
          .neq('status', 'archived')
          .order('start_date', { ascending: false });

        if (!error && data && data.length > 0) {
          const map = new Map<string, Trip>();
          memoryTrips.forEach(tr => map.set(tr.id, tr));
          data.forEach(tr => {
            map.set(tr.id, {
              id: tr.id,
              userId: tr.user_id,
              name: tr.name,
              destination: tr.destination,
              startDate: tr.start_date,
              endDate: tr.end_date,
              description: tr.description || '',
              status: tr.status,
              createdAt: tr.created_at,
              updatedAt: tr.updated_at
            });
          });
          return Array.from(map.values());
        }
      } catch (e) {
        // Fallback to memory
      }
    }
    return memoryTrips;
  }

  async createCategory(cat: Category): Promise<Category> {
    store.categories.set(cat.id, cat);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('categories').insert({
          id: cat.id.startsWith('cat-') ? undefined : cat.id,
          user_id: cat.userId,
          name: cat.name,
          type: cat.type,
          icon_key: cat.iconKey,
          color_hex: cat.colorHex,
          created_at: cat.createdAt
        });
      } catch (e) {
        console.warn('Supabase category insert error (cached locally):', e);
      }
    }
    return cat;
  }

  async createTrip(trip: Trip): Promise<Trip> {
    store.trips.set(trip.id, trip);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('trips').insert({
          id: trip.id.startsWith('trip-') ? undefined : trip.id,
          user_id: trip.userId,
          name: trip.name,
          destination: trip.destination,
          start_date: trip.startDate,
          end_date: trip.endDate,
          description: trip.description,
          status: trip.status,
          created_at: trip.createdAt,
          updated_at: trip.updatedAt
        });
      } catch (e) {
        console.warn('Supabase trip insert error (cached locally):', e);
      }
    }
    return trip;
  }

  async updateTrip(trip: Trip): Promise<Trip> {
    store.trips.set(trip.id, trip);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('trips').update({
          name: trip.name,
          destination: trip.destination,
          start_date: trip.startDate,
          end_date: trip.endDate,
          description: trip.description,
          status: trip.status,
          updated_at: trip.updatedAt
        }).eq('id', trip.id);
      } catch (e) {
        console.warn('Supabase trip update error:', e);
      }
    }
    return trip;
  }

  async deleteTrip(tripId: string): Promise<void> {
    store.trips.delete(tripId);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('trips').delete().eq('id', tripId);
      } catch (e) {
        console.warn('Supabase trip delete error:', e);
      }
    }
  }

  // ===================== ACTIVITY & PRESENCE =====================
  async recordActivity(userId: string, eventType: string, result: 'success' | 'failure' | 'pending' = 'success', metadata?: Record<string, any>) {
    store.recordActivity(userId, eventType, result, metadata);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('activity_events').insert({
          user_id: userId,
          event_type: eventType,
          result,
          metadata: metadata || {}
        });
      } catch (e) {
        // Silent catch for activity events
      }
    }
  }

  async updatePresence(userId: string, platform: string = 'android') {
    store.updatePresence(userId, platform);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('user_presence').upsert({
          user_id: userId,
          last_seen_at: new Date().toISOString(),
          last_heartbeat_at: new Date().toISOString(),
          client_platform: platform,
          updated_at: new Date().toISOString()
        });
      } catch (e) {
        // Silent catch for heartbeat
      }
    }
  }

  // ===================== MONITORING EVENTS =====================
  async insertMonitoringEvents(events: MonitoringEvent[]): Promise<{ insertedCount: number; duplicatesIgnored: number; eventIds: string[] }> {
    let insertedCount = 0;
    let duplicatesIgnored = 0;
    const eventIds: string[] = [];

    const toInsertSupabase: any[] = [];

    for (const event of events) {
      if (store.monitoringEvents.has(event.id)) {
        duplicatesIgnored++;
        continue;
      }

      store.monitoringEvents.set(event.id, event);
      eventIds.push(event.id);
      insertedCount++;

      toInsertSupabase.push({
        id: event.id,
        event_type: event.eventType,
        severity: event.severity,
        source: event.source,
        error_code: event.errorCode,
        message: event.message,
        stack_trace: event.stackTrace || null,
        screen_name: event.screenName || null,
        operation_name: event.operationName || null,
        app_version: event.appVersion,
        app_build: event.appBuild,
        android_version: event.androidVersion || null,
        device_model: event.deviceModel || null,
        environment: event.environment,
        user_id: event.userId || null,
        installation_id: event.installationId,
        correlation_id: event.correlationId,
        occurred_at: event.occurredAt,
        received_at: event.receivedAt,
        status: event.status,
        occurrence_count: event.occurrenceCount || 1,
        metadata_json: event.metadataJson || {}
      });
    }

    if (toInsertSupabase.length > 0 && isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('monitoring_events').upsert(toInsertSupabase, { onConflict: 'id', ignoreDuplicates: true });
      } catch (e) {
        console.warn('Supabase monitoring_events insert error (cached in memory):', e);
      }
    }

    return { insertedCount, duplicatesIgnored, eventIds };
  }

  async getMonitoringEvents(filter: {
    severity?: string;
    source?: string;
    status?: string;
    search?: string;
    dateFrom?: string;
    dateTo?: string;
    limit?: number;
    offset?: number;
  }): Promise<{ events: MonitoringEvent[]; total: number }> {
    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        let query = supabaseAdmin
          .from('monitoring_events')
          .select('*', { count: 'exact' });

        if (filter.severity) query = query.eq('severity', filter.severity);
        if (filter.source) query = query.eq('source', filter.source);
        if (filter.status) query = query.eq('status', filter.status);
        if (filter.dateFrom) query = query.gte('occurred_at', filter.dateFrom);
        if (filter.dateTo) query = query.lte('occurred_at', filter.dateTo);
        if (filter.search) {
          query = query.or(`message.ilike.%${filter.search}%,error_code.ilike.%${filter.search}%,screen_name.ilike.%${filter.search}%`);
        }

        const offset = filter.offset || 0;
        const limit = Math.min(filter.limit || 50, 100);
        query = query.order('occurred_at', { ascending: false }).range(offset, offset + limit - 1);

        const { data, count, error } = await query;
        if (!error && data) {
          const events: MonitoringEvent[] = data.map(d => ({
            id: d.id,
            eventType: d.event_type,
            severity: d.severity,
            source: d.source,
            errorCode: d.error_code,
            message: d.message,
            stackTrace: d.stack_trace,
            screenName: d.screen_name,
            operationName: d.operation_name,
            appVersion: d.app_version,
            appBuild: d.app_build,
            androidVersion: d.android_version,
            deviceModel: d.device_model,
            environment: d.environment,
            userId: d.user_id,
            installationId: d.installation_id,
            correlationId: d.correlation_id,
            occurredAt: d.occurred_at,
            receivedAt: d.received_at,
            status: d.status,
            occurrenceCount: d.occurrence_count,
            metadataJson: d.metadata_json || {}
          }));
          return { events, total: count || events.length };
        }
      } catch (e) {
        console.warn('Supabase monitoring_events query error, falling back to memory:', e);
      }
    }

    // Fallback to in-memory store
    let events = Array.from(store.monitoringEvents.values());
    if (filter.severity) events = events.filter(e => e.severity === filter.severity);
    if (filter.source) events = events.filter(e => e.source === filter.source);
    if (filter.status) events = events.filter(e => e.status === filter.status);
    if (filter.dateFrom) events = events.filter(e => new Date(e.occurredAt) >= new Date(filter.dateFrom!));
    if (filter.dateTo) events = events.filter(e => new Date(e.occurredAt) <= new Date(filter.dateTo!));
    if (filter.search) {
      const q = filter.search.toLowerCase();
      events = events.filter(e => e.message.toLowerCase().includes(q) || e.errorCode.toLowerCase().includes(q) || (e.screenName && e.screenName.toLowerCase().includes(q)));
    }

    events.sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime());

    const total = events.length;
    const offset = filter.offset || 0;
    const limit = Math.min(filter.limit || 50, 100);
    const paginated = events.slice(offset, offset + limit);

    return { events: paginated, total };
  }

  async getMonitoringEventById(id: string): Promise<{ event: MonitoringEvent; history: MonitoringEventStatusHistory[] } | null> {
    const memoryEvent = store.monitoringEvents.get(id);
    const history = store.monitoringEventHistory.filter(h => h.eventId === id);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        const { data, error } = await supabaseAdmin.from('monitoring_events').select('*').eq('id', id).single();
        if (!error && data) {
          const event: MonitoringEvent = {
            id: data.id,
            eventType: data.event_type,
            severity: data.severity,
            source: data.source,
            errorCode: data.error_code,
            message: data.message,
            stackTrace: data.stack_trace,
            screenName: data.screen_name,
            operationName: data.operation_name,
            appVersion: data.app_version,
            appBuild: data.app_build,
            androidVersion: data.android_version,
            deviceModel: data.device_model,
            environment: data.environment,
            userId: data.user_id,
            installationId: data.installation_id,
            correlationId: data.correlation_id,
            occurredAt: data.occurred_at,
            receivedAt: data.received_at,
            status: data.status,
            occurrenceCount: data.occurrence_count,
            metadataJson: data.metadata_json || {}
          };

          const { data: histData } = await supabaseAdmin.from('monitoring_event_status_history').select('*').eq('event_id', id).order('changed_at', { ascending: true });
          const dbHistory: MonitoringEventStatusHistory[] = (histData || []).map(h => ({
            id: h.id,
            eventId: h.event_id,
            oldStatus: h.old_status,
            newStatus: h.new_status,
            changedBy: h.changed_by,
            reason: h.reason,
            changedAt: h.changed_at
          }));

          return { event, history: dbHistory.length > 0 ? dbHistory : history };
        }
      } catch (e) {
        // Fallback
      }
    }

    if (!memoryEvent) return null;
    return { event: memoryEvent, history };
  }

  async updateMonitoringEventStatus(id: string, newStatus: 'NEW' | 'ACKNOWLEDGED' | 'RESOLVED' | 'IGNORED', changedBy: string, reason?: string): Promise<boolean> {
    const event = store.monitoringEvents.get(id);
    const oldStatus = event ? event.status : 'NEW';

    if (event) {
      event.status = newStatus;
      store.monitoringEvents.set(id, event);
    }

    const histEntry: MonitoringEventStatusHistory = {
      id: `msh-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
      eventId: id,
      oldStatus,
      newStatus,
      changedBy,
      reason,
      changedAt: new Date().toISOString()
    };
    store.monitoringEventHistory.push(histEntry);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('monitoring_events').update({ status: newStatus }).eq('id', id);
        await supabaseAdmin.from('monitoring_event_status_history').insert({
          event_id: id,
          old_status: oldStatus,
          new_status: newStatus,
          changed_by: changedBy,
          reason: reason || null,
          changed_at: histEntry.changedAt
        });
      } catch (e) {
        console.warn('Supabase monitoring status update error:', e);
      }
    }

    return true;
  }

  async getMonitoringSummary(): Promise<{
    totalEvents: number;
    newEvents: number;
    criticalEvents: number;
    errorEvents: number;
    warningEvents: number;
    resolvedEvents: number;
    eventsToday: number;
    syncFailuresCount: number;
    lastIngestedAt: string | null;
  }> {
    const events = Array.from(store.monitoringEvents.values());
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();

    const totalEvents = events.length;
    const newEvents = events.filter(e => e.status === 'NEW').length;
    const criticalEvents = events.filter(e => e.severity === 'CRITICAL').length;
    const errorEvents = events.filter(e => e.severity === 'ERROR').length;
    const warningEvents = events.filter(e => e.severity === 'WARNING').length;
    const resolvedEvents = events.filter(e => e.status === 'RESOLVED').length;
    const eventsToday = events.filter(e => new Date(e.occurredAt).getTime() >= todayStart).length;
    const syncFailuresCount = events.filter(e => e.source === 'SYNC' && (e.severity === 'ERROR' || e.severity === 'CRITICAL')).length;
    
    let lastIngestedAt: string | null = null;
    if (events.length > 0) {
      const sorted = [...events].sort((a, b) => new Date(b.receivedAt).getTime() - new Date(a.receivedAt).getTime());
      lastIngestedAt = sorted[0].receivedAt;
    }

    return {
      totalEvents,
      newEvents,
      criticalEvents,
      errorEvents,
      warningEvents,
      resolvedEvents,
      eventsToday,
      syncFailuresCount,
      lastIngestedAt
    };
  }

  // ===================== MULTI-ENTITY SYNC =====================
  async pushMultiEntity(userId: string, payload: {
    accounts?: any[];
    categories?: any[];
    transactions?: any[];
    trips?: any[];
    savingGoals?: any[];
    upcomingExpenses?: any[];
  }): Promise<{
    syncedCounts: Record<string, number>;
    rejectedCounts: Record<string, number>;
  }> {
    const syncedCounts: Record<string, number> = {
      accounts: 0,
      categories: 0,
      transactions: 0,
      trips: 0,
      savingGoals: 0,
      upcomingExpenses: 0
    };
    const rejectedCounts: Record<string, number> = {
      accounts: 0,
      categories: 0,
      transactions: 0,
      trips: 0,
      savingGoals: 0,
      upcomingExpenses: 0
    };

    // 1. Accounts
    if (Array.isArray(payload.accounts)) {
      for (const a of payload.accounts) {
        try {
          if (!a.id || !a.name || !a.type) throw new Error('Invalid account');
          await this.createAccount({
            id: a.id,
            userId,
            name: a.name,
            type: a.type,
            openingBalanceMinor: Number(a.openingBalanceMinor || 0),
            currencyCode: a.currencyCode || 'INR',
            createdAt: a.createdAt || new Date().toISOString()
          });
          syncedCounts.accounts++;
        } catch {
          rejectedCounts.accounts++;
        }
      }
    }

    // 2. Categories
    if (Array.isArray(payload.categories)) {
      for (const c of payload.categories) {
        try {
          if (!c.id || !c.name || !c.type) throw new Error('Invalid category');
          await this.createCategory({
            id: c.id,
            userId,
            name: c.name,
            type: c.type,
            iconKey: c.iconKey || 'ic_other',
            colorHex: c.colorHex || '#10B981',
            createdAt: c.createdAt || new Date().toISOString()
          });
          syncedCounts.categories++;
        } catch {
          rejectedCounts.categories++;
        }
      }
    }

    // 3. Trips
    if (Array.isArray(payload.trips)) {
      for (const tr of payload.trips) {
        try {
          if (!tr.id || !tr.name || !tr.destination || !tr.startDate || !tr.endDate) throw new Error('Invalid trip');
          await this.createTrip({
            id: tr.id,
            userId,
            name: tr.name,
            destination: tr.destination,
            startDate: tr.startDate,
            endDate: tr.endDate,
            description: tr.description || '',
            status: tr.status || 'active',
            createdAt: tr.createdAt || new Date().toISOString(),
            updatedAt: tr.updatedAt || new Date().toISOString()
          });
          syncedCounts.trips++;
        } catch {
          rejectedCounts.trips++;
        }
      }
    }

    // 4. Transactions
    if (Array.isArray(payload.transactions)) {
      for (const tx of payload.transactions) {
        try {
          if (!tx.id || !tx.type || tx.amountMinor === undefined) throw new Error('Invalid transaction');
          // Idempotency check
          if (tx.idempotencyKey) {
            const existing = Array.from(store.transactions.values()).find(t => t.idempotencyKey === tx.idempotencyKey);
            if (existing) {
              syncedCounts.transactions++;
              continue;
            }
          }
          await this.createTransaction({
            id: tx.id,
            userId,
            accountId: tx.accountId,
            type: tx.type,
            amountMinor: Number(tx.amountMinor),
            currencyCode: tx.currencyCode || 'INR',
            categoryId: tx.categoryId || null,
            description: tx.description || '',
            transactionDate: tx.transactionDate || new Date().toISOString(),
            status: tx.status || 'completed',
            idempotencyKey: tx.idempotencyKey,
            relatedPerson: tx.relatedPerson,
            destinationAccountId: tx.destinationAccountId,
            expenseGroup: tx.expenseGroup || 'GENERAL',
            tripId: tx.tripId || null,
            isImportant: tx.isImportant || false,
            createdAt: tx.createdAt || new Date().toISOString()
          });
          syncedCounts.transactions++;
        } catch {
          rejectedCounts.transactions++;
        }
      }
    }

    // 5. Saving Goals
    if (Array.isArray(payload.savingGoals)) {
      for (const sg of payload.savingGoals) {
        try {
          if (!sg.id || !sg.title) throw new Error('Invalid saving goal');
          store.savingGoals.set(sg.id, {
            id: sg.id,
            userId,
            title: sg.title,
            targetAmountMinor: Number(sg.targetAmountMinor || 0),
            currentAmountMinor: Number(sg.currentAmountMinor || 0),
            targetDate: sg.targetDate || '',
            createdAt: sg.createdAt || new Date().toISOString()
          });
          if (isSupabaseConfigured() && supabaseAdmin) {
            try {
              await supabaseAdmin.from('saving_goals').upsert({
                id: sg.id,
                user_id: userId,
                name: sg.title,
                target_amount_minor: Number(sg.targetAmountMinor || 0),
                saved_amount_minor: Number(sg.currentAmountMinor || 0),
                target_date: sg.targetDate || null
              });
            } catch (e) {
              // Ignore Supabase cache warning
            }
          }
          syncedCounts.savingGoals++;
        } catch {
          rejectedCounts.savingGoals++;
        }
      }
    }

    // 6. Upcoming Expenses
    if (Array.isArray(payload.upcomingExpenses)) {
      for (const ue of payload.upcomingExpenses) {
        try {
          if (!ue.id || !ue.title) throw new Error('Invalid upcoming expense');
          store.upcomingExpenses.set(ue.id, {
            id: ue.id,
            userId,
            title: ue.title,
            expectedAmountMinor: Number(ue.expectedAmountMinor || 0),
            dueDate: ue.dueDate || '',
            reminderEnabled: ue.reminderEnabled || false,
            status: ue.status || 'pending',
            createdAt: ue.createdAt || new Date().toISOString()
          });
          if (isSupabaseConfigured() && supabaseAdmin) {
            try {
              await supabaseAdmin.from('upcoming_expenses').upsert({
                id: ue.id,
                user_id: userId,
                title: ue.title,
                estimated_amount_minor: Number(ue.expectedAmountMinor || 0),
                due_date: ue.dueDate || new Date().toISOString().substring(0, 10),
                is_paid: ue.status === 'paid'
              });
            } catch (e) {
              // Ignore
            }
          }
          syncedCounts.upcomingExpenses++;
        } catch {
          rejectedCounts.upcomingExpenses++;
        }
      }
    }

    return { syncedCounts, rejectedCounts };
  }

  async pullMultiEntity(userId: string, since?: string): Promise<{
    accounts: Account[];
    categories: Category[];
    transactions: Transaction[];
    trips: Trip[];
    savingGoals: SavingGoal[];
    upcomingExpenses: UpcomingExpense[];
    serverTime: string;
  }> {
    const sinceTimestamp = since ? new Date(since).getTime() : 0;

    const accounts = (await this.getAccounts(userId)).filter(a => sinceTimestamp === 0 || new Date(a.createdAt).getTime() > sinceTimestamp);
    const categories = (await this.getCategories(userId)).filter(c => sinceTimestamp === 0 || new Date(c.createdAt).getTime() > sinceTimestamp);
    const transactions = (await this.getTransactions(userId)).filter(t => sinceTimestamp === 0 || new Date(t.createdAt).getTime() > sinceTimestamp);
    const trips = (await this.getTrips(userId)).filter(tr => sinceTimestamp === 0 || new Date(tr.createdAt).getTime() > sinceTimestamp);

    const savingGoals = Array.from(store.savingGoals.values()).filter(sg => sg.userId === userId && (sinceTimestamp === 0 || new Date(sg.createdAt).getTime() > sinceTimestamp));
    const upcomingExpenses = Array.from(store.upcomingExpenses.values()).filter(ue => ue.userId === userId && (sinceTimestamp === 0 || new Date(ue.createdAt).getTime() > sinceTimestamp));

    return {
      accounts,
      categories,
      transactions,
      trips,
      savingGoals,
      upcomingExpenses,
      serverTime: new Date().toISOString()
    };
  }
}

export const dataAdapter = new DataAdapter();

