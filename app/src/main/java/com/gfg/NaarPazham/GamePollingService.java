package com.gfg.NaarPazham;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class GamePollingService {
    private static final String TAG = "GamePollingService";
    private static final int POLLING_INTERVAL_MS = 2000; // Poll every 2 seconds
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final NetworkService networkService;
    private final Handler handler;
    private final String gameId;
    private final String playerId;
    private final PollingCallback callback;

    private Runnable pollingRunnable;
    private volatile boolean isPolling = false;
    private int consecutiveFailures = 0;
    private long lastKnownTotalMoves = -1;

    // Track previous state to detect changes
    private String lastGameStatus = null;
    private Integer lastCurrentPlayer = null;
    private String lastWinner = null;

    // FIXED: Enhanced callback interface
    public interface PollingCallback {
        void onGameStateUpdated(ServerGameState gameState);
        void onPollingError(String error);
        void onOpponentMove(ServerGameState gameState);
        void onGameEnded(ServerGameState gameState);
        void onPlayerJoined(); // Callback for when second player joins
    }

    public GamePollingService(String gameId, String playerId,
                              NetworkService networkService, PollingCallback callback) {
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        if (networkService == null) {
            throw new IllegalArgumentException("NetworkService cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("PollingCallback cannot be null");
        }

        this.gameId = gameId.trim();
        this.playerId = playerId.trim();
        this.networkService = networkService;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());

        this.pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPolling) {
                    pollGameStatus();
                }
            }
        };
    }

    public void startPolling() {
        if (!isPolling) {
            isPolling = true;
            consecutiveFailures = 0;
            Log.d(TAG, "Starting polling for game: " + gameId + " (Player: " + playerId + ")");
            handler.post(pollingRunnable);
        }
    }

    public void stopPolling() {
        isPolling = false;
        if (handler != null) {
            handler.removeCallbacks(pollingRunnable);
        }
        Log.d(TAG, "Stopped polling for game: " + gameId);
    }

    public boolean isPolling() {
        return isPolling;
    }

    // FIXED: Use the correct NetworkService method signature
    private void pollGameStatus() {
        if (!isPolling) {
            return;
        }

        try {
            // FIXED: Use getGameState instead of getGameStatus since NetworkService doesn't have getGameStatus(gameId, playerId, callback)
            networkService.getGameState(gameId, playerId, new NetworkService.GameCallback() {
                @Override
                public void onSuccess(ServerGameState gameState) {
                    if (isPolling) {
                        handleGameStateSuccess(gameState);
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    if (isPolling) {
                        handlePollingError(errorMessage);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in pollGameStatus", e);
            handlePollingError("Polling error: " + e.getMessage());
        }
    }

    // FIXED: New method to handle successful game state retrieval
    private void handleGameStateSuccess(ServerGameState gameState) {
        if (!isPolling) {
            Log.d(TAG, "Ignoring game state update - not polling");
            return;
        }

        consecutiveFailures = 0;

        // Extract current state info
        String currentGameStatus = gameState.getGameStatus();
        int currentPlayer = gameState.isPlayer1Turn() ? 1 : 2;
        String currentWinner = gameState.getWinner();

        // Check if any significant state has changed
        boolean statusChanged = !currentGameStatus.equals(lastGameStatus);
        boolean playerChanged = lastCurrentPlayer == null || lastCurrentPlayer != currentPlayer;
        boolean winnerChanged = !((currentWinner == null && lastWinner == null) ||
                (currentWinner != null && currentWinner.equals(lastWinner)));

        // Update last known state
        lastGameStatus = currentGameStatus;
        lastCurrentPlayer = currentPlayer;
        lastWinner = currentWinner;

        // Handle the game state update
        handleGameStateUpdate(gameState, statusChanged, playerChanged, winnerChanged);

        // Schedule next poll
        scheduleNextPoll();
    }

    private void handleGameStateUpdate(ServerGameState gameState, boolean statusChanged,
                                       boolean playerChanged, boolean winnerChanged) {
        if (!isPolling) {
            return;
        }

        try {
            // Check for specific events in priority order

            // 1. Game ended - highest priority
            if (winnerChanged && gameState.getWinner() != null) {
                Log.d(TAG, "Game ended - Winner: " + gameState.getWinner());
                callback.onGameEnded(gameState);
                stopPolling(); // Stop polling when game ends
                return;
            }

            // 2. Check if second player joined
            if (statusChanged && "ACTIVE".equals(gameState.getGameStatus()) &&
                    gameState.isPlayer1Assigned() && gameState.isPlayer2Assigned() &&
                    (lastGameStatus == null || !lastGameStatus.equals("ACTIVE"))) {
                Log.d(TAG, "Second player joined the game");
                callback.onPlayerJoined();
            }

            // 3. Check for opponent moves
            if (gameState.getTotalMoves() > lastKnownTotalMoves) {
                Log.d(TAG, "Move detected - Total moves: " + gameState.getTotalMoves() +
                        " (Previous: " + lastKnownTotalMoves + ")");
                lastKnownTotalMoves = gameState.getTotalMoves();

                // Determine if this was an opponent move
                // If it's currently our turn after the move count increased, opponent just moved
                boolean isOurTurn = (gameState.isPlayer1Turn() && playerId.equals(gameState.getPlayer1Id())) ||
                        (!gameState.isPlayer1Turn() && playerId.equals(gameState.getPlayer2Id()));

                if (isOurTurn && playerChanged) {
                    Log.d(TAG, "Opponent made a move - now it's our turn");
                    callback.onOpponentMove(gameState);
                } else {
                    callback.onGameStateUpdated(gameState);
                }
            } else if (statusChanged || playerChanged) {
                // 4. General state update (no new moves but other changes)
                Log.d(TAG, "General game state update - Status: " + gameState.getGameStatus() +
                        ", Current Player: " + (gameState.isPlayer1Turn() ? "1" : "2"));
                callback.onGameStateUpdated(gameState);
            }

            // Update our tracking
            if (gameState.getTotalMoves() > lastKnownTotalMoves) {
                lastKnownTotalMoves = gameState.getTotalMoves();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling game state update", e);
            handlePollingError("Error processing game state: " + e.getMessage());
        }
    }

    private void handlePollingError(String errorMessage) {
        consecutiveFailures++;

        Log.w(TAG, "Polling error (" + consecutiveFailures + "/" +
                MAX_CONSECUTIVE_FAILURES + "): " + errorMessage);

        // Stop polling if too many consecutive failures
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.e(TAG, "Stopping polling due to " + MAX_CONSECUTIVE_FAILURES + " consecutive failures");
            stopPolling();
            try {
                callback.onPollingError("Stopped polling due to repeated failures. Check your connection.");
            } catch (Exception e) {
                Log.e(TAG, "Error in polling error callback", e);
            }
            return;
        }

        // For non-fatal errors, just notify callback but continue polling
        try {
            // Don't stop polling for single errors, just notify
            if (consecutiveFailures == 1) {
                callback.onPollingError("Connection issue: " + errorMessage + ". Retrying...");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in polling error callback", e);
        }

        // Retry with exponential backoff
        scheduleNextPoll(POLLING_INTERVAL_MS * consecutiveFailures);
    }

    private void scheduleNextPoll() {
        scheduleNextPoll(POLLING_INTERVAL_MS);
    }

    private void scheduleNextPoll(int delay) {
        if (isPolling && handler != null) {
            handler.postDelayed(pollingRunnable, delay);
        }
    }

    // FIXED: Added validation
    public void setLastKnownTotalMoves(long totalMoves) {
        if (totalMoves >= 0) {
            this.lastKnownTotalMoves = totalMoves;
            Log.d(TAG, "Updated last known total moves to: " + totalMoves);
        }
    }

    public void reset() {
        Log.d(TAG, "Resetting polling service state");
        lastKnownTotalMoves = -1;
        lastGameStatus = null;
        lastCurrentPlayer = null;
        lastWinner = null;
        consecutiveFailures = 0;
    }

    // FIXED: Added utility methods for better debugging and state management
    public long getLastKnownTotalMoves() {
        return lastKnownTotalMoves;
    }

    public String getGameId() {
        return gameId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    // FIXED: Added cleanup method
    public void cleanup() {
        Log.d(TAG, "Cleaning up polling service");
        stopPolling();
        // Clear any remaining callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}