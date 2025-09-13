package com.gfg.NaarPazham;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Step 9: Queue Persistence Manager
 * Handles saving and restoring queue state across app restarts
 */
public class QueuePersistenceManager {
    private static final String TAG = "QueuePersistenceManager";
    private static final String PREFS_NAME = "multiplayer_queue_persistence";

    // Persistence keys
    private static final String KEY_QUEUE_STATE = "queue_state";
    private static final String KEY_PLAYER_ID = "player_id";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_QUEUE_START_TIME = "queue_start_time";
    private static final String KEY_QUEUE_SESSION_ID = "queue_session_id";
    private static final String KEY_LAST_SAVE_TIME = "last_save_time";
    private static final String KEY_TOTAL_QUEUE_TIME = "total_queue_time";
    private static final String KEY_QUEUE_ATTEMPTS = "queue_attempts";
    private static final String KEY_LAST_KNOWN_POSITION = "last_known_position";
    private static final String KEY_APP_VERSION = "app_version";

    // Configuration
    private static final long MAX_PERSISTENCE_AGE = 600000; // 10 minutes
    private static final long MIN_QUEUE_TIME_TO_PERSIST = 5000; // 5 seconds
    private static final String CURRENT_VERSION = "1.0"; // Update when queue format changes

    private Context context;
    private SharedPreferences prefs;
    private String currentSessionId;

    public static class PersistedQueueData {
        public final QueueState state;
        public final String playerId;
        public final String deviceId;
        public final long queueStartTime;
        public final long totalQueueTime;
        public final int queueAttempts;
        public final int lastKnownPosition;
        public final String sessionId;
        public final boolean isValid;
        public final String invalidReason;

        public PersistedQueueData(QueueState state, String playerId, String deviceId,
                                  long queueStartTime, long totalQueueTime, int queueAttempts,
                                  int lastKnownPosition, String sessionId,
                                  boolean isValid, String invalidReason) {
            this.state = state;
            this.playerId = playerId;
            this.deviceId = deviceId;
            this.queueStartTime = queueStartTime;
            this.totalQueueTime = totalQueueTime;
            this.queueAttempts = queueAttempts;
            this.lastKnownPosition = lastKnownPosition;
            this.sessionId = sessionId;
            this.isValid = isValid;
            this.invalidReason = invalidReason;
        }

        public static PersistedQueueData invalid(String reason) {
            return new PersistedQueueData(QueueState.IDLE, null, null, 0, 0, 0, -1,
                    null, false, reason);
        }

        public long getTimeSinceStart() {
            return queueStartTime > 0 ? System.currentTimeMillis() - queueStartTime : 0;
        }

        public boolean isRecent() {
            return getTimeSinceStart() < MAX_PERSISTENCE_AGE;
        }
    }

    public QueuePersistenceManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.currentSessionId = generateSessionId();

        Log.d(TAG, "QueuePersistenceManager initialized with session: " + currentSessionId);
    }

    /**
     * Save current queue state to persistent storage
     */
    public void saveQueueState(QueueState state, String playerId, String deviceId,
                               long queueStartTime, long totalQueueTime,
                               int queueAttempts, int lastKnownPosition) {

        // Only persist if queue has been active for meaningful time
        long currentQueueTime = queueStartTime > 0 ?
                System.currentTimeMillis() - queueStartTime : 0;

        if (state == QueueState.IN_QUEUE && currentQueueTime < MIN_QUEUE_TIME_TO_PERSIST) {
            Log.d(TAG, "Queue time too short to persist: " + currentQueueTime + "ms");
            return;
        }

        try {
            SharedPreferences.Editor editor = prefs.edit();

            // Core queue data
            editor.putString(KEY_QUEUE_STATE, state.name());
            if (playerId != null) editor.putString(KEY_PLAYER_ID, playerId);
            if (deviceId != null) editor.putString(KEY_DEVICE_ID, deviceId);
            editor.putLong(KEY_QUEUE_START_TIME, queueStartTime);
            editor.putLong(KEY_TOTAL_QUEUE_TIME, totalQueueTime);
            editor.putInt(KEY_QUEUE_ATTEMPTS, queueAttempts);
            editor.putInt(KEY_LAST_KNOWN_POSITION, lastKnownPosition);

            // Session and metadata
            editor.putString(KEY_QUEUE_SESSION_ID, currentSessionId);
            editor.putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis());
            editor.putString(KEY_APP_VERSION, CURRENT_VERSION);

            editor.apply();

            Log.d(TAG, "Queue state saved - State: " + state +
                    ", Player: " + (playerId != null ? playerId.substring(Math.max(0, playerId.length() - 8)) : "null") +
                    ", Time: " + currentQueueTime + "ms");

        } catch (Exception e) {
            Log.e(TAG, "Error saving queue state", e);
        }
    }

    /**
     * Load persisted queue state
     */
    public PersistedQueueData loadQueueState() {
        try {
            // Check if we have saved data
            if (!prefs.contains(KEY_QUEUE_STATE)) {
                return PersistedQueueData.invalid("No saved queue state");
            }

            // Load basic data
            String stateStr = prefs.getString(KEY_QUEUE_STATE, QueueState.IDLE.name());
            String playerId = prefs.getString(KEY_PLAYER_ID, null);
            String deviceId = prefs.getString(KEY_DEVICE_ID, null);
            long queueStartTime = prefs.getLong(KEY_QUEUE_START_TIME, 0);
            long totalQueueTime = prefs.getLong(KEY_TOTAL_QUEUE_TIME, 0);
            int queueAttempts = prefs.getInt(KEY_QUEUE_ATTEMPTS, 0);
            int lastKnownPosition = prefs.getInt(KEY_LAST_KNOWN_POSITION, -1);
            String sessionId = prefs.getString(KEY_QUEUE_SESSION_ID, null);
            long lastSaveTime = prefs.getLong(KEY_LAST_SAVE_TIME, 0);
            String savedVersion = prefs.getString(KEY_APP_VERSION, "0.0");

            // Parse state
            QueueState state;
            try {
                state = QueueState.valueOf(stateStr);
            } catch (IllegalArgumentException e) {
                return PersistedQueueData.invalid("Invalid queue state: " + stateStr);
            }

            // Validation checks
            if (!CURRENT_VERSION.equals(savedVersion)) {
                Log.w(TAG, "Queue data from different app version: " + savedVersion + " vs " + CURRENT_VERSION);
                return PersistedQueueData.invalid("App version mismatch");
            }

            if (playerId == null || deviceId == null) {
                return PersistedQueueData.invalid("Missing player or device ID");
            }

            if (queueStartTime <= 0) {
                return PersistedQueueData.invalid("Invalid queue start time");
            }

            // Age check
            long timeSinceLastSave = System.currentTimeMillis() - lastSaveTime;
            if (timeSinceLastSave > MAX_PERSISTENCE_AGE) {
                return PersistedQueueData.invalid("Saved data too old: " + timeSinceLastSave + "ms");
            }

            // State-specific validation
            if (state != QueueState.IN_QUEUE) {
                return PersistedQueueData.invalid("Only IN_QUEUE state can be restored");
            }

            // Create valid data object
            PersistedQueueData data = new PersistedQueueData(
                    state, playerId, deviceId, queueStartTime, totalQueueTime,
                    queueAttempts, lastKnownPosition, sessionId, true, null
            );

            Log.d(TAG, "Loaded valid queue state - Player: " + playerId.substring(Math.max(0, playerId.length() - 8)) +
                    ", Age: " + data.getTimeSinceStart() + "ms" +
                    ", Position: " + lastKnownPosition);

            return data;

        } catch (Exception e) {
            Log.e(TAG, "Error loading queue state", e);
            return PersistedQueueData.invalid("Error loading: " + e.getMessage());
        }
    }

    /**
     * Clear all persisted queue data
     */
    public void clearQueueState() {
        try {
            prefs.edit().clear().apply();
            Log.d(TAG, "Queue state cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing queue state", e);
        }
    }

    /**
     * Save queue statistics for analytics
     */
    public void saveQueueStatistics(long totalWaitTime, int totalAttempts,
                                    boolean matchFound, String endReason) {
        try {
            // Save to separate statistics storage for analytics
            SharedPreferences statsPrefs = context.getSharedPreferences(
                    PREFS_NAME + "_stats", Context.MODE_PRIVATE);

            JSONObject statsJson = new JSONObject();
            statsJson.put("timestamp", System.currentTimeMillis());
            statsJson.put("totalWaitTime", totalWaitTime);
            statsJson.put("totalAttempts", totalAttempts);
            statsJson.put("matchFound", matchFound);
            statsJson.put("endReason", endReason);
            statsJson.put("sessionId", currentSessionId);

            // Store latest stats
            statsPrefs.edit()
                    .putString("latest_session", statsJson.toString())
                    .putLong("last_queue_time", totalWaitTime)
                    .putBoolean("last_match_found", matchFound)
                    .apply();

            Log.d(TAG, "Queue statistics saved - Wait: " + totalWaitTime +
                    "ms, Attempts: " + totalAttempts + ", Found: " + matchFound);

        } catch (JSONException e) {
            Log.e(TAG, "Error saving queue statistics", e);
        }
    }

    /**
     * Get queue statistics for display
     */
    public JSONObject getQueueStatistics() {
        try {
            SharedPreferences statsPrefs = context.getSharedPreferences(
                    PREFS_NAME + "_stats", Context.MODE_PRIVATE);

            String latestStats = statsPrefs.getString("latest_session", null);
            if (latestStats != null) {
                return new JSONObject(latestStats);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading queue statistics", e);
        }

        return null;
    }

    /**
     * Check if app was restored from background during active queue
     */
    public boolean wasRestoredFromBackground() {
        PersistedQueueData data = loadQueueState();

        if (data.isValid && data.sessionId != null && !data.sessionId.equals(currentSessionId)) {
            // Different session ID means app was killed and restarted
            Log.d(TAG, "App was restored from background - Previous session: " +
                    data.sessionId + ", Current: " + currentSessionId);
            return true;
        }

        return false;
    }

    /**
     * Update persistence with current queue position
     */
    public void updateQueuePosition(int position, int estimatedWait) {
        if (!prefs.contains(KEY_QUEUE_STATE)) return;

        try {
            prefs.edit()
                    .putInt(KEY_LAST_KNOWN_POSITION, position)
                    .putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "Queue position updated: " + position + " (estimated wait: " + estimatedWait + "s)");
        } catch (Exception e) {
            Log.e(TAG, "Error updating queue position", e);
        }
    }

    /**
     * Increment queue attempt counter
     */
    public void incrementQueueAttempts() {
        try {
            int attempts = prefs.getInt(KEY_QUEUE_ATTEMPTS, 0) + 1;
            prefs.edit()
                    .putInt(KEY_QUEUE_ATTEMPTS, attempts)
                    .putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "Queue attempts incremented to: " + attempts);
        } catch (Exception e) {
            Log.e(TAG, "Error incrementing queue attempts", e);
        }
    }

    /**
     * Generate unique session ID for this app launch
     */
    private String generateSessionId() {
        return "SESSION_" + System.currentTimeMillis() + "_" +
                (int)(Math.random() * 1000);
    }

    /**
     * Check if persistence is healthy (not corrupted)
     */
    public boolean isPersistenceHealthy() {
        try {
            // Try to read and validate basic data
            loadQueueState();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Persistence health check failed", e);
            return false;
        }
    }

    /**
     * Reset persistence if corrupted
     */
    public void resetPersistenceIfCorrupted() {
        if (!isPersistenceHealthy()) {
            Log.w(TAG, "Persistence corrupted, resetting");
            clearQueueState();
        }
    }

    /**
     * Get debug information about current persistence state
     */
    public String getDebugInfo() {
        try {
            StringBuilder debug = new StringBuilder();
            debug.append("Persistence Debug Info:\n");
            debug.append("Session ID: ").append(currentSessionId).append("\n");
            debug.append("Has saved data: ").append(prefs.contains(KEY_QUEUE_STATE)).append("\n");

            if (prefs.contains(KEY_QUEUE_STATE)) {
                debug.append("State: ").append(prefs.getString(KEY_QUEUE_STATE, "null")).append("\n");
                debug.append("Last save: ").append(prefs.getLong(KEY_LAST_SAVE_TIME, 0)).append("\n");
                debug.append("Version: ").append(prefs.getString(KEY_APP_VERSION, "null")).append("\n");
            }

            return debug.toString();
        } catch (Exception e) {
            return "Error getting debug info: " + e.getMessage();
        }
    }
}