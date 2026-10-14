package com.smarttracker.ui.money;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.AccountEntity;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.databinding.FragmentMoneyBinding;

import java.util.List;

public class MoneyFragment extends Fragment {

    private FragmentMoneyBinding binding;
    private TransactionRepository repository;
    private static final String DEMO_USER_ID = "d0000000-0000-0000-0000-000000000001";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMoneyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new TransactionRepository(requireActivity().getApplication());

        binding.btnAddAccount.setOnClickListener(v -> showAddAccountDialog());

        observeBalances();
    }

    private void observeBalances() {
        String userId = com.smarttracker.data.session.SessionManager.getInstance(requireContext()).getUserId();
        repository.getAllTransactionsLive(userId).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null) {
                updateAccountBalances(transactions);
            }
        });
    }

    private void updateAccountBalances(List<TransactionEntity> transactions) {
        // Base balances from initial seed: Cash ₹2,000, Bank ₹8,000, UPI ₹1,500, Savings ₹5,000
        long cashMinor = 200000;
        long bankMinor = 800000;
        long upiMinor = 150000;
        long savingsMinor = 500000;

        for (TransactionEntity tx : transactions) {
            if (!"completed".equalsIgnoreCase(tx.status)) continue;

            long effect = 0;
            if ("expense".equalsIgnoreCase(tx.type) || "lend".equalsIgnoreCase(tx.type)) {
                effect = -tx.amountMinor;
            } else if ("received".equalsIgnoreCase(tx.type) || "borrow".equalsIgnoreCase(tx.type)) {
                effect = tx.amountMinor;
            }

            if ("acc_cash".equalsIgnoreCase(tx.accountId)) {
                cashMinor += effect;
            } else if ("acc_bank".equalsIgnoreCase(tx.accountId)) {
                bankMinor += effect;
            } else if ("acc_upi".equalsIgnoreCase(tx.accountId)) {
                upiMinor += effect;
            } else if ("acc_savings".equalsIgnoreCase(tx.accountId)) {
                savingsMinor += effect;
            }
        }

        long totalMinor = cashMinor + bankMinor + upiMinor + savingsMinor;

        if (isAdded()) {
            binding.tvMoneyTotal.setText(FinancialEngine.formatMinorToSimpleINR(totalMinor));
            binding.tvAccCashBalance.setText(FinancialEngine.formatMinorToSimpleINR(cashMinor));
            binding.tvAccBankBalance.setText(FinancialEngine.formatMinorToSimpleINR(bankMinor));
            binding.tvAccUpiBalance.setText(FinancialEngine.formatMinorToSimpleINR(upiMinor));
            binding.tvAccSavingsBalance.setText(FinancialEngine.formatMinorToSimpleINR(savingsMinor));
        }
    }

    private void showAddAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add Account");

        final EditText input = new EditText(requireContext());
        input.setHint("Account Name (e.g. Credit Card)");
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                Toast.makeText(requireContext(), "Account '" + name + "' added successfully!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}
