package com.smarttracker.ui.insights;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.databinding.FragmentInsightsBinding;

import java.util.List;

public class InsightsFragment extends Fragment {

    private FragmentInsightsBinding binding;
    private TransactionRepository repository;
    private static final String DEMO_USER_ID = "d0000000-0000-0000-0000-000000000001";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInsightsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new TransactionRepository(requireActivity().getApplication());

        observeTransactions();
    }

    private void observeTransactions() {
        String userId = com.smarttracker.data.session.SessionManager.getInstance(requireContext()).getUserId();
        repository.getAllTransactionsLive(userId).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null) {
                computeInsights(transactions);
            }
        });
    }

    private void computeInsights(List<TransactionEntity> transactions) {
        long totalReceivedMinor = 0;
        long totalExpenseMinor = 0;

        for (TransactionEntity tx : transactions) {
            if (!"completed".equalsIgnoreCase(tx.status)) continue;

            if ("received".equalsIgnoreCase(tx.type)) {
                totalReceivedMinor += tx.amountMinor;
            } else if ("expense".equalsIgnoreCase(tx.type)) {
                totalExpenseMinor += tx.amountMinor;
            }
        }

        // Add demo base baseline if needed for college presentation (₹25k income, ₹16.5k expense)
        if (totalReceivedMinor == 0) totalReceivedMinor = 2500000;
        if (totalExpenseMinor == 0) totalExpenseMinor = 1650000;

        long netSavings = totalReceivedMinor - totalExpenseMinor;

        if (isAdded()) {
            binding.tvInsightReceived.setText(FinancialEngine.formatMinorToSimpleINR(totalReceivedMinor));
            binding.tvInsightExpense.setText(FinancialEngine.formatMinorToSimpleINR(totalExpenseMinor));
            binding.tvInsightSavings.setText(FinancialEngine.formatMinorToSimpleINR(netSavings));
        }
    }
}
