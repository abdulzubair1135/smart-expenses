package com.smarttracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Normalized Room entity for local persistence of error telemetry,
 * crashes, background failures and synchronization events.
 */
@Entity(tableName = "monitoring_events")
public class MonitoringEventEntity {

    @PrimaryKey
    @NonNull
    public String id; // Stable UUID

    @NonNull
    public String eventType;

    @NonNull
    public String severity; // INFO, WARNING, ERROR, CRITICAL

    @NonNull
    public String source; // ANDROID, SYNC, UI, BACKGROUND

    @NonNull
    public String errorCode;

    @NonNull
    public String message; // Sanitized

    public String stackTrace; // Sanitized

    public String screenName;

    public String operationName;

    @NonNull
    public String appVersion;

    @NonNull
    public String appBuild;

    public String androidVersion;

    public String deviceModel;

    public String userId; // Nullable or pseudonymous

    @NonNull
    public String installationId;

    @NonNull
    public String correlationId;

    public long occurredAt;

    @NonNull
    public String syncStatus; // PENDING, SYNCED, FAILED

    public int retryCount;

    public String metadataJson;

    public MonitoringEventEntity(@NonNull String id, @NonNull String eventType, @NonNull String severity,
                                 @NonNull String source, @NonNull String errorCode, @NonNull String message,
                                 String stackTrace, String screenName, String operationName,
                                 @NonNull String appVersion, @NonNull String appBuild, String androidVersion,
                                 String deviceModel, String userId, @NonNull String installationId,
                                 @NonNull String correlationId, long occurredAt, @NonNull String syncStatus,
                                 int retryCount, String metadataJson) {
        this.id = id;
        this.eventType = eventType;
        this.severity = severity;
        this.source = source;
        this.errorCode = errorCode;
        this.message = message;
        this.stackTrace = stackTrace;
        this.screenName = screenName;
        this.operationName = operationName;
        this.appVersion = appVersion;
        this.appBuild = appBuild;
        this.androidVersion = androidVersion;
        this.deviceModel = deviceModel;
        this.userId = userId;
        this.installationId = installationId;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.syncStatus = syncStatus;
        this.retryCount = retryCount;
        this.metadataJson = metadataJson != null ? metadataJson : "{}";
    }
}
