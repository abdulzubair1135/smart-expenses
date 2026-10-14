import { store, Account, Transaction, Trip, Category, ActivityEvent, UserPresence, BackupRecord, Profile } from './store';
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
    if (isSupabaseConfigured() && supabaseAdmin) {
      const { data, error } = await supabaseAdmin
        .from('accounts')
        .select('*')
        .eq('user_id', userId)
        .is('archived_at', null);

      if (!error && data) {
        return data.map(d => ({
          id: d.id,
          userId: d.user_id,
          name: d.name,
          type: d.type,
          openingBalanceMinor: Number(d.opening_balance_minor),
          currencyCode: d.currency_code,
          createdAt: d.created_at
        }));
      }
    }
    return Array.from(store.accounts.values()).filter(a => a.userId === userId);
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
    if (isSupabaseConfigured() && supabaseAdmin) {
      const { data, error } = await supabaseAdmin
        .from('transactions')
        .select('*')
        .eq('user_id', userId)
        .is('deleted_at', null)
        .order('transaction_date', { ascending: false });

      if (!error && data) {
        return data.map(t => ({
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
        }));
      }
    }
    return Array.from(store.transactions.values()).filter(t => t.userId === userId);
  }

  async createTransaction(tx: Transaction): Promise<Transaction> {
    store.transactions.set(tx.id, tx);

    if (isSupabaseConfigured() && supabaseAdmin) {
      try {
        await supabaseAdmin.from('transactions').insert({
          id: tx.id.startsWith('tx-') ? undefined : tx.id,
          user_id: tx.userId,
          account_id: tx.accountId,
          type: tx.type,
          amount_minor: tx.amountMinor,
          currency_code: tx.currencyCode,
          category_id: tx.categoryId,
          description: tx.description,
          transaction_date: tx.transactionDate,
          status: tx.status,
          idempotency_key: tx.idempotencyKey,
          related_person: tx.relatedPerson,
          destination_account_id: tx.destinationAccountId,
          expense_group: tx.expenseGroup || 'GENERAL',
          trip_id: tx.tripId,
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
    if (isSupabaseConfigured() && supabaseAdmin) {
      const { data, error } = await supabaseAdmin
        .from('trips')
        .select('*')
        .eq('user_id', userId)
        .neq('status', 'archived')
        .order('start_date', { ascending: false });

      if (!error && data) {
        return data.map(tr => ({
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
        }));
      }
    }
    return Array.from(store.trips.values()).filter(tr => tr.userId === userId && tr.status !== 'archived');
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
}

export const dataAdapter = new DataAdapter();
