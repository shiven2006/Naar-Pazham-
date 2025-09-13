package com.gfg.NaarPazham;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Step 7: Complete QueueManager Class
 * Manages all matchmaking queue operations and state
 */
public class QueueManager {
    private static final String TAG = "QueueManager";
    private static final int QUEUE_UPDATE_INTERVAL = 2000; // 2 seconds
    private static final int MAX_QUEUE_TIME = 300000; // 5 minutes
    private static final int QUEUE_STATUS_CHECK_INTERVAL = 3000; // 3 seconds

    // Persistence keys
    private static final String PREFS_NAME = "queue_manager_prefs";
    private static final String KEY_QUEUE_STATE = "queue_state";
    private static final String KEY_PLAYER_ID = "queue_player_id";
    private static final String KEY_DEVICE_ID = "queue_device_id";
    private static final String KEY_QUEUE_START_TIME = "queue_start_time";

    private QueueState currentState = QueueState.IDLE;
    private Handler queueHandler;
    private Handler uiUpdateHandler;
    private Runnable queueUpdateTask;
    private Runnable uiUpdateTask;
    private Runnable statusCheckTask;
    private QueueCallback callback;
    private NetworkService networkService;
    private Context context;

    private String playerId;
    private String deviceId;
    private long queueStartTime;
    private int consecutiveFailures = 0;
    private int currentQueueSize = -1;
    private String lastKnownStatus = "";

    // Queue statistics
    private long totalTimeInQueue = 0;
    private int estimatedWaitTime = -1;
    private boolean isPersistenceEnabled = true;

    public interface QueueCallback {
        void onQueueStateChanged(QueueState newState, QueueInfo info);
        void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId);
        void onQueueError(String error);
        void onQueueTimeout();
        void onQueuePositionUpdated(int position, int estimatedWait);
    }

    public static class QueueInfo {
        public final long timeInQueue;
        public final int estimatedWait;
        public final int queueSize;
        public final String status;
        public final QueueState state;
        public final boolean isConnected;

        public QueueInfo(long timeInQueue, int estimatedWait, int queueSize,
                         String status, QueueState state, boolean isConnected) {
            this.timeInQueue = timeInQueue;
            this.estimatedWait = estimatedWait;
            this.queueSize = queueSize;
            this.status = status;
            this.state = state;
            this.isConnected = isConnected;
        }

        public QueueInfo(long timeInQueue, String status, QueueState state) {
            this(timeInQueue, -1, -1, status, state, true);
        }
    }

    public QueueManager(Context context, NetworkService networkService, QueueCallback callback) {
        this.context = context.getApplicationContext();
        this.networkService = networkService;
        this.callback = callback;
        this.queueHandler = new Handler(Looper.getMainLooper());
        this.uiUpdateHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "QueueManager initialized");
    }

    /**
     * Join the matchmaking queue
     */
    public void joinQueue(String playerId, String deviceId) {
        if (currentState != QueueState.IDLE) {
            Log.w(TAG, "Already in queue state: " + currentState);
            notifyError("Already in matchmaking process");
            return;
        }

        this.playerId = playerId;
        this.deviceId = deviceId;
        this.queueStartTime = System.currentTimeMillis();
        this.consecutiveFailures = 0;

        Log.d(TAG, "Joining queue - Player: " + playerId + ", Device: " + deviceId);

        changeState(QueueState.JOINING);
        saveQueueState(); // Persist immediately

        networkService.findMatchWithDeviceId(playerId, deviceId, new NetworkService.MatchmakingCallback() {
            @Override
            public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                Log.i(TAG, "Immediate match found!");
                changeState(QueueState.MATCH_FOUND);
                clearQueueState(); // Clear persistence
                stopAllTasks();
                callback.onMatchFound(gameState, isPlayer1, playerId);
            }

            @Override
            public void onWaitingForMatch(String playerId) {
                Log.d(TAG, "Entered queue, starting monitoring");
                changeState(QueueState.IN_QUEUE);
                startQueueMonitoring();
                saveQueueState(); // Update persistence
            }

            @Override
            public void onAlreadyInQueue() {
                Log.d(TAG, "Already in queue, resuming monitoring");
                changeState(QueueState.IN_QUEUE);
                startQueueMonitoring();
                saveQueueState(); // Update persistence
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to join queue: " + errorMessage);
                changeState(QueueState.IDLE);
                clearQueueState(); // Clear failed attempt
                notifyError("Failed to join matchmaking: " + errorMessage);
            }
        });
    }

    /**
     * Leave the matchmaking queue
     */
    public void leaveQueue() {
        if (currentState == QueueState.IDLE) {
            Log.d(TAG, "Already idle, nothing to leave");
            return;
        }

        Log.d(TAG, "Leaving queue from state: " + currentState);

        changeState(QueueState.LEAVING_QUEUE);
        stopAllTasks();

        if (playerId != null) {
            networkService.cancelMatchmaking(playerId, new NetworkService.GameCallback() {
                @Override
                public void onSuccess(ServerGameState gameState) {
                    Log.d(TAG, "Successfully left queue");
                    changeState(QueueState.IDLE);
                    clearQueueState();
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.w(TAG, "Cancel request failed: " + errorMessage);
                    // Still consider it left - server might have already processed
                    changeState(QueueState.IDLE);
                    clearQueueState();
                }
            });
        } else {
            changeState(QueueState.IDLE);
            clearQueueState();
        }
    }

    /**
     * Resume queue from saved state (for persistence)
     */
    public boolean resumeQueueIfSaved() {
        if (!isPersistenceEnabled) return false;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedState = prefs.getString(KEY_QUEUE_STATE, QueueState.IDLE.name());
            String savedPlayerId = prefs.getString(KEY_PLAYER_ID, null);
            String savedDeviceId = prefs.getString(KEY_DEVICE_ID, null);
            long savedStartTime = prefs.getLong(KEY_QUEUE_START_TIME, 0);

            if (QueueState.valueOf(savedState) == QueueState.IN_QUEUE &&
                    savedPlayerId != null && savedDeviceId != null && savedStartTime > 0) {

                // Check if saved queue is still recent (within 10 minutes)
                long timeSinceSave = System.currentTimeMillis() - savedStartTime;
                if (timeSinceSave < 600000) { // 10 minutes

                    Log.d(TAG, "Resuming saved queue state");
                    this.playerId = savedPlayerId;
                    this.deviceId = savedDeviceId;
                    this.queueStartTime = savedStartTime;

                    changeState(QueueState.IN_QUEUE);
                    startQueueMonitoring();

                    return true;
                } else {
                    Log.d(TAG, "Saved queue too old, clearing");
                    clearQueueState();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resuming queue state", e);
            clearQueueState();
        }

        return false;
    }

    /**
     * Start queue monitoring (polling and UI updates)
     */
    private void startQueueMonitoring() {
        stopAllTasks(); // Ensure no duplicate tasks
        startQueueStatusChecking();
        startUIUpdates();
        startTimeoutMonitoring();
    }

    /**
     * Start periodic queue status checking
     */
    private void startQueueStatusChecking() {
        if (statusCheckTask != null) {
            queueHandler.removeCallbacks(statusCheckTask);
        }

        statusCheckTask = new Runnable() {
            @Override
            public void run() {
                if (currentState == QueueState.IN_QUEUE && playerId != null) {
                    checkQueueStatus();

                    if (currentState == QueueState.IN_QUEUE) {
                        // Schedule next check with progressive backoff on failures
                        long delay = Math.min(QUEUE_STATUS_CHECK_INTERVAL * (1 + consecutiveFailures), 15000);
                        queueHandler.postDelayed(this, delay);
                    }
                }
            }
        };

        queueHandler.post(statusCheckTask);
    }

    /**
     * Check queue status with server
     */
    private void checkQueueStatus() {
        // Check queue size
        networkService.getQueueStatus(new NetworkService.MatchmakingStatusCallback() {
            @Override
            public void onStatusUpdate(int queueSize) {
                currentQueueSize = queueSize;
                estimatedWaitTime = calculateEstimatedWait(queueSize);
                consecutiveFailures = 0; // Reset on success

                callback.onQueuePositionUpdated(queueSize, estimatedWaitTime);

                Log.d(TAG, "Queue status - Size: " + queueSize + ", Estimated wait: " + estimatedWaitTime);
            }

            @Override
            public void onFailure(String errorMessage) {
                consecutiveFailures++;
                Log.w(TAG, "Queue status check failed (attempt " + consecutiveFailures + "): " + errorMessage);

                if (consecutiveFailures >= 5) {
                    Log.e(TAG, "Too many queue status failures, treating as disconnected");
                    // Don't leave queue, just mark as connection issues
                    lastKnownStatus = "Connection issues - retrying...";
                }
            }
        });

        // Check for match
        networkService.getMatchmakingStatus(playerId, new NetworkService.MatchmakingCallback() {
            @Override
            public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                Log.i(TAG, "Match found during status check!");
                changeState(QueueState.MATCH_FOUND);
                clearQueueState();
                stopAllTasks();
                callback.onMatchFound(gameState, isPlayer1, playerId);
            }

            @Override
            public void onWaitingForMatch(String playerId) {
                lastKnownStatus = "Searching for opponent...";
                consecutiveFailures = 0;
            }

            @Override
            public void onAlreadyInQueue() {
                lastKnownStatus = "In matchmaking queue...";
                consecutiveFailures = 0;
            }

            @Override
            public void onFailure(String errorMessage) {
                consecutiveFailures++;
                Log.w(TAG, "Match status check failed: " + errorMessage);
            }
        });
    }

    /**
     * Start UI update timer
     */
    private void startUIUpdates() {
        if (uiUpdateTask != null) {
            uiUpdateHandler.removeCallbacks(uiUpdateTask);
        }

        uiUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (currentState == QueueState.IN_QUEUE) {
                    updateUI();
                    uiUpdateHandler.postDelayed(this, 1000); // Update UI every second
                }
            }
        };

        uiUpdateHandler.post(uiUpdateTask);
    }

    /**
     * Start timeout monitoring
     */
    private void startTimeoutMonitoring() {
        queueHandler.postDelayed(() -> {
            if (currentState == QueueState.IN_QUEUE) {
                Log.w(TAG, "Queue timeout reached");
                callback.onQueueTimeout();
                leaveQueue();
            }
        }, MAX_QUEUE_TIME);
    }

    /**
     * Update UI with current queue information
     */
    private void updateUI() {
        long timeInQueue = System.currentTimeMillis() - queueStartTime;
        boolean isConnected = consecutiveFailures < 3;

        String status = lastKnownStatus;
        if (!isConnected) {
            status = "Connection issues - retrying...";
        } else if (status == null || status.isEmpty()) {
            status = "Searching for opponent...";
        }

        QueueInfo info = new QueueInfo(
                timeInQueue,
                estimatedWaitTime,
                currentQueueSize,
                status,
                currentState,
                isConnected
        );

        callback.onQueueStateChanged(currentState, info);
    }

    /**
     * Calculate estimated wait time based on queue size
     */
    private int calculateEstimatedWait(int queueSize) {
        if (queueSize <= 1) return 30; // 30 seconds if alone or first

        // Algorithm: assume average match time of 60 seconds
        // Position in queue = queueSize / 2 (rough estimate)
        // Wait time = position * average_match_time
        int averageMatchTime = 60;
        int position = Math.max(1, queueSize / 2);

        return Math.min(position * averageMatchTime, 300); // Cap at 5 minutes
    }

    /**
     * Change queue state and notify callback
     */
    private void changeState(QueueState newState) {
        QueueState oldState = currentState;
        currentState = newState;

        Log.d(TAG, "Queue state changed: " + oldState + " -> " + newState);

        // Calculate current queue info
        long timeInQueue = queueStartTime > 0 ? System.currentTimeMillis() - queueStartTime : 0;
        String status = getStatusMessage(newState);

        QueueInfo info = new QueueInfo(timeInQueue, status, newState);
        callback.onQueueStateChanged(newState, info);

        // Update persistence
        if (isPersistenceEnabled) {
            saveQueueState();
        }
    }

    /**
     * Get status message for current state
     */
    private String getStatusMessage(QueueState state) {
        switch (state) {
            case IDLE: return "Ready to find match";
            case JOINING: return "Joining matchmaking queue...";
            case IN_QUEUE: return lastKnownStatus.isEmpty() ? "Searching for opponent..." : lastKnownStatus;
            case MATCH_FOUND: return "Match found! Loading game...";
            case IN_GAME: return "In game";
            case LEAVING_QUEUE: return "Leaving queue...";
            default: return "Unknown status";
        }
    }

    /**
     * Stop all background tasks
     */
    private void stopAllTasks() {
        if (statusCheckTask != null) {
            queueHandler.removeCallbacks(statusCheckTask);
            statusCheckTask = null;
        }

        if (uiUpdateTask != null) {
            uiUpdateHandler.removeCallbacks(uiUpdateTask);
            uiUpdateTask = null;
        }

        if (queueUpdateTask != null) {
            queueHandler.removeCallbacks(queueUpdateTask);
            queueUpdateTask = null;
        }

        // Remove all timeout callbacks
        queueHandler.removeCallbacksAndMessages(null);
        uiUpdateHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Save current queue state to persistence
     */
    private void saveQueueState() {
        if (!isPersistenceEnabled || context == null) return;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString(KEY_QUEUE_STATE, currentState.name());
            if (playerId != null) editor.putString(KEY_PLAYER_ID, playerId);
            if (deviceId != null) editor.putString(KEY_DEVICE_ID, deviceId);
            if (queueStartTime > 0) editor.putLong(KEY_QUEUE_START_TIME, queueStartTime);

            editor.apply();

            Log.d(TAG, "Queue state saved: " + currentState);
        } catch (Exception e) {
            Log.e(TAG, "Error saving queue state", e);
        }
    }

    /**
     * Clear saved queue state
     */
    private void clearQueueState() {
        if (!isPersistenceEnabled || context == null) return;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();

            Log.d(TAG, "Queue state cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing queue state", e);
        }
    }

    /**
     * Notify error to callback
     */
    private void notifyError(String error) {
        callback.onQueueError(error);
    }

    // Public getters and utility methods
    public QueueState getCurrentState() {
        return currentState;
    }

    public long getTimeInQueue() {
        return queueStartTime > 0 ? System.currentTimeMillis() - queueStartTime : 0;
    }

    public int getCurrentQueueSize() {
        return currentQueueSize;
    }

    public String getCurrentPlayerId() {
        return playerId;
    }

    public void setPersistenceEnabled(boolean enabled) {
        this.isPersistenceEnabled = enabled;
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up QueueManager");
        stopAllTasks();

        if (currentState != QueueState.IDLE && currentState != QueueState.IN_GAME) {
            clearQueueState();
        }

        currentState = QueueState.IDLE;
    }
}