package com.smarttracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.smarttracker.data.local.entity.TripEntity;

import java.util.List;

@Dao
public interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TripEntity trip);

    @Update
    void update(TripEntity trip);

    @Query("DELETE FROM trips WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM trips WHERE userId = :userId ORDER BY startDate DESC")
    LiveData<List<TripEntity>> getTripsByUserLive(String userId);

    @Query("SELECT * FROM trips WHERE userId = :userId ORDER BY startDate DESC")
    List<TripEntity> getTripsByUser(String userId);

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    TripEntity getTripById(String id);

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    LiveData<TripEntity> getTripByIdLive(String id);

    @Query("SELECT * FROM trips WHERE userId = :userId AND status = :status ORDER BY startDate DESC")
    LiveData<List<TripEntity>> getTripsByStatusLive(String userId, String status);

    @Query("SELECT * FROM trips WHERE isSynced = 0")
    List<TripEntity> getUnsyncedTrips();

    @Query("UPDATE trips SET isSynced = 1 WHERE id = :id")
    void markAsSynced(String id);

    @Query("UPDATE trips SET userId = :newUserId")
    void reassignAllTripsToUser(String newUserId);
}
