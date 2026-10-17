package com.smarttracker.ui.insights;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.CategoryEntity;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.repository.TransactionRepository;
import com.smarttracker.databinding.FragmentInsightsBinding;
import com.smarttracker.databinding.ItemInsightCategoryBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InsightsFragment extends Fragment {

    private FragmentInsightsBinding binding;
    private TransactionRepository repository;
    private List<CategoryEntity> currentCategories = new ArrayList<>();
    private List<TransactionEntity> currentTransactions = new ArrayList<>();

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

        observeData();
    }

    private void observeData() {
        String userId = com.smarttracker.data.session.SessionManager.getInstance(requireContext()).getUserId();

        repository.getCategoriesLive(userId).observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                this.currentCategories = categories;
                if (!currentTransactions.isEmpty()) {
                    computeInsights(currentTransactions);
                }
            }
        });

        repository.getAllTransactionsLive(userId).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null) {
                this.currentTransactions = transactions;
                computeInsights(transactions);
            }
        });
    }

    private void computeInsights(List<TransactionEntity> transactions) {
        if (!isAdded() || binding == null) return;

        long totalReceivedMinor = 0;
        long totalExpenseMinor = 0;

        Map<String, Long> categoryExpenseMap = new HashMap<>();
        long generalMinor = 0;
        long travelMinor = 0;
        long specialMinor = 0;

        for (TransactionEntity tx : transactions) {
            if (!"completed".equalsIgnoreCase(tx.status)) continue;

            if ("received".equalsIgnoreCase(tx.type)) {
                totalReceivedMinor += tx.amountMinor;
            } else if ("expense".equalsIgnoreCase(tx.type)) {
                totalExpenseMinor += tx.amountMinor;

                // Category accumulation
                String catKey = tx.categoryId != null && !tx.categoryId.trim().isEmpty() ? tx.categoryId : "cat_other";
                long currentCatSum = categoryExpenseMap.containsKey(catKey) ? categoryExpenseMap.get(catKey) : 0L;
                categoryExpenseMap.put(catKey, currentCatSum + tx.amountMinor);

                // Group accumulation
                if ("TRAVEL".equalsIgnoreCase(tx.expenseGroup) || tx.tripId != null) {
                    travelMinor += tx.amountMinor;
                } else if ("SPECIAL".equalsIgnoreCase(tx.expenseGroup)) {
                    specialMinor += tx.amountMinor;
                } else {
                    generalMinor += tx.amountMinor;
                }
            }
        }

        long netSavings = totalReceivedMinor - totalExpenseMinor;

        binding.tvInsightReceived.setText(FinancialEngine.formatMinorToSimpleINR(totalReceivedMinor));
        binding.tvInsightExpense.setText(FinancialEngine.formatMinorToSimpleINR(totalExpenseMinor));
        binding.tvInsightSavings.setText(FinancialEngine.formatMinorToSimpleINR(netSavings));

        renderCategoryBreakdown(categoryExpenseMap, totalExpenseMinor);
        renderExpenseGroups(generalMinor, travelMinor, specialMinor, totalExpenseMinor);
    }

    private static class CategoryInsightData {
        String id;
        String name;
        String colorHex;
        long amountMinor;
        int percentage;

        CategoryInsightData(String id, String name, String colorHex, long amountMinor, int percentage) {
            this.id = id;
            this.name = name;
            this.colorHex = colorHex;
            this.amountMinor = amountMinor;
            this.percentage = percentage;
        }
    }

    private void renderCategoryBreakdown(Map<String, Long> categoryExpenseMap, long totalExpenseMinor) {
        binding.layoutCategoriesContainer.removeAllViews();
        binding.layoutSegmentedChart.removeAllViews();

        if (totalExpenseMinor <= 0 || categoryExpenseMap.isEmpty()) {
            binding.cardSegmentedChart.setVisibility(View.GONE);
            binding.layoutCategoriesContainer.setVisibility(View.GONE);
            binding.tvEmptyCategories.setVisibility(View.VISIBLE);
            return;
        }

        binding.cardSegmentedChart.setVisibility(View.VISIBLE);
        binding.layoutCategoriesContainer.setVisibility(View.VISIBLE);
        binding.tvEmptyCategories.setVisibility(View.GONE);

        // Build list of category display items
        List<CategoryInsightData> items = new ArrayList<>();
        for (Map.Entry<String, Long> entry : categoryExpenseMap.entrySet()) {
            String catId = entry.getKey();
            long amount = entry.getValue();
            int pct = (int) Math.round((amount * 100.0) / totalExpenseMinor);
            if (pct <= 0 && amount > 0) pct = 1; // Floor to at least 1% if non-zero

            String name = "Others";
            String colorHex = "#6B7280";

            // Resolve from currentCategories
            for (CategoryEntity c : currentCategories) {
                if (c.id.equals(catId) || (c.userId == null && catId.contains(c.name.toLowerCase()))) {
                    name = c.name;
                    colorHex = c.colorHex != null ? c.colorHex : "#6B7280";
                    break;
                }
            }

            // Fallback name mapping for UUIDs
            if ("Others".equals(name)) {
                if (catId.endsWith("000000000001") || catId.contains("food")) {
                    name = "Food & Dining";
                    colorHex = "#EF4444";
                } else if (catId.endsWith("000000000002") || catId.contains("travel")) {
                    name = "Travel & Transport";
                    colorHex = "#F59E0B";
                } else if (catId.endsWith("000000000003") || catId.contains("shopping")) {
                    name = "Shopping";
                    colorHex = "#3B82F6";
                } else if (catId.endsWith("000000000004") || catId.contains("bills")) {
                    name = "Bills & Utilities";
                    colorHex = "#8B5CF6";
                } else if (catId.endsWith("000000000005") || catId.contains("other")) {
                    name = "General & Others";
                    colorHex = "#6B7280";
                }
            }

            items.add(new CategoryInsightData(catId, name, colorHex, amount, pct));
        }

        // Sort descending by amount
        Collections.sort(items, (a, b) -> Long.compare(b.amountMinor, a.amountMinor));

        // Render multi-segment horizontal chart and dynamic rows
        for (CategoryInsightData item : items) {
            int parsedColor;
            try {
                parsedColor = Color.parseColor(item.colorHex);
            } catch (Exception e) {
                parsedColor = Color.parseColor("#6B7280");
            }

            // Add segment to multi-color chart bar
            View segment = new View(requireContext());
            LinearLayout.LayoutParams segParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) item.percentage);
            segment.setLayoutParams(segParams);
            segment.setBackgroundColor(parsedColor);
            binding.layoutSegmentedChart.addView(segment);

            // Inflate row
            ItemInsightCategoryBinding row = ItemInsightCategoryBinding.inflate(getLayoutInflater(), binding.layoutCategoriesContainer, false);
            row.tvCategoryName.setText(item.name);
            row.tvCategoryAmount.setText(FinancialEngine.formatMinorToSimpleINR(item.amountMinor));
            row.tvCategoryPercentage.setText(item.percentage + "%");
            row.pbCategoryProgress.setProgress(item.percentage);
            row.pbCategoryProgress.setProgressTintList(ColorStateList.valueOf(parsedColor));
            row.viewCategoryIndicator.setBackgroundTintList(ColorStateList.valueOf(parsedColor));

            binding.layoutCategoriesContainer.addView(row.getRoot());
        }
    }

    private void renderExpenseGroups(long generalMinor, long travelMinor, long specialMinor, long totalExpenseMinor) {
        if (totalExpenseMinor <= 0) {
            binding.cardExpenseGroups.setVisibility(View.GONE);
            return;
        }

        binding.cardExpenseGroups.setVisibility(View.VISIBLE);

        int pctGeneral = (int) Math.round((generalMinor * 100.0) / totalExpenseMinor);
        int pctTravel = (int) Math.round((travelMinor * 100.0) / totalExpenseMinor);
        int pctSpecial = (int) Math.round((specialMinor * 100.0) / totalExpenseMinor);

        binding.tvGroupGeneralAmount.setText(FinancialEngine.formatMinorToSimpleINR(generalMinor));
        binding.tvGroupGeneralPct.setText(pctGeneral + "%");
        binding.pbGroupGeneral.setProgress(pctGeneral);
        binding.pbGroupGeneral.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#3B82F6")));

        binding.tvGroupTravelAmount.setText(FinancialEngine.formatMinorToSimpleINR(travelMinor));
        binding.tvGroupTravelPct.setText(pctTravel + "%");
        binding.pbGroupTravel.setProgress(pctTravel);
        binding.pbGroupTravel.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B")));

        binding.tvGroupSpecialAmount.setText(FinancialEngine.formatMinorToSimpleINR(specialMinor));
        binding.tvGroupSpecialPct.setText(pctSpecial + "%");
        binding.pbGroupSpecial.setProgress(pctSpecial);
        binding.pbGroupSpecial.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#EC4899")));
    }
}

