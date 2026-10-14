package com.smarttracker.ui.special;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivitySpecialExpensesBinding;
import com.smarttracker.ui.home.TransactionAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SpecialExpensesActivity extends AppCompatActivity {

    private ActivitySpecialExpensesBinding binding;
    private TransactionRepository repository;
    private SessionManager sessionManager;
    private TransactionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySpecialExpensesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        repository = new TransactionRepository(getApplication());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddSpecial.setOnClickListener(v -> showAddSpecialDialog());

        setupRecyclerView();
        observeSpecialExpenses();
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter(transaction -> {
            new AlertDialog.Builder(this)
                    .setTitle("Special Expense")
                    .setMessage(String.format("Description: %s\nAmount: %s\nImportant: %s\nDate: %s",
                            transaction.description,
                            FinancialEngine.formatMinorToSimpleINR(transaction.amountMinor),
                            transaction.isImportant ? "YES ⚡" : "No",
                            transaction.transactionDate))
                    .setNegativeButton("Delete", (dialog, which) -> repository.deleteTransaction(transaction.id))
                    .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                    .show();
        });
        binding.rvSpecialExpenses.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSpecialExpenses.setAdapter(adapter);
    }

    private void observeSpecialExpenses() {
        String userId = sessionManager.getUserId();
        repository.getSpecialExpensesLive(userId).observe(this, transactions -> {
            if (transactions != null) {
                adapter.setTransactions(transactions);

                long totalMinor = 0;
                int importantCount = 0;
                for (TransactionEntity tx : transactions) {
                    if ("expense".equalsIgnoreCase(tx.type) && "completed".equalsIgnoreCase(tx.status)) {
                        totalMinor += tx.amountMinor;
                        if (tx.isImportant) importantCount++;
                    }
                }

                binding.tvSpecialTotal.setText(FinancialEngine.formatMinorToSimpleINR(totalMinor));
                binding.tvSpecialCount.setText(String.format(Locale.getDefault(),
                        "%d special records (%d marked important) • Preserves single financial truth",
                        transactions.size(), importantCount));

                if (transactions.isEmpty()) {
                    binding.rvSpecialExpenses.setVisibility(View.GONE);
                    binding.tvEmptySpecial.setVisibility(View.VISIBLE);
                } else {
                    binding.rvSpecialExpenses.setVisibility(View.VISIBLE);
                    binding.tvEmptySpecial.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showAddSpecialDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_transaction, null);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        EditText etDesc = dialogView.findViewById(R.id.et_description);
        RadioButton rbCash = dialogView.findViewById(R.id.rb_acc_cash);
        RadioButton rbBank = dialogView.findViewById(R.id.rb_acc_bank);

        dialogView.findViewById(R.id.btn_voice_input).setVisibility(View.GONE);
        etDesc.setText("Medical / Emergency Expense");

        new AlertDialog.Builder(this)
                .setTitle("Add Special Expense")
                .setView(dialogView)
                .setPositiveButton("Save Special Expense", (dialog, which) -> {
                    String amountStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
                    long minorUnits = FinancialEngine.parseCurrencyToMinor(amountStr);

                    if (minorUnits <= 0) {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "Special Expense";
                    String accountId = "acc_upi";
                    if (rbCash != null && rbCash.isChecked()) accountId = "acc_cash";
                    if (rbBank != null && rbBank.isChecked()) accountId = "acc_bank";

                    String dateStr = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());

                    TransactionEntity tx = new TransactionEntity(
                            UUID.randomUUID().toString(),
                            sessionManager.getUserId(),
                            accountId,
                            "expense",
                            minorUnits,
                            "INR",
                            "cat_bills",
                            desc,
                            dateStr,
                            "completed",
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            false,
                            System.currentTimeMillis(),
                            "SPECIAL",
                            null,
                            true
                    );

                    repository.insertTransaction(tx);
                    Toast.makeText(this, "Special expense recorded!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
