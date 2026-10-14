package com.smarttracker.ui.travel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smarttracker.R;
import com.smarttracker.core.common.FinancialEngine;
import com.smarttracker.data.local.entity.TransactionEntity;
import com.smarttracker.data.local.entity.TripEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    public interface OnTripClickListener {
        void onTripClick(TripEntity trip);
    }

    private List<TripEntity> trips = new ArrayList<>();
    private Map<String, Long> tripSpendingMap = new HashMap<>();
    private final OnTripClickListener listener;

    public TripAdapter(OnTripClickListener listener) {
        this.listener = listener;
    }

    public void setTrips(List<TripEntity> trips, List<TransactionEntity> transactions) {
        this.trips = trips != null ? trips : new ArrayList<>();
        this.tripSpendingMap.clear();

        if (transactions != null) {
            for (TransactionEntity tx : transactions) {
                if (tx.tripId != null && "expense".equalsIgnoreCase(tx.type) && "completed".equalsIgnoreCase(tx.status)) {
                    long current = tripSpendingMap.containsKey(tx.tripId) ? tripSpendingMap.get(tx.tripId) : 0L;
                    tripSpendingMap.put(tx.tripId, current + tx.amountMinor);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        TripEntity trip = trips.get(position);
        holder.tvTripName.setText(trip.name);
        holder.tvTripDestination.setText("📍 " + trip.destination);
        holder.tvTripDates.setText("📅 " + trip.startDate + " – " + trip.endDate);
        holder.tvTripStatus.setText(trip.status.toUpperCase());

        long spent = tripSpendingMap.containsKey(trip.id) ? tripSpendingMap.get(trip.id) : 0L;
        holder.tvTripTotalSpent.setText(FinancialEngine.formatMinorToSimpleINR(spent));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTripClick(trip);
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView tvTripName, tvTripStatus, tvTripDestination, tvTripDates, tvTripTotalSpent;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTripName = itemView.findViewById(R.id.tv_trip_name);
            tvTripStatus = itemView.findViewById(R.id.tv_trip_status);
            tvTripDestination = itemView.findViewById(R.id.tv_trip_destination);
            tvTripDates = itemView.findViewById(R.id.tv_trip_dates);
            tvTripTotalSpent = itemView.findViewById(R.id.tv_trip_total_spent);
        }
    }
}
