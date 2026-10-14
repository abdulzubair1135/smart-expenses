package com.smarttracker.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.smarttracker.data.local.dao.AccountDao;
import com.smarttracker.data.local.dao.CategoryDao;
import com.smarttracker.data.local.dao.SavingGoalDao;
import com.smarttracker.data.local.dao.TransactionDao;
import com.smarttracker.data.local.dao.TripDao;
import com.smarttracker.data.local.dao.UpcomingExpenseDao;
import com.smarttracker.data.local.entity.AccountEntity;
import com.smarttracker.data.local.entity.CategoryEntity;
import com.smarttracker.data.local.entity.SavingGoalEntity;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.local.entity.TripEntity;
import com.smarttracker.data.local.entity.UpcomingExpenseEntity;
import androidx.room.migration.Migration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {
        TransactionEntity.class,
        AccountEntity.class,
        CategoryEntity.class,
        SavingGoalEntity.class,
        UpcomingExpenseEntity.class,
        TripEntity.class
}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();
    public abstract AccountDao accountDao();
    public abstract CategoryDao categoryDao();
    public abstract SavingGoalDao savingGoalDao();
    public abstract UpcomingExpenseDao upcomingExpenseDao();
    public abstract TripDao tripDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN expenseGroup TEXT DEFAULT 'GENERAL'");
            database.execSQL("ALTER TABLE transactions ADD COLUMN tripId TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN isImportant INTEGER NOT NULL DEFAULT 0");

            database.execSQL("CREATE TABLE IF NOT EXISTS trips (" +
                    "id TEXT PRIMARY KEY NOT NULL, " +
                    "userId TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "destination TEXT NOT NULL, " +
                    "startDate TEXT NOT NULL, " +
                    "endDate TEXT NOT NULL, " +
                    "description TEXT, " +
                    "status TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL DEFAULT 0)");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "smart_expenses.db")
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            databaseWriteExecutor.execute(() -> {
                // Populate initial categories and demo accounts matching UI Reference
                CategoryDao catDao = INSTANCE.categoryDao();
                List<CategoryEntity> defaultCats = new ArrayList<>();
                defaultCats.add(new CategoryEntity("cat_food", null, "Food", "expense", "ic_food", "#EF4444"));
                defaultCats.add(new CategoryEntity("cat_travel", null, "Travel", "expense", "ic_travel", "#F59E0B"));
                defaultCats.add(new CategoryEntity("cat_shopping", null, "Shopping", "expense", "ic_shopping", "#3B82F6"));
                defaultCats.add(new CategoryEntity("cat_bills", null, "Bills", "expense", "ic_bills", "#8B5CF6"));
                defaultCats.add(new CategoryEntity("cat_other", null, "Others", "expense", "ic_other", "#6B7280"));
                defaultCats.add(new CategoryEntity("cat_salary", null, "Salary", "income", "ic_salary", "#10B981"));
                catDao.insertAll(defaultCats);

                // Default accounts for Demo User
                String demoUserId = "d0000000-0000-0000-0000-000000000001";
                AccountDao accDao = INSTANCE.accountDao();
                List<AccountEntity> accounts = new ArrayList<>();
                // Total: ₹10,500 (Cash ₹2,000 + Bank ₹8,000 + UPI ₹1,500 + Savings ₹5,000 = ₹16,500 with initial values)
                accounts.add(new AccountEntity("acc_cash", demoUserId, "Cash", "cash", 200000, "INR", System.currentTimeMillis()));
                accounts.add(new AccountEntity("acc_bank", demoUserId, "Bank", "bank", 800000, "INR", System.currentTimeMillis()));
                accounts.add(new AccountEntity("acc_upi", demoUserId, "UPI", "upi", 150000, "INR", System.currentTimeMillis()));
                accounts.add(new AccountEntity("acc_savings", demoUserId, "Savings", "savings", 500000, "INR", System.currentTimeMillis()));
                accDao.insertAll(accounts);

                // Initial Goal
                SavingGoalDao goalDao = INSTANCE.savingGoalDao();
                goalDao.insert(new SavingGoalEntity("goal_1", demoUserId, "New Phone", 3000000, 1200000, "2026-12-31"));

                // Initial Upcoming
                UpcomingExpenseDao upDao = INSTANCE.upcomingExpenseDao();
                upDao.insert(new UpcomingExpenseEntity("up_1", demoUserId, "Electricity Bill", 120000, "5 Oct", true, "pending"));

                // Initial Demo Transactions (Screen 3 & 12: Food ₹250, Received ₹500, Lent ₹1000, Transfer ₹2000)
                TransactionDao txDao = INSTANCE.transactionDao();
                txDao.insert(new TransactionEntity("tx_1", demoUserId, "acc_upi", "expense", 25000, "INR", "cat_food", "Food - Expense", "2026-10-13T12:30:00Z", "completed", null, null, null, true, System.currentTimeMillis()));
                txDao.insert(new TransactionEntity("tx_2", demoUserId, "acc_cash", "received", 50000, "INR", null, "Rahul - Received", "2026-10-13T11:45:00Z", "completed", null, "Rahul", null, true, System.currentTimeMillis()));
            });
        }
    };
}
