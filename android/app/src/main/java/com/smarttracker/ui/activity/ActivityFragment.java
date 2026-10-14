package com.smarttracker.ui.activity;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.FragmentActivityBinding;
import com.smarttracker.ui.home.TransactionAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActivityFragment extends Fragment {

    private FragmentActivityBinding binding;
    private TransactionRepository repository;
    private TransactionAdapter adapter;
    private SessionManager sessionManager;
    private List<TransactionEntity> allTransactions = new ArrayList<>();
    private String currentFilter = "ALL";
    private String currentSearchQuery = "";

    // Period Navigation Calendars
    private final Calendar dailyCalendar = Calendar.getInstance();
    private final Calendar monthlyCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat dayLabelFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
    private final SimpleDateFormat monthLabelFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

    private String pendingFilter = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentActivityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = SessionManager.getInstance(requireContext());
        repository = new TransactionRepository(requireActivity().getApplication());

        adapter = new TransactionAdapter(this::showTransactionOptionsDialog);
        binding.rvActivityTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActivityTransactions.setAdapter(adapter);

        setupSearchWatcher();
        setupFilterListeners();
        setupPeriodNavListeners();
        observeTransactions();

        if (pendingFilter != null) {
            applyInitialFilter(pendingFilter);
            pendingFilter = null;
        }
    }

    public void setInitialFilter(String filter) {
        if (binding != null) {
            applyInitialFilter(filter);
        } else {
            pendingFilter = filter;
        }
    }

    private void applyInitialFilter(String filter) {
        if ("DAILY".equalsIgnoreCase(filter)) {
            setFilter("DAILY", binding.btnFilterDaily);
        } else if ("MONTHLY".equalsIgnoreCase(filter)) {
            setFilter("MONTHLY", binding.btnFilterMonthly);
        } else if ("TRAVEL".equalsIgnoreCase(filter)) {
            setFilter("TRAVEL", binding.btnFilterTravel);
        } else if ("SPECIAL".equalsIgnoreCase(filter)) {
            setFilter("SPECIAL", binding.btnFilterSpecial);
        } else {
            setFilter("ALL", binding.btnFilterAll);
        }
    }

    private void setupSearchWatcher() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim().toLowerCase() : "";
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void observeTransactions() {
        String userId = sessionManager.getUserId();
        repository.getAllTransactionsLive(userId).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null) {
                allTransactions = transactions;
                applyFilter();
            }
        });
    }

    private void setupFilterListeners() {
        binding.btnFilterAll.setOnClickListener(v -> setFilter("ALL", binding.btnFilterAll));
        binding.btnFilterDaily.setOnClickListener(v -> setFilter("DAILY", binding.btnFilterDaily));
        binding.btnFilterMonthly.setOnClickListener(v -> setFilter("MONTHLY", binding.btnFilterMonthly));
        binding.btnFilterTravel.setOnClickListener(v -> setFilter("TRAVEL", binding.btnFilterTravel));
        binding.btnFilterSpecial.setOnClickListener(v -> setFilter("SPECIAL", binding.btnFilterSpecial));
        binding.btnFilterExpense.setOnClickListener(v -> setFilter("expense", binding.btnFilterExpense));
        binding.btnFilterReceived.setOnClickListener(v -> setFilter("received", binding.btnFilterReceived));
        binding.btnFilterLend.setOnClickListener(v -> setFilter("lend", binding.btnFilterLend));
        binding.btnFilterBorrow.setOnClickListener(v -> setFilter("borrow", binding.btnFilterBorrow));
    }

    private void setupPeriodNavListeners() {
        binding.btnPeriodPrev.setOnClickListener(v -> {
            if ("DAILY".equalsIgnoreCase(currentFilter)) {
                dailyCalendar.add(Calendar.DAY_OF_YEAR, -1);
            } else if ("MONTHLY".equalsIgnoreCase(currentFilter)) {
                monthlyCalendar.add(Calendar.MONTH, -1);
            }
            applyFilter();
        });

        binding.btnPeriodNext.setOnClickListener(v -> {
            if ("DAILY".equalsIgnoreCase(currentFilter)) {
                dailyCalendar.add(Calendar.DAY_OF_YEAR, 1);
            } else if ("MONTHLY".equalsIgnoreCase(currentFilter)) {
                monthlyCalendar.add(Calendar.MONTH, 1);
            }
            applyFilter();
        });

        binding.tvPeriodLabel.setOnClickListener(v -> {
            if ("DAILY".equalsIgnoreCase(currentFilter)) {
                DatePickerDialog dpd = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                    dailyCalendar.set(year, month, dayOfMonth);
                    applyFilter();
                }, dailyCalendar.get(Calendar.YEAR), dailyCalendar.get(Calendar.MONTH), dailyCalendar.get(Calendar.DAY_OF_MONTH));
                dpd.show();
            } else if ("MONTHLY".equalsIgnoreCase(currentFilter)) {
                DatePickerDialog dpd = new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                    monthlyCalendar.set(Calendar.YEAR, year);
                    monthlyCalendar.set(Calendar.MONTH, month);
                    applyFilter();
                }, monthlyCalendar.get(Calendar.YEAR), monthlyCalendar.get(Calendar.MONTH), 1);
                dpd.show();
            }
        });
    }

    private void setFilter(String filter, Button selectedBtn) {
        currentFilter = filter;
        resetButtonStyles();
        selectedBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
        selectedBtn.setTextColor(Color.WHITE);
        applyFilter();
    }

    private void resetButtonStyles() {
        Button[] buttons = {
                binding.btnFilterAll, binding.btnFilterDaily, binding.btnFilterMonthly,
                binding.btnFilterTravel, binding.btnFilterSpecial, binding.btnFilterExpense,
                binding.btnFilterReceived, binding.btnFilterLend, binding.btnFilterBorrow
        };
        for (Button b : buttons) {
            b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E2E8F0")));
            b.setTextColor(Color.parseColor("#0F172A"));
        }
    }

    private void applyFilter() {
        List<TransactionEntity> filtered = new ArrayList<>();
        long currentPeriodTotalMinor = 0;
        long previousPeriodTotalMinor = 0;
        Map<String, Long> categoryBreakdown = new HashMap<>();

        if ("DAILY".equalsIgnoreCase(currentFilter)) {
            binding.cardPeriodNav.setVisibility(View.VISIBLE);
            String selectedDay = dateFormat.format(dailyCalendar.getTime());
            binding.tvPeriodLabel.setText(dayLabelFormat.format(dailyCalendar.getTime()));

            Calendar prevCal = (Calendar) dailyCalendar.clone();
            prevCal.add(Calendar.DAY_OF_YEAR, -1);
            String prevDay = dateFormat.format(prevCal.getTime());

            for (TransactionEntity tx : allTransactions) {
                if (tx.transactionDate == null) continue;
                if ("expense".equalsIgnoreCase(tx.type)) {
                    if (tx.transactionDate.startsWith(selectedDay)) {
                        boolean matchesSearch = matchesSearch(tx);
                        if (matchesSearch) {
                            filtered.add(tx);
                        }
                        currentPeriodTotalMinor += tx.amountMinor;
                        String cat = tx.categoryId != null ? tx.categoryId.replace("cat_", "").replace("_", " ") : "general";
                        long prevCatTotal = categoryBreakdown.containsKey(cat) ? categoryBreakdown.get(cat) : 0L;
                        categoryBreakdown.put(cat, prevCatTotal + tx.amountMinor);
                    } else if (tx.transactionDate.startsWith(prevDay)) {
                        previousPeriodTotalMinor += tx.amountMinor;
                    }
                }
            }

            binding.tvPeriodSpending.setText("Daily Total: " + FinancialEngine.formatMinorToSimpleINR(currentPeriodTotalMinor));
            long diff = currentPeriodTotalMinor - previousPeriodTotalMinor;
            String diffSign = diff >= 0 ? "+" : "";
            binding.tvPeriodComparison.setText(String.format("vs Prev Day: %s (%s%s)",
                    FinancialEngine.formatMinorToSimpleINR(previousPeriodTotalMinor),
                    diffSign,
                    FinancialEngine.formatMinorToSimpleINR(diff)));

        } else if ("MONTHLY".equalsIgnoreCase(currentFilter)) {
            binding.cardPeriodNav.setVisibility(View.VISIBLE);
            String selectedMonth = monthFormat.format(monthlyCalendar.getTime());
            binding.tvPeriodLabel.setText(monthLabelFormat.format(monthlyCalendar.getTime()));

            Calendar prevCal = (Calendar) monthlyCalendar.clone();
            prevCal.add(Calendar.MONTH, -1);
            String prevMonth = monthFormat.format(prevCal.getTime());

            for (TransactionEntity tx : allTransactions) {
                if (tx.transactionDate == null) continue;
                if ("expense".equalsIgnoreCase(tx.type)) {
                    if (tx.transactionDate.startsWith(selectedMonth)) {
                        boolean matchesSearch = matchesSearch(tx);
                        if (matchesSearch) {
                            filtered.add(tx);
                        }
                        currentPeriodTotalMinor += tx.amountMinor;
                        String cat = tx.categoryId != null ? tx.categoryId.replace("cat_", "").replace("_", " ") : "general";
                        long prevCatTotal = categoryBreakdown.containsKey(cat) ? categoryBreakdown.get(cat) : 0L;
                        categoryBreakdown.put(cat, prevCatTotal + tx.amountMinor);
                    } else if (tx.transactionDate.startsWith(prevMonth)) {
                        previousPeriodTotalMinor += tx.amountMinor;
                    }
                }
            }

            binding.tvPeriodSpending.setText("Monthly Total: " + FinancialEngine.formatMinorToSimpleINR(currentPeriodTotalMinor));
            long diff = currentPeriodTotalMinor - previousPeriodTotalMinor;
            String diffSign = diff >= 0 ? "+" : "";
            binding.tvPeriodComparison.setText(String.format("vs Prev Month: %s (%s%s)",
                    FinancialEngine.formatMinorToSimpleINR(previousPeriodTotalMinor),
                    diffSign,
                    FinancialEngine.formatMinorToSimpleINR(diff)));

        } else {
            binding.cardPeriodNav.setVisibility(View.GONE);

            for (TransactionEntity tx : allTransactions) {
                boolean matchesType;
                if ("ALL".equalsIgnoreCase(currentFilter)) {
                    matchesType = true;
                } else if ("TRAVEL".equalsIgnoreCase(currentFilter)) {
                    matchesType = "TRAVEL".equalsIgnoreCase(tx.expenseGroup) || tx.tripId != null;
                } else if ("SPECIAL".equalsIgnoreCase(currentFilter)) {
                    matchesType = "SPECIAL".equalsIgnoreCase(tx.expenseGroup);
                } else {
                    matchesType = currentFilter.equalsIgnoreCase(tx.type);
                }

                if (matchesType && matchesSearch(tx)) {
                    filtered.add(tx);
                }
            }
        }

        // Format breakdown string if any
        if (!categoryBreakdown.isEmpty()) {
            StringBuilder sb = new StringBuilder("Breakdown: ");
            int count = 0;
            for (Map.Entry<String, Long> entry : categoryBreakdown.entrySet()) {
                if (count > 0) sb.append(" • ");
                sb.append(entry.getKey()).append(": ").append(FinancialEngine.formatMinorToSimpleINR(entry.getValue()));
                count++;
                if (count >= 3) break;
            }
            binding.tvPeriodBreakdown.setText(sb.toString());
            binding.tvPeriodBreakdown.setVisibility(View.VISIBLE);
        } else {
            binding.tvPeriodBreakdown.setVisibility(View.GONE);
        }

        adapter.setTransactions(filtered);
        binding.llEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvActivityTransactions.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean matchesSearch(TransactionEntity tx) {
        if (currentSearchQuery.isEmpty()) return true;
        return (tx.description != null && tx.description.toLowerCase().contains(currentSearchQuery)) ||
                (tx.relatedPerson != null && tx.relatedPerson.toLowerCase().contains(currentSearchQuery)) ||
                (tx.categoryId != null && tx.categoryId.toLowerCase().contains(currentSearchQuery)) ||
                (tx.expenseGroup != null && tx.expenseGroup.toLowerCase().contains(currentSearchQuery));
    }

    private void showTransactionOptionsDialog(TransactionEntity transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Transaction Details");
        String details = String.format("Description: %s\nAmount: %s\nType: %s\nGroup: %s\nStatus: %s\nDate: %s\nOffline Synced: %s",
                transaction.description,
                FinancialEngine.formatMinorToSimpleINR(transaction.amountMinor),
                transaction.type.toUpperCase(),
                transaction.expenseGroup != null ? transaction.expenseGroup : "GENERAL",
                transaction.status,
                transaction.transactionDate != null ? transaction.transactionDate.substring(0, Math.min(10, transaction.transactionDate.length())) : "",
                transaction.isSynced ? "Yes" : "Pending");

        if (transaction.isImportant) {
            details += "\n⭐ Marked as Special / Emergency";
        }
        if (transaction.tripId != null) {
            details += "\n✈️ Trip Linked";
        }

        builder.setMessage(details);
        builder.setNegativeButton("Delete", (dialog, which) -> {
            repository.deleteTransaction(transaction.id);
        });
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
