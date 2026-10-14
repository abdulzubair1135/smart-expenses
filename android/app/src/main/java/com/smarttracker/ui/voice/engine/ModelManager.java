package com.smarttracker.ui.voice.engine;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the on-device Qwen GGUF model lifecycle:
 * - App-managed storage in getExternalFilesDir("models")
 * - Storage check (>1GB free required)
 * - Download with progress, cancellation, and resume
 * - Manual sideload detection (via ADB or manual file transfer)
 * - Safe model deletion
 */
public class ModelManager {

    private static final String TAG = "ModelManager";

    public static final String MODEL_FILE_NAME = "qwen-0.6b-instruct-q4_k_m.gguf";
    public static final String MODEL_DISPLAY_NAME = "Qwen3 0.6B Instruct (Q4_K_M GGUF)";
    public static final long EXPECTED_MODEL_SIZE_BYTES = 397L * 1024L * 1024L; // ~397 MB
    public static final long MIN_REQUIRED_FREE_STORAGE_BYTES = 1024L * 1024L * 1024L; // 1 GB free required

    // Official Hugging Face GGUF repository URL for Qwen 0.6B / 0.5B Q4_K_M
    public static final String DEFAULT_MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";

    public interface DownloadListener {
        void onDownloadStarted(long totalBytes);
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onDownloadCompleted(@NonNull File modelFile);
        void onDownloadCancelled();
        void onError(@NonNull String errorMessage);
    }

    private static volatile ModelManager instance;
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private Future<?> currentDownloadTask;
    private HttpURLConnection currentConnection;

    private ModelManager() {}

    public static ModelManager getInstance() {
        if (instance == null) {
            synchronized (ModelManager.class) {
                if (instance == null) {
                    instance = new ModelManager();
                }
            }
        }
        return instance;
    }

    @NonNull
    public File getModelDirectory(@NonNull Context context) {
        File dir = context.getExternalFilesDir("models");
        if (dir == null) {
            dir = new File(context.getFilesDir(), "models");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public File getModelFile(@NonNull Context context) {
        return new File(getModelDirectory(context), MODEL_FILE_NAME);
    }

    public boolean isModelDownloaded(@NonNull Context context) {
        File file = getModelFile(context);
        return file.exists() && file.length() > 1024L * 1024L; // Minimum 1MB for valid GGUF header
    }

    public long getDownloadedFileSize(@NonNull Context context) {
        File file = getModelFile(context);
        return file.exists() ? file.length() : 0L;
    }

    public long getAvailableStorageBytes(@NonNull Context context) {
        try {
            File path = getModelDirectory(context);
            StatFs stat = new StatFs(path.getPath());
            return stat.getAvailableBytes();
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage: " + e.getMessage());
            return 0L;
        }
    }

    public boolean hasEnoughStorage(@NonNull Context context) {
        return getAvailableStorageBytes(context) >= MIN_REQUIRED_FREE_STORAGE_BYTES;
    }

    public boolean isDownloading() {
        return isDownloading.get();
    }

    /**
     * Downloads the model with progress, storage validation, and cancellation.
     */
    public void startDownload(@NonNull Context context, @Nullable String modelUrl, @NonNull DownloadListener listener) {
        if (isDownloading.get()) {
            listener.onError("A download is already in progress.");
            return;
        }

        if (!hasEnoughStorage(context)) {
            listener.onError("Insufficient storage space. Smart Expenses Tracker requires at least 1 GB of free space to install the AI model.");
            return;
        }

        final String urlToUse = (modelUrl != null && !modelUrl.trim().isEmpty()) ? modelUrl : DEFAULT_MODEL_URL;
        final File destinationFile = getModelFile(context);
        final File tempFile = new File(destinationFile.getAbsolutePath() + ".part");

        isDownloading.set(true);

        currentDownloadTask = downloadExecutor.submit(() -> {
            InputStream in = null;
            FileOutputStream out = null;
            try {
                URL url = new URL(urlToUse);
                currentConnection = (HttpURLConnection) url.openConnection();
                currentConnection.setConnectTimeout(15000);
                currentConnection.setReadTimeout(30000);

                long existingBytes = 0;
                if (tempFile.exists()) {
                    existingBytes = tempFile.length();
                    currentConnection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                }

                currentConnection.connect();
                int responseCode = currentConnection.getResponseCode();

                long totalBytes;
                if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    totalBytes = existingBytes + currentConnection.getContentLengthLong();
                    out = new FileOutputStream(tempFile, true);
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    totalBytes = currentConnection.getContentLengthLong();
                    out = new FileOutputStream(tempFile, false);
                    existingBytes = 0;
                } else {
                    throw new IOException("Server responded with HTTP " + responseCode);
                }

                final long finalTotalBytes = totalBytes > 0 ? totalBytes : EXPECTED_MODEL_SIZE_BYTES;
                mainHandler.post(() -> listener.onDownloadStarted(finalTotalBytes));

                in = currentConnection.getInputStream();
                byte[] buffer = new byte[64 * 1024]; // 64KB buffer
                long bytesDownloaded = existingBytes;
                int read;
                int lastReportedPercent = -1;

                while ((read = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Download cancelled by user.");
                    }

                    out.write(buffer, 0, read);
                    bytesDownloaded += read;

                    int percent = (int) ((bytesDownloaded * 100) / finalTotalBytes);
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent;
                        final long curBytes = bytesDownloaded;
                        mainHandler.post(() -> listener.onProgress(percent, curBytes, finalTotalBytes));
                    }
                }

                out.flush();
                out.close();
                out = null;

                // Rename temp file to final destination
                if (destinationFile.exists()) {
                    destinationFile.delete();
                }
                if (!tempFile.renameTo(destinationFile)) {
                    throw new IOException("Failed to rename temporary download file.");
                }

                isDownloading.set(false);
                mainHandler.post(() -> listener.onDownloadCompleted(destinationFile));

            } catch (InterruptedException e) {
                isDownloading.set(false);
                mainHandler.post(listener::onDownloadCancelled);
            } catch (Exception e) {
                isDownloading.set(false);
                Log.e(TAG, "Download failed: " + e.getMessage(), e);
                mainHandler.post(() -> listener.onError("Download error: " + e.getMessage()));
            } finally {
                if (in != null) {
                    try { in.close(); } catch (IOException ignored) {}
                }
                if (out != null) {
                    try { out.close(); } catch (IOException ignored) {}
                }
                if (currentConnection != null) {
                    currentConnection.disconnect();
                    currentConnection = null;
                }
            }
        });
    }

    public void cancelDownload() {
        if (isDownloading.get() && currentDownloadTask != null) {
            currentDownloadTask.cancel(true);
            if (currentConnection != null) {
                new Thread(() -> {
                    try { currentConnection.disconnect(); } catch (Exception ignored) {}
                }).start();
            }
            isDownloading.set(false);
        }
    }

    public boolean deleteModel(@NonNull Context context) {
        cancelDownload();
        File modelFile = getModelFile(context);
        File tempFile = new File(modelFile.getAbsolutePath() + ".part");
        boolean deleted = true;
        if (modelFile.exists()) {
            deleted = modelFile.delete();
        }
        if (tempFile.exists()) {
            tempFile.delete();
        }
        return deleted;
    }

    /**
     * Verifies GGUF magic bytes (0x46554747 = "GGUF") in the file header.
     */
    public boolean verifyModelIntegrity(@NonNull Context context) {
        File file = getModelFile(context);
        if (!file.exists() || file.length() < 16) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = fis.read(magic);
            if (read == 4) {
                // GGUF magic is 'G', 'G', 'U', 'F' (0x47, 0x47, 0x55, 0x46 in ASCII)
                return magic[0] == 'G' && magic[1] == 'G' && magic[2] == 'U' && magic[3] == 'F';
            }
        } catch (Exception e) {
            Log.e(TAG, "Integrity check failed: " + e.getMessage());
        }
        return false;
    }
}
