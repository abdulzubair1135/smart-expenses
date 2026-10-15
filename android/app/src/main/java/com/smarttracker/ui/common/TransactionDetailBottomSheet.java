package com.smarttracker.ui.common;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;

public class TransactionDetailBottomSheet extends BottomSheetDialogFragment {

    public interface OnTransactionDeletedListener {
        void onDeleted(String transactionId);
    }

    private TransactionEntity transaction;
    private OnTransactionDeletedListener deletedListener;

    public static TransactionDetailBottomSheet newInstance(TransactionEntity transaction, OnTransactionDeletedListener listener) {
        TransactionDetailBottomSheet sheet = new TransactionDetailBottomSheet();
        sheet.transaction = transaction;
        sheet.deletedListener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_transaction_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (transaction == null) {
            dismiss();
            return;
        }

        TextView tvTypeBadge = view.findViewById(R.id.tvDetailTypeBadge);
        ImageView btnClose = view.findViewById(R.id.btnDetailClose);
        TextView tvAmount = view.findViewById(R.id.tvDetailAmount);
        TextView tvSubtitle = view.findViewById(R.id.tvDetailStatusSubtitle);
        TextView tvDescription = view.findViewById(R.id.tvDetailDescription);
        TextView tvAccount = view.findViewById(R.id.tvDetailAccount);
        TextView tvCategory = view.findViewById(R.id.tvDetailCategory);
        TextView tvDate = view.findViewById(R.id.tvDetailDate);
        TextView tvSyncStatus = view.findViewById(R.id.tvDetailSyncStatus);
        TextView tvSpecialBadge = view.findViewById(R.id.tvDetailSpecialBadge);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteTransaction);
        MaterialButton btnDone = view.findViewById(R.id.btnDoneDetail);

        btnClose.setOnClickListener(v -> dismiss());
        btnDone.setOnClickListener(v -> dismiss());

        // Type & Colors
        String type = transaction.type != null ? transaction.type.toUpperCase() : "TRANSACTION";
        tvTypeBadge.setText(type);

        String amountFormatted = FinancialEngine.formatMinorToSimpleINR(transaction.amountMinor);
        if ("EXPENSE".equalsIgnoreCase(type) || "LEND".equalsIgnoreCase(type)) {
            tvAmount.setText("- " + amountFormatted);
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_expense));
            tvTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_expense));
        } else if ("RECEIVED".equalsIgnoreCase(type) || "BORROW".equalsIgnoreCase(type) || "INCOME".equalsIgnoreCase(type)) {
            tvAmount.setText("+ " + amountFormatted);
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_income));
            tvTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_income));
        } else {
            tvAmount.setText(amountFormatted);
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_transfer));
            tvTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_transfer));
        }

        // Subtitle
        String status = transaction.status != null ? transaction.status.toUpperCase() : "COMPLETED";
        tvSubtitle.setText("Payment " + status);

        // Description
        tvDescription.setText(transaction.description != null && !transaction.description.isEmpty()
                ? transaction.description : "Expense Record");

        // Account Resolution
        String accountName = "Cash Wallet";
        if (transaction.accountId != null) {
            String acc = transaction.accountId.toLowerCase();
            if (acc.contains("bank") || acc.contains("76de6e02")) {
                accountName = "Bank Account (Primary)";
            } else if (acc.contains("upi") || acc.contains("445bd17b")) {
                accountName = "UPI (Instant Transfer)";
            } else if (acc.contains("savings") || acc.contains("8e904f50")) {
                accountName = "Savings Vault";
            }
        }
        tvAccount.setText(accountName);

        // Category & Group
        String category = transaction.categoryId != null ? transaction.categoryId : "General";
        String group = transaction.expenseGroup != null ? transaction.expenseGroup : "GENERAL";
        tvCategory.setText(category + " (" + group + ")");

        // Date & Time
        if (transaction.transactionDate != null && !transaction.transactionDate.isEmpty()) {
            String dt = transaction.transactionDate.replace("T", " ");
            if (dt.length() > 16) {
                dt = dt.substring(0, 16);
            }
            tvDate.setText(dt);
        } else {
            tvDate.setText("Today");
        }

        // Cloud Sync Status
        if (transaction.isSynced) {
            tvSyncStatus.setText("✓ Synced (PostgreSQL Cloud)");
            tvSyncStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary));
        } else {
            tvSyncStatus.setText("⌛ Queued for Offline Sync");
            tvSyncStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.financial_pending));
        }

        // Special Badges
        if (transaction.isImportant) {
            tvSpecialBadge.setVisibility(View.VISIBLE);
            tvSpecialBadge.setText("⭐ Marked as Special / Emergency Expense");
        } else if (transaction.tripId != null && !transaction.tripId.isEmpty()) {
            tvSpecialBadge.setVisibility(View.VISIBLE);
            tvSpecialBadge.setText("✈️ Linked to Travel Trip");
        } else {
            tvSpecialBadge.setVisibility(View.GONE);
        }

        // Delete Action
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to permanently delete this transaction?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (deletedListener != null) {
                            deletedListener.onDeleted(transaction.id);
                        }
                        dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}
