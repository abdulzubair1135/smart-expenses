package com.smarttracker.ui.travel;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.local.entity.TripEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.ActivityTravelTripsBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TravelTripsActivity extends AppCompatActivity {

    private ActivityTravelTripsBinding binding;
    private TransactionRepository repository;
    private SessionManager sessionManager;
    private TripAdapter adapter;

    private List<TripEntity> allTrips = new ArrayList<>();
    private List<TransactionEntity> allTransactions = new ArrayList<>();
    private String currentStatusFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTravelTripsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        repository = new TransactionRepository(getApplication());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCreateTrip.setOnClickListener(v -> showCreateTripDialog());

        setupRecyclerView();
        setupFilterTabs();
        observeData();
    }

    private void setupRecyclerView() {
        adapter = new TripAdapter(trip -> {
            Intent intent = new Intent(TravelTripsActivity.this, TripDetailsActivity.class);
            intent.putExtra("EXTRA_TRIP_ID", trip.id);
            startActivity(intent);
        });
        binding.rvTrips.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTrips.setAdapter(adapter);
    }

    private void setupFilterTabs() {
        binding.btnFilterAllTrips.setOnClickListener(v -> setFilter("ALL", binding.btnFilterAllTrips));
        binding.btnFilterActiveTrips.setOnClickListener(v -> setFilter("active", binding.btnFilterActiveTrips));
        binding.btnFilterCompletedTrips.setOnClickListener(v -> setFilter("completed", binding.btnFilterCompletedTrips));
    }

    private void setFilter(String status, Button selectedBtn) {
        currentStatusFilter = status;
        resetFilterButtons();
        selectedBtn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_primary)));
        selectedBtn.setTextColor(Color.WHITE);
        applyFilter();
    }

    private void resetFilterButtons() {
        int normalText = ContextCompat.getColor(this, R.color.text_secondary);
        binding.btnFilterAllTrips.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
        binding.btnFilterAllTrips.setTextColor(normalText);
        binding.btnFilterActiveTrips.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
        binding.btnFilterActiveTrips.setTextColor(normalText);
        binding.btnFilterCompletedTrips.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F1F5F9")));
        binding.btnFilterCompletedTrips.setTextColor(normalText);
    }

    private void observeData() {
        String userId = sessionManager.getUserId();

        repository.getTripsByUserLive(userId).observe(this, trips -> {
            if (trips != null) {
                allTrips = trips;
                applyFilter();
            }
        });

        repository.getAllTransactionsLive(userId).observe(this, transactions -> {
            if (transactions != null) {
                allTransactions = transactions;
                computeTotalTravelSpending();
                applyFilter();
            }
        });
    }

    private void computeTotalTravelSpending() {
        long totalTravelMinor = 0;
        int travelTxCount = 0;

        for (TransactionEntity tx : allTransactions) {
            if ("expense".equalsIgnoreCase(tx.type) && "completed".equalsIgnoreCase(tx.status)) {
                if ("TRAVEL".equalsIgnoreCase(tx.expenseGroup) || tx.tripId != null) {
                    totalTravelMinor += tx.amountMinor;
                    travelTxCount++;
                }
            }
        }

        binding.tvTotalTravelSpent.setText(FinancialEngine.formatMinorToSimpleINR(totalTravelMinor));
        binding.tvTravelMeta.setText(String.format(Locale.getDefault(),
                "%d trips • %d travel transactions recorded", allTrips.size(), travelTxCount));
    }

    private void applyFilter() {
        List<TripEntity> filtered = new ArrayList<>();
        for (TripEntity trip : allTrips) {
            if ("ALL".equalsIgnoreCase(currentStatusFilter) || trip.status.equalsIgnoreCase(currentStatusFilter)) {
                filtered.add(trip);
            }
        }

        adapter.setTrips(filtered, allTransactions);

        if (filtered.isEmpty()) {
            binding.rvTrips.setVisibility(View.GONE);
            binding.tvEmptyTrips.setVisibility(View.VISIBLE);
        } else {
            binding.rvTrips.setVisibility(View.VISIBLE);
            binding.tvEmptyTrips.setVisibility(View.GONE);
        }
    }

    private void showCreateTripDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_trip, null);
        EditText etName = dialogView.findViewById(R.id.et_trip_name);
        EditText etDestination = dialogView.findViewById(R.id.et_trip_destination);
        EditText etStartDate = dialogView.findViewById(R.id.et_start_date);
        EditText etEndDate = dialogView.findViewById(R.id.et_end_date);
        EditText etDescription = dialogView.findViewById(R.id.et_trip_description);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar now = Calendar.getInstance();
        etStartDate.setText(sdf.format(now.getTime()));
        now.add(Calendar.DAY_OF_MONTH, 3);
        etEndDate.setText(sdf.format(now.getTime()));

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Create Trip", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String dest = etDestination.getText().toString().trim();
                    String start = etStartDate.getText().toString().trim();
                    String end = etEndDate.getText().toString().trim();
                    String desc = etDescription.getText().toString().trim();

                    if (name.isEmpty() || dest.isEmpty()) {
                        Toast.makeText(this, "Trip name and destination are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (start.compareTo(end) > 0) {
                        Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long timeNow = System.currentTimeMillis();
                    TripEntity newTrip = new TripEntity(
                            UUID.randomUUID().toString(),
                            sessionManager.getUserId(),
                            name,
                            dest,
                            start,
                            end,
                            desc,
                            "active",
                            timeNow,
                            timeNow,
                            false
                    );

                    repository.insertTrip(newTrip);
                    Toast.makeText(this, "Trip '" + name + "' created successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showDatePicker(EditText targetField) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String selected = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            targetField.setText(selected);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}
