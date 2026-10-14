package com.smarttracker.ui.home;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.databinding.ItemTransactionBinding;

import java.util.ArrayList;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<TransactionEntity> transactions = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TransactionEntity transaction);
    }

    public TransactionAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<TransactionEntity> transactions) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        holder.bind(transactions.get(position));
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;

        TransactionViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TransactionEntity tx) {
            Context ctx = binding.getRoot().getContext();

            // Format Amount & Color based on Type
            String formattedAmount = FinancialEngine.formatMinorToSimpleINR(tx.amountMinor);
            if ("expense".equalsIgnoreCase(tx.type)) {
                binding.tvTxAmount.setText("- " + formattedAmount);
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.financial_expense));
                binding.flIconBg.setBackgroundColor(Color.parseColor("#FEE2E2"));
            } else if ("received".equalsIgnoreCase(tx.type)) {
                binding.tvTxAmount.setText("+ " + formattedAmount);
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.financial_income));
                binding.flIconBg.setBackgroundColor(Color.parseColor("#DCFCE7"));
            } else if ("lend".equalsIgnoreCase(tx.type)) {
                binding.tvTxAmount.setText("- " + formattedAmount);
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.financial_lend));
                binding.flIconBg.setBackgroundColor(Color.parseColor("#FEF3C7"));
            } else if ("borrow".equalsIgnoreCase(tx.type)) {
                binding.tvTxAmount.setText("+ " + formattedAmount);
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.financial_borrow));
                binding.flIconBg.setBackgroundColor(Color.parseColor("#EDE9FE"));
            } else {
                binding.tvTxAmount.setText(formattedAmount);
                binding.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.financial_transfer));
                binding.flIconBg.setBackgroundColor(Color.parseColor("#DBEAFE"));
            }

            binding.tvTxTitle.setText(tx.description != null && !tx.description.isEmpty() ? tx.description : tx.type);
            binding.tvTxSubtitle.setText(String.format("%s • %s", tx.type.toUpperCase(), tx.status));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(tx);
            });
        }
    }
}
