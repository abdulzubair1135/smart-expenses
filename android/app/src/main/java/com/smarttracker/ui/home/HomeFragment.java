package com.smarttracker.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import com.smarttracker.ui.travel.TravelTripsActivity;
import com.smarttracker.ui.special.SpecialExpensesActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.data.session.SessionManager;
import com.smarttracker.databinding.FragmentHomeBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private TransactionRepository repository;
    private TransactionAdapter adapter;
    private SessionManager sessionManager;

    public interface OnSettingsClickListener {
        void onSettingsClick();
    }

    private OnSettingsClickListener settingsClickListener;

    public void setOnSettingsClickListener(OnSettingsClickListener listener) {
        this.settingsClickListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = SessionManager.getInstance(requireContext());
        repository = new TransactionRepository(requireActivity().getApplication());

        setupGreetingAndHeader();

        adapter = new TransactionAdapter(this::showTransactionOptionsDialog);
        binding.rvRecentTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentTransactions.setAdapter(adapter);

        binding.btnSettings.setOnClickListener(v -> {
            if (settingsClickListener != null) {
                settingsClickListener.onSettingsClick();
            }
        });

        binding.btnVoice.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showVoiceAssistantDialog();
            }
        });

        binding.cardOverviewToday.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openActivityWithFilter("DAILY");
            }
        });

        binding.cardOverviewMonth.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openActivityWithFilter("MONTHLY");
            }
        });

        binding.cardOverviewTravel.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), TravelTripsActivity.class));
        });

        binding.cardOverviewSpecial.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SpecialExpensesActivity.class));
        });

        // Trigger entrance animations
        try {
            binding.cardBalance.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.card_entrance));
        } catch (Exception ignored) {}

        observeData();
    }

    private void setupGreetingAndHeader() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Good Morning,";
        } else if (hour < 17) {
            greeting = "Good Afternoon,";
        } else {
            greeting = "Good Evening,";
        }

        binding.tvUserName.setText(sessionManager.getUserName());
    }

    private java.util.List<com.smarttracker.data.local.entity.AccountEntity> currentAccounts = null;
    private List<TransactionEntity> currentTransactions = null;

    public void observeData() {
        String userId = sessionManager.getUserId();

        repository.getAccountsLive(userId).observe(getViewLifecycleOwner(), accounts -> {
            this.currentAccounts = accounts;
            if (currentTransactions != null) {
                computeAndDisplayBalances(currentTransactions);
            }
        });

        repository.getAllTransactionsLive(userId).observe(getViewLifecycleOwner(), transactions -> {
            this.currentTransactions = transactions;
            if (transactions != null) {
                if (transactions.isEmpty()) {
                    binding.rvRecentTransactions.setVisibility(View.GONE);
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                } else {
                    binding.rvRecentTransactions.setVisibility(View.VISIBLE);
                    binding.tvEmptyState.setVisibility(View.GONE);
                    adapter.setTransactions(transactions.size() > 5 ? transactions.subList(0, 5) : transactions);
                }
                computeAndDisplayBalances(transactions);
            }
        });

        repository.getPrimaryGoalLive(userId).observe(getViewLifecycleOwner(), goal -> {
            if (goal != null && isAdded()) {
                binding.tvGoalTitle.setText(goal.title);
                String amounts = String.format("%s / %s",
                        FinancialEngine.formatMinorToSimpleINR(goal.currentAmountMinor),
                        FinancialEngine.formatMinorToSimpleINR(goal.targetAmountMinor));
                binding.tvGoalAmounts.setText(amounts);
                int progress = (int) ((goal.currentAmountMinor * 100.0) / goal.targetAmountMinor);
                binding.tvGoalPercentage.setText(progress + "%");
                binding.pbGoalProgress.setProgress(progress);
            }
        });

        repository.getNextUpcomingLive(userId).observe(getViewLifecycleOwner(), upcoming -> {
            if (upcoming != null && isAdded()) {
                binding.tvUpcomingTitle.setText(upcoming.title);
                binding.tvUpcomingAmount.setText(String.format("%s • %s",
                        FinancialEngine.formatMinorToSimpleINR(upcoming.expectedAmountMinor),
                        upcoming.dueDate));
            }
        });
    }

    private void computeAndDisplayBalances(List<TransactionEntity> transactions) {
        long availableBalanceMinor = 0;
        if (currentAccounts != null && !currentAccounts.isEmpty()) {
            for (com.smarttracker.data.local.entity.AccountEntity acc : currentAccounts) {
                availableBalanceMinor += acc.openingBalanceMinor;
            }
        }
        long todayExpenseMinor = 0;
        long todayReceivedMinor = 0;
        long monthExpenseMinor = 0;
        long travelExpenseMinor = 0;
        long specialExpenseMinor = 0;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String thisMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());

        for (TransactionEntity tx : transactions) {
            if (!"completed".equalsIgnoreCase(tx.status)) continue;

            if ("expense".equalsIgnoreCase(tx.type)) {
                availableBalanceMinor -= tx.amountMinor;

                if (tx.transactionDate != null) {
                    if (tx.transactionDate.startsWith(today)) {
                        todayExpenseMinor += tx.amountMinor;
                    }
                    if (tx.transactionDate.startsWith(thisMonth)) {
                        monthExpenseMinor += tx.amountMinor;
                    }
                }

                if ("TRAVEL".equalsIgnoreCase(tx.expenseGroup) || tx.tripId != null) {
                    travelExpenseMinor += tx.amountMinor;
                }

                if ("SPECIAL".equalsIgnoreCase(tx.expenseGroup)) {
                    specialExpenseMinor += tx.amountMinor;
                }
            } else if ("received".equalsIgnoreCase(tx.type)) {
                availableBalanceMinor += tx.amountMinor;
                if (tx.transactionDate != null && tx.transactionDate.startsWith(today)) {
                    todayReceivedMinor += tx.amountMinor;
                }
            } else if ("lend".equalsIgnoreCase(tx.type)) {
                availableBalanceMinor -= tx.amountMinor;
            } else if ("borrow".equalsIgnoreCase(tx.type)) {
                availableBalanceMinor += tx.amountMinor;
            }
        }

        if (isAdded()) {
            binding.tvAvailableBalance.setText(FinancialEngine.formatMinorToSimpleINR(availableBalanceMinor));
            binding.tvTodayExpense.setText(FinancialEngine.formatMinorToSimpleINR(todayExpenseMinor));
            binding.tvTodayReceived.setText(FinancialEngine.formatMinorToSimpleINR(todayReceivedMinor));

            // Expense Overview 4 cards
            binding.tvOverviewToday.setText(FinancialEngine.formatMinorToSimpleINR(todayExpenseMinor));
            binding.tvOverviewMonth.setText(FinancialEngine.formatMinorToSimpleINR(monthExpenseMinor));
            binding.tvOverviewTravel.setText(FinancialEngine.formatMinorToSimpleINR(travelExpenseMinor));
            binding.tvOverviewSpecial.setText(FinancialEngine.formatMinorToSimpleINR(specialExpenseMinor));
        }
    }

    private void showTransactionOptionsDialog(TransactionEntity transaction) {
        if (transaction == null || getChildFragmentManager().isStateSaved()) return;
        com.smarttracker.ui.common.TransactionDetailBottomSheet sheet =
                com.smarttracker.ui.common.TransactionDetailBottomSheet.newInstance(transaction, id -> {
                    repository.deleteTransaction(id);
                });
        sheet.show(getChildFragmentManager(), "TransactionDetailBottomSheet");
    }
}
