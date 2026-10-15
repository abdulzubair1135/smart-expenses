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

    private java.util.List<AccountEntity> currentAccounts = null;
    private java.util.List<TransactionEntity> currentTransactions = null;

    private void observeBalances() {
        String userId = com.smarttracker.data.session.SessionManager.getInstance(requireContext()).getUserId();
        repository.getAccountsLive(userId).observe(getViewLifecycleOwner(), accounts -> {
            this.currentAccounts = accounts;
            if (currentTransactions != null) {
                updateAccountBalances(currentTransactions);
            }
        });
        repository.getAllTransactionsLive(userId).observe(getViewLifecycleOwner(), transactions -> {
            this.currentTransactions = transactions;
            if (transactions != null) {
                updateAccountBalances(transactions);
            }
        });
    }

    private void updateAccountBalances(List<TransactionEntity> transactions) {
        long cashMinor = 200000;
        long bankMinor = 800000;
        long upiMinor = 150000;
        long savingsMinor = 500000;

        if (currentAccounts != null && !currentAccounts.isEmpty()) {
            for (AccountEntity acc : currentAccounts) {
                String type = acc.type != null ? acc.type.toLowerCase() : "";
                if (type.contains("cash")) cashMinor = acc.openingBalanceMinor;
                else if (type.contains("bank")) bankMinor = acc.openingBalanceMinor;
                else if (type.contains("upi")) upiMinor = acc.openingBalanceMinor;
                else if (type.contains("savings")) savingsMinor = acc.openingBalanceMinor;
            }
        }

        for (TransactionEntity tx : transactions) {
            if (!"completed".equalsIgnoreCase(tx.status)) continue;

            long effect = 0;
            if ("expense".equalsIgnoreCase(tx.type) || "lend".equalsIgnoreCase(tx.type)) {
                effect = -tx.amountMinor;
            } else if ("received".equalsIgnoreCase(tx.type) || "borrow".equalsIgnoreCase(tx.type)) {
                effect = tx.amountMinor;
            }

            String accId = tx.accountId != null ? tx.accountId.toLowerCase() : "";
            if (accId.contains("cash") || "6d6badcf-b4d0-4f29-b0ea-3f4a42b6daf1".equalsIgnoreCase(tx.accountId)) {
                cashMinor += effect;
            } else if (accId.contains("bank") || "76de6e02-5f6d-49cc-959f-b56d10f6d29b".equalsIgnoreCase(tx.accountId)) {
                bankMinor += effect;
            } else if (accId.contains("upi") || "445bd17b-e290-4838-bc6a-764748b88251".equalsIgnoreCase(tx.accountId)) {
                upiMinor += effect;
            } else if (accId.contains("savings") || "8e904f50-2677-4520-82b9-32f6986e18f4".equalsIgnoreCase(tx.accountId)) {
                savingsMinor += effect;
            } else {
                bankMinor += effect;
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
