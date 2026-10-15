package com.smarttracker.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smarttracker.data.local.entity.MonitoringEventEntity;

import java.util.List;

@Dao
public interface MonitoringEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(MonitoringEventEntity event);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<MonitoringEventEntity> events);

    @Query("SELECT * FROM monitoring_events WHERE syncStatus = 'PENDING' ORDER BY occurredAt ASC LIMIT :limit")
    List<MonitoringEventEntity> getPendingEvents(int limit);

    @Query("UPDATE monitoring_events SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    void markEventsSynced(List<String> ids);

    @Query("UPDATE monitoring_events SET retryCount = retryCount + 1, syncStatus = CASE WHEN retryCount >= 5 THEN 'FAILED' ELSE 'PENDING' END WHERE id IN (:ids)")
    void incrementRetryCount(List<String> ids);

    @Query("SELECT COUNT(*) FROM monitoring_events WHERE syncStatus = 'PENDING'")
    int getPendingCount();

    @Query("DELETE FROM monitoring_events WHERE syncStatus = 'SYNCED' AND occurredAt < :olderThanTimestamp")
    void pruneOldSyncedEvents(long olderThanTimestamp);

    @Query("DELETE FROM monitoring_events WHERE id IN (SELECT id FROM monitoring_events ORDER BY occurredAt ASC LIMIT (SELECT MAX(0, COUNT(*) - :maxCapacity) FROM monitoring_events))")
    void pruneExcessCapacity(int maxCapacity);
}
