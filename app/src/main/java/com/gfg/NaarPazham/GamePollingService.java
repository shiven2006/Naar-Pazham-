package com.gfg.NaarPazham;

import android.os.Handler;
import android.os.Looper;

public class GamePollingService implements NetworkService.StatusCallback {
    private static final int POLLING_INTERVAL_MS = 2000; // Poll every 2 seconds
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final NetworkService networkService;
    private final Handler handler;
    private final String gameId;
    private final PollingCallback callback;

    private Runnable pollingRunnable;
    private boolean isPolling = false;
    private int consecutiveFailures = 0;
    private long lastKnownTotalMoves = -1;

    // Track previous state to detect changes
    private String lastGameStatus = null;
    private Integer lastCurrentPlayer = null;
    private String lastWinner = null;

    public interface PollingCallback {
        void onGameStateUpdated(ServerGameState gameState);
        void onPollingError(String error);
        void onOpponentMove(ServerGameState gameState);
        void onGameEnded(ServerGameState gameState);
        void onPlayerJoined(); // New callback for when second player joins
    }

    public GamePollingService(String gameId, NetworkService networkService, PollingCallback callback) {
        this.gameId = gameId;
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
            System.out.println("Starting polling for game: " + gameId);
            handler.post(pollingRunnable);
        }
    }

    public void stopPolling() {
        isPolling = false;
        handler.removeCallbacks(pollingRunnable);
        System.out.println("Stopped polling for game: " + gameId);
    }

    public boolean isPolling() {
        return isPolling;
    }

    private void pollGameStatus() {
        // First get the game status (lighter endpoint)
        networkService.getGameStatus(gameId, this);
    }

    @Override
    public void onStatusUpdate(String gameStatus, int currentPlayer, String winner) {
        consecutiveFailures = 0;

        // Check if any significant state has changed
        boolean statusChanged = !gameStatus.equals(lastGameStatus);
        boolean playerChanged = lastCurrentPlayer == null || lastCurrentPlayer != currentPlayer;
        boolean winnerChanged = !((winner == null && lastWinner == null) ||
                (winner != null && winner.equals(lastWinner)));

        // Update last known state
        lastGameStatus = gameStatus;
        lastCurrentPlayer = currentPlayer;
        lastWinner = winner;

        // If significant changes occurred, get full game state
        if (statusChanged || playerChanged || winnerChanged) {
            System.out.println("Status change detected - fetching full game state");
            networkService.getGameState(gameId, new NetworkService.GameCallback() {
                @Override
                public void onSuccess(ServerGameState gameState) {
                    handleGameStateUpdate(gameState, statusChanged, playerChanged, winnerChanged);
                    scheduleNextPoll();
                }

                @Override
                public void onFailure(String errorMessage) {
                    handlePollingError(errorMessage);
                }
            });
        } else {
            // No significant changes, just schedule next poll
            scheduleNextPoll();
        }
    }

    @Override
    public void onFailure(String errorMessage) {
        handlePollingError(errorMessage);
    }

    private void handleGameStateUpdate(ServerGameState gameState, boolean statusChanged,
                                       boolean playerChanged, boolean winnerChanged) {

        // Check for specific events
        if (statusChanged && "ACTIVE".equals(gameState.getGameStatus()) &&
                gameState.isPlayer1Assigned() && gameState.isPlayer2Assigned()) {
            // Second player joined
            callback.onPlayerJoined();
        }

        if (winnerChanged && gameState.getWinner() != null) {
            // Game ended
            callback.onGameEnded(gameState);
            stopPolling(); // Stop polling when game ends
            return;
        }

        if (playerChanged && gameState.getTotalMoves() > lastKnownTotalMoves) {
            // Opponent made a move
            lastKnownTotalMoves = gameState.getTotalMoves();
            callback.onOpponentMove(gameState);
        } else {
            // General state update
            callback.onGameStateUpdated(gameState);
        }

        // Update our tracking
        if (gameState.getTotalMoves() > lastKnownTotalMoves) {
            lastKnownTotalMoves = gameState.getTotalMoves();
        }
    }

    private void handlePollingError(String errorMessage) {
        consecutiveFailures++;

        System.err.println("Polling error (" + consecutiveFailures + "/" +
                MAX_CONSECUTIVE_FAILURES + "): " + errorMessage);

        // Stop polling if too many consecutive failures
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            stopPolling();
            callback.onPollingError("Stopped polling due to repeated failures. Check your connection.");
            return;
        }

        // Retry with exponential backoff
        scheduleNextPoll(POLLING_INTERVAL_MS * consecutiveFailures);
    }

    private void scheduleNextPoll() {
        scheduleNextPoll(POLLING_INTERVAL_MS);
    }

    private void scheduleNextPoll(int delay) {
        if (isPolling) {
            handler.postDelayed(pollingRunnable, delay);
        }
    }

    public void setLastKnownTotalMoves(long totalMoves) {
        this.lastKnownTotalMoves = totalMoves;
    }

    public void reset() {
        lastKnownTotalMoves = -1;
        lastGameStatus = null;
        lastCurrentPlayer = null;
        lastWinner = null;
        consecutiveFailures = 0;
    }
}