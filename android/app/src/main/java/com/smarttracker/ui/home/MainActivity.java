package com.smarttracker.ui.home;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivityMainBinding;
import com.smarttracker.databinding.BottomSheetAddMenuBinding;
import com.smarttracker.databinding.BottomSheetAddTransactionBinding;
import com.smarttracker.ui.activity.ActivityFragment;
import com.smarttracker.ui.auth.LoginActivity;
import com.smarttracker.ui.insights.InsightsFragment;
import com.smarttracker.ui.money.MoneyFragment;
import com.smarttracker.ui.settings.SettingsActivity;
import com.smarttracker.ui.voice.VoiceAssistantBottomSheetDialog;
import com.smarttracker.ui.voice.VoiceCommandModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TransactionRepository repository;
    private SessionManager sessionManager;

    private HomeFragment homeFragment;
    private ActivityFragment activityFragment;
    private MoneyFragment moneyFragment;
    private InsightsFragment insightsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        repository = new TransactionRepository(getApplication());

        homeFragment = new HomeFragment();
        activityFragment = new ActivityFragment();
        moneyFragment = new MoneyFragment();
        insightsFragment = new InsightsFragment();

        homeFragment.setOnSettingsClickListener(() -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // Initialize with Home Fragment
        switchFragment(homeFragment);
        updateNavIcons(binding.ivNavHome, binding.tvNavHome);

        setupNavClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null && !sessionManager.isLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void setupNavClickListeners() {
        // Tab 1: Home
        binding.tabHome.setOnClickListener(v -> {
            switchFragment(homeFragment);
            updateNavIcons(binding.ivNavHome, binding.tvNavHome);
        });

        // Tab 2: Activity
        binding.tabActivity.setOnClickListener(v -> {
            switchFragment(activityFragment);
            updateNavIcons(binding.ivNavActivity, binding.tvNavActivity);
        });

        // Tab 3: Central Add Button with scale animation
        binding.fabAdd.setOnClickListener(v -> {
            try {
                binding.fabAdd.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in));
            } catch (Exception ignored) {}
            showAddMenuBottomSheet();
        });
        binding.tabAdd.setOnClickListener(v -> showAddMenuBottomSheet());

        // Tab 4: Money
        binding.tabMoney.setOnClickListener(v -> {
            switchFragment(moneyFragment);
            updateNavIcons(binding.ivNavMoney, binding.tvNavMoney);
        });

        // Tab 5: Insights
        binding.tabInsights.setOnClickListener(v -> {
            switchFragment(insightsFragment);
            updateNavIcons(binding.ivNavInsights, binding.tvNavInsights);
        });
    }

    private void switchFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    private void updateNavIcons(ImageView activeIcon, TextView activeText) {
        int primaryColor = ContextCompat.getColor(this, R.color.brand_primary);
        int secondaryColor = ContextCompat.getColor(this, R.color.text_secondary);

        ImageView[] icons = {binding.ivNavHome, binding.ivNavActivity, binding.ivNavMoney, binding.ivNavInsights};
        TextView[] texts = {binding.tvNavHome, binding.tvNavActivity, binding.tvNavMoney, binding.tvNavInsights};

        for (int i = 0; i < icons.length; i++) {
            icons[i].setImageTintList(ColorStateList.valueOf(secondaryColor));
            texts[i].setTextColor(secondaryColor);
        }

        activeIcon.setImageTintList(ColorStateList.valueOf(primaryColor));
        activeText.setTextColor(primaryColor);
    }

    private void showAddMenuBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        BottomSheetAddMenuBinding sheetBinding = BottomSheetAddMenuBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        sheetBinding.btnActionExpense.setOnClickListener(v -> {
            dialog.dismiss();
            showTransactionEntrySheet("expense", "Food / Dining");
        });

        sheetBinding.btnActionReceived.setOnClickListener(v -> {
            dialog.dismiss();
            showTransactionEntrySheet("received", "Salary / Gift");
        });

        sheetBinding.btnActionLend.setOnClickListener(v -> {
            dialog.dismiss();
            showTransactionEntrySheet("lend", "Lent to friend");
        });

        sheetBinding.btnActionBorrow.setOnClickListener(v -> {
            dialog.dismiss();
            showTransactionEntrySheet("borrow", "Borrowed from colleague");
        });

        sheetBinding.btnActionTransfer.setOnClickListener(v -> {
            dialog.dismiss();
            showTransactionEntrySheet("transfer", "Bank to Cash");
        });

        sheetBinding.btnActionVoice.setOnClickListener(v -> {
            dialog.dismiss();
            showVoiceAssistantDialog();
        });

        sheetBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showTransactionEntrySheet(String type, String defaultDescription) {
        BottomSheetDialog formDialog = new BottomSheetDialog(this);
        BottomSheetAddTransactionBinding formBinding = BottomSheetAddTransactionBinding.inflate(getLayoutInflater());
        formDialog.setContentView(formBinding.getRoot());

        formBinding.tvFormTitle.setText("Add " + type.toUpperCase());
        formBinding.etDescription.setText(defaultDescription);

        formBinding.btnVoiceInput.setOnClickListener(v -> {
            formDialog.dismiss();
            showVoiceAssistantDialog();
        });

        formBinding.btnFormCancel.setOnClickListener(v -> formDialog.dismiss());

        formBinding.btnFormSave.setOnClickListener(v -> {
            String amountStr = formBinding.etAmount.getText() != null ? formBinding.etAmount.getText().toString().trim() : "";
            long minorUnits = FinancialEngine.parseCurrencyToMinor(amountStr);

            if (minorUnits <= 0) {
                Toast.makeText(this, "Please enter a valid amount greater than ₹0", Toast.LENGTH_SHORT).show();
                return;
            }

            String desc = formBinding.etDescription.getText() != null ? formBinding.etDescription.getText().toString().trim() : defaultDescription;
            if (desc.isEmpty()) desc = defaultDescription;

            String accountId = "acc_upi";
            if (formBinding.rbAccCash.isChecked()) accountId = "acc_cash";
            if (formBinding.rbAccBank.isChecked()) accountId = "acc_bank";

            String dateStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());
            String userId = sessionManager.getUserId();

            TransactionEntity tx = new TransactionEntity(
                    UUID.randomUUID().toString(),
                    userId,
                    accountId,
                    type,
                    minorUnits,
                    "INR",
                    "cat_food",
                    desc,
                    dateStr,
                    "completed",
                    UUID.randomUUID().toString(),
                    null,
                    null,
                    false,
                    System.currentTimeMillis()
            );

            repository.insertTransaction(tx);
            formDialog.dismiss();
            Toast.makeText(this, type.toUpperCase() + " of " + FinancialEngine.formatMinorToSimpleINR(minorUnits) + " saved offline!", Toast.LENGTH_SHORT).show();
            if (homeFragment != null && homeFragment.isAdded()) {
                homeFragment.observeData();
            }
        });

        formDialog.show();
    }

    public void showVoiceAssistantDialog() {
        VoiceAssistantBottomSheetDialog voiceDialog = new VoiceAssistantBottomSheetDialog(this, repository);
        voiceDialog.setOnTransactionSavedListener(() -> {
            if (homeFragment != null && homeFragment.isAdded()) {
                homeFragment.observeData();
            }
        });
        voiceDialog.setOnEditDraftListener(this::showAddTransactionFromDraft);
        voiceDialog.show();
    }

    public void showAddTransactionFromDraft(VoiceCommandModel draft) {
        BottomSheetDialog formDialog = new BottomSheetDialog(this);
        BottomSheetAddTransactionBinding formBinding = BottomSheetAddTransactionBinding.inflate(getLayoutInflater());
        formDialog.setContentView(formBinding.getRoot());

        formBinding.tvFormTitle.setText("Add " + (draft.transactionType != null ? draft.transactionType.toUpperCase() : "EXPENSE"));
        if (draft.amountMinor > 0) {
            formBinding.etAmount.setText(String.valueOf(draft.amountMinor / 100));
        }
        formBinding.etDescription.setText(draft.description != null ? draft.description : "Voice Expense");

        if ("Cash".equalsIgnoreCase(draft.accountName)) {
            formBinding.rbAccCash.setChecked(true);
        } else if ("Bank".equalsIgnoreCase(draft.accountName)) {
            formBinding.rbAccBank.setChecked(true);
        } else {
            formBinding.rbAccUpi.setChecked(true);
        }

        formBinding.btnVoiceInput.setOnClickListener(v -> {
            formDialog.dismiss();
            showVoiceAssistantDialog();
        });

        formBinding.btnFormCancel.setOnClickListener(v -> formDialog.dismiss());
        formBinding.btnFormSave.setOnClickListener(v -> {
            String amountStr = formBinding.etAmount.getText() != null ? formBinding.etAmount.getText().toString().trim() : "";
            long minorUnits = FinancialEngine.parseCurrencyToMinor(amountStr);
            if (minorUnits <= 0) {
                Toast.makeText(this, "Please enter a valid amount greater than ₹0", Toast.LENGTH_SHORT).show();
                return;
            }

            String desc = formBinding.etDescription.getText() != null ? formBinding.etDescription.getText().toString().trim() : "Voice Add";
            String accountId = "acc_upi";
            if (formBinding.rbAccCash.isChecked()) accountId = "acc_cash";
            if (formBinding.rbAccBank.isChecked()) accountId = "acc_bank";

            String dateStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());
            String userId = sessionManager.getUserId();

            TransactionEntity tx = new TransactionEntity(
                    UUID.randomUUID().toString(),
                    userId,
                    accountId,
                    draft.transactionType != null ? draft.transactionType.toLowerCase() : "expense",
                    minorUnits,
                    "INR",
                    "cat_food",
                    desc,
                    dateStr,
                    "completed",
                    draft.idempotencyKey,
                    draft.recipient,
                    draft.destinationAccount,
                    false,
                    System.currentTimeMillis()
            );

            repository.insertTransaction(tx);
            formDialog.dismiss();
            Toast.makeText(this, "Transaction saved offline!", Toast.LENGTH_SHORT).show();
            if (homeFragment != null && homeFragment.isAdded()) {
                homeFragment.observeData();
            }
        });

        formDialog.show();
    }
}
