package com.smarttracker.ui.travel;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.local.entity.TripEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivityTripDetailsBinding;
import com.smarttracker.ui.home.TransactionAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TripDetailsActivity extends AppCompatActivity {

    private ActivityTripDetailsBinding binding;
    private TransactionRepository repository;
    private SessionManager sessionManager;
    private TransactionAdapter adapter;
    private String tripId;
    private TripEntity currentTrip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tripId = getIntent().getStringExtra("EXTRA_TRIP_ID");
        if (tripId == null) {
            Toast.makeText(this, "Invalid trip ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sessionManager = SessionManager.getInstance(this);
        repository = new TransactionRepository(getApplication());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnTripOptions.setOnClickListener(v -> showTripOptionsMenu());
        binding.btnAddTripExpense.setOnClickListener(v -> showAddTripExpenseDialog());

        setupRecyclerView();
        observeTripData();
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter(transaction -> {
            new AlertDialog.Builder(this)
                    .setTitle("Trip Expense")
                    .setMessage(String.format("%s\nAmount: %s\nStatus: %s",
                            transaction.description,
                            FinancialEngine.formatMinorToSimpleINR(transaction.amountMinor),
                            transaction.status))
                    .setNegativeButton("Delete", (dialog, which) -> repository.deleteTransaction(transaction.id))
                    .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                    .show();
        });
        binding.rvTripTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTripTransactions.setAdapter(adapter);
    }

    private void observeTripData() {
        repository.getTripByIdLive(tripId).observe(this, trip -> {
            if (trip != null) {
                currentTrip = trip;
                binding.tvToolbarTitle.setText(trip.name);
                binding.tvDetailTripName.setText(trip.name);
                binding.tvDetailDestination.setText("📍 " + trip.destination + " (" + trip.status.toUpperCase() + ")");
                binding.tvDetailDates.setText("📅 " + trip.startDate + " – " + trip.endDate);
                binding.tvDetailDescription.setText(trip.description != null && !trip.description.isEmpty()
                        ? trip.description : "No description provided");
            }
        });

        String userId = sessionManager.getUserId();
        repository.getTransactionsByTripLive(userId, tripId).observe(this, transactions -> {
            if (transactions != null) {
                adapter.setTransactions(transactions);

                long totalSpentMinor = 0;
                for (TransactionEntity tx : transactions) {
                    if ("expense".equalsIgnoreCase(tx.type) && "completed".equalsIgnoreCase(tx.status)) {
                        totalSpentMinor += tx.amountMinor;
                    }
                }

                binding.tvDetailTotalSpent.setText(FinancialEngine.formatMinorToSimpleINR(totalSpentMinor));
                binding.tvDetailTxCount.setText(String.format(Locale.getDefault(), "%d linked expenses", transactions.size()));

                if (transactions.isEmpty()) {
                    binding.rvTripTransactions.setVisibility(View.GONE);
                    binding.tvEmptyTripTx.setVisibility(View.VISIBLE);
                } else {
                    binding.rvTripTransactions.setVisibility(View.VISIBLE);
                    binding.tvEmptyTripTx.setVisibility(View.GONE);
                }
            }
        });
    }

    private void showAddTripExpenseDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_transaction, null);
        EditText etAmount = dialogView.findViewById(R.id.et_amount);
        EditText etDesc = dialogView.findViewById(R.id.et_description);
        RadioButton rbCash = dialogView.findViewById(R.id.rb_acc_cash);
        RadioButton rbBank = dialogView.findViewById(R.id.rb_acc_bank);

        dialogView.findViewById(R.id.btn_voice_input).setVisibility(View.GONE);
        etDesc.setText(currentTrip != null ? currentTrip.name + " - Expense" : "Travel Expense");

        new AlertDialog.Builder(this)
                .setTitle("Add Trip Expense")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String amountStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
                    long minorUnits = FinancialEngine.parseCurrencyToMinor(amountStr);

                    if (minorUnits <= 0) {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "Travel Expense";
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
                            "cat_travel",
                            desc,
                            dateStr,
                            "completed",
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            false,
                            System.currentTimeMillis(),
                            "TRAVEL",
                            tripId,
                            false
                    );

                    repository.insertTransaction(tx);
                    Toast.makeText(this, "Travel expense recorded to trip!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showTripOptionsMenu() {
        if (currentTrip == null) return;
        String[] options = new String[]{"Mark as Completed", "Mark as Active", "Archive Trip"};

        new AlertDialog.Builder(this)
                .setTitle("Trip Actions")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        currentTrip.status = "completed";
                        currentTrip.updatedAt = System.currentTimeMillis();
                        repository.updateTrip(currentTrip);
                        Toast.makeText(this, "Trip marked as completed", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        currentTrip.status = "active";
                        currentTrip.updatedAt = System.currentTimeMillis();
                        repository.updateTrip(currentTrip);
                        Toast.makeText(this, "Trip marked as active", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        currentTrip.status = "archived";
                        currentTrip.updatedAt = System.currentTimeMillis();
                        repository.updateTrip(currentTrip);
                        Toast.makeText(this, "Trip archived safely", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
