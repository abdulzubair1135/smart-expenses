package com.smarttracker.ui.activity;

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

import java.util.ArrayList;
import java.util.List;

public class ActivityFragment extends Fragment {

    private FragmentActivityBinding binding;
    private TransactionRepository repository;
    private TransactionAdapter adapter;
    private SessionManager sessionManager;
    private List<TransactionEntity> allTransactions = new ArrayList<>();
    private String currentFilter = "ALL";
    private String currentSearchQuery = "";

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
        observeTransactions();
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
        binding.btnFilterExpense.setOnClickListener(v -> setFilter("expense", binding.btnFilterExpense));
        binding.btnFilterReceived.setOnClickListener(v -> setFilter("received", binding.btnFilterReceived));
        binding.btnFilterLend.setOnClickListener(v -> setFilter("lend", binding.btnFilterLend));
        binding.btnFilterBorrow.setOnClickListener(v -> setFilter("borrow", binding.btnFilterBorrow));
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
                binding.btnFilterAll, binding.btnFilterExpense,
                binding.btnFilterReceived, binding.btnFilterLend, binding.btnFilterBorrow
        };
        for (Button b : buttons) {
            b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E2E8F0")));
            b.setTextColor(Color.parseColor("#0F172A"));
        }
    }

    private void applyFilter() {
        List<TransactionEntity> filtered = new ArrayList<>();
        for (TransactionEntity tx : allTransactions) {
            boolean matchesType = "ALL".equalsIgnoreCase(currentFilter) || currentFilter.equalsIgnoreCase(tx.type);
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    (tx.description != null && tx.description.toLowerCase().contains(currentSearchQuery)) ||
                    (tx.relatedPerson != null && tx.relatedPerson.toLowerCase().contains(currentSearchQuery));

            if (matchesType && matchesSearch) {
                filtered.add(tx);
            }
        }

        adapter.setTransactions(filtered);
        binding.llEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvActivityTransactions.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showTransactionOptionsDialog(TransactionEntity transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Transaction Details");
        builder.setMessage(String.format("Description: %s\nAmount: %s\nType: %s\nStatus: %s\nOffline Synced: %s",
                transaction.description,
                FinancialEngine.formatMinorToSimpleINR(transaction.amountMinor),
                transaction.type.toUpperCase(),
                transaction.status,
                transaction.isSynced ? "Yes" : "Pending"));

        builder.setNegativeButton("Delete", (dialog, which) -> {
            repository.deleteTransaction(transaction.id);
        });
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
