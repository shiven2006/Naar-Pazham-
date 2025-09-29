package com.gfg.NaarPazham;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.UUID;

enum QueueState {
    IDLE,           // Not in queue
    JOINING,        // Attempting to join queue
    IN_QUEUE,       // Waiting for match
    MATCH_FOUND,    // Match found, loading game
    IN_GAME,        // Currently playing
    LEAVING_QUEUE   // Attempting to leave queue
}

enum GameMode {
    LOCAL,      // Local single-device multiplayer
    ONLINE      // Online matchmaking
}

public class MainActivity extends AppCompatActivity implements UIUpdateListener, GameStateListener,
        QueueManager.QueueCallback, LocalGameManager.LocalGameCallback {
    private static final String TAG = "MainActivity";

    // ===== ENHANCED COMPONENTS =====
    private QueueManager queueManager;
    private NetworkRetryManager retryManager;
    private QueuePersistenceManager persistenceManager;

    // ===== EXISTING COMPONENTS =====
    private GameView gameView;
    private TextView statusText;
    private TextView gameIdText;
    private TextView playerRoleText;
    private TextView queueStatusText;
    private Button resetButton;
    private Button findMatchButton;
    private Button cancelMatchButton;
    private Button rulesButton;
    private OnBackPressedCallback backPressedCallback;
    private AlertDialog currentGameOverDialog = null;
    private boolean gameOverDialogShowing = false;

    // ===== ENHANCED QUEUE UI COMPONENTS =====
    private LinearLayout queueInfoPanel;
    private TextView queueStatusHeader;
    private ProgressBar queueProgress;
    private TextView queueTimeText;
    private TextView queuePositionText;
    private TextView queueStatusDetail;
    private TextView estimatedWaitText;
    private Button cancelQueueButton;

    // ===== GAME STATE =====
    private String currentGameId = null;
    private String currentPlayerId = null;
    private String currentDeviceId = null;

    // ===== LEGACY SUPPORT =====
    private boolean isInMatchmakingQueue = false; // For backward compatibility

    // ===== RESOURCE MANAGEMENT =====
    private HandlerThread matchmakingThread;
    private Handler matchmakingHandler;
    private NetworkService networkService;
    private boolean isActivityDestroyed = false;
    private boolean isActivityFinishing = false;

    // ===== QUEUE STATISTICS =====
    private int queueAttempts = 0;
    private long totalQueueTime = 0;

    // ===== LOCAL MODE COMPONENTS =====
    private GameMode currentGameMode = GameMode.LOCAL; // Default to local mode
    private LocalGameManager localGameManager;

    // ===== MODE UI COMPONENTS =====
    private TextView currentModeText;
    private Button switchModeButton;

    public MainActivity(NetworkService networkService) {
        this.networkService = networkService;
    }

    public MainActivity() {
        super();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Force fresh ID generation for clean slate
        DeviceIdGenerator.clearDeviceId();
        PlayerIdGenerator.initialize(this);
        DeviceIdGenerator.initialize(this);

        Log.d(TAG, "Forced fresh ID generation");

        // Initialize NetworkService
        if (networkService == null) {
            networkService = new NetworkService();
        }
        networkService.initialize(this);

        try {
            // Initialize enhanced components in order
            persistenceManager = new QueuePersistenceManager(this);
            retryManager = NetworkRetryManager.forMatchmaking();
            queueManager = new QueueManager(this, networkService, this);

            initializeHandlers();
            initializeViews();

            gameView.post(() -> {
                if (localGameManager == null && currentGameMode == GameMode.LOCAL) {
                    if (gameView.getGameState() != null && gameView.getBoard() != null) {
                        localGameManager = new LocalGameManager(gameView.getGameState(), gameView.getBoard());
                        localGameManager.setCallback(this);
                        gameView.setLocalMode(true);
                        gameView.setLocalGameManager(localGameManager);
                        Log.d(TAG, "LocalGameManager initialized after GameView ready");
                    }
                }
            });

            initializeGameMode();
            setupEventListeners();
            setupBackPressedCallback();
            updateUIForNoGame();

            // Check for restorable queue session after UI is ready
            checkForRestoredQueue();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing app", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeGameMode() {
        switchToLocalMode();
        updateModeUI();

        // Initialize LocalGameManager after GameView is ready
        if (gameView != null && gameView.getGameState() != null && gameView.getBoard() != null) {
            localGameManager = new LocalGameManager(gameView.getGameState(), gameView.getBoard());
            localGameManager.setCallback(this);
            gameView.setLocalMode(true);
            gameView.setLocalGameManager(localGameManager);
        }
    }


    private void switchToLocalMode() {
        Log.d(TAG, "Switching to Local Mode");

        currentGameMode = GameMode.LOCAL;

        // Stop any online matchmaking
        if (queueManager != null && queueManager.getCurrentState() != QueueState.IDLE) {
            queueManager.leaveQueue();
        }

        // FIXED: Initialize local game manager with proper null checks and post() for GameView readiness
        if (gameView != null) {
            gameView.setLocalMode(true);

            // Use post() to ensure GameView is fully initialized
            gameView.post(() -> {
                if (gameView.getGameState() != null && gameView.getBoard() != null) {
                    localGameManager = new LocalGameManager(gameView.getGameState(), gameView.getBoard());
                    localGameManager.setCallback(this);
                    gameView.setLocalGameManager(localGameManager);
                    Log.d(TAG, "LocalGameManager initialized successfully");
                } else {
                    Log.d(TAG, "GameView not ready yet, LocalGameManager will be initialized later");
                }
            });
        }

        updateUIForLocalMode();
        Toast.makeText(this, "Switched to Local Mode", Toast.LENGTH_SHORT).show();
    }

    private void switchToOnlineMode() {
        Log.d(TAG, "Switching to Online Mode");

        currentGameMode = GameMode.ONLINE;

        // Reset local game
        if (localGameManager != null) {
            localGameManager.resetGame();
            localGameManager = null;
        }

        // Set GameView to online mode
        if (gameView != null) {
            gameView.setLocalMode(false);
            gameView.setLocalGameManager(null);
        }

        updateUIForOnlineMode();

        // Check for restorable queue session
        checkForRestoredQueue();

        Toast.makeText(this, "Switched to Online Mode", Toast.LENGTH_SHORT).show();
    }

    private void updateModeUI() {
        runOnUiThread(() -> {
            if (currentModeText != null) {
                String modeText = currentGameMode == GameMode.LOCAL ? "Local Mode" : "Online Mode";
                currentModeText.setText("Current Mode: " + modeText);
            }

            if (switchModeButton != null) {
                String buttonText = currentGameMode == GameMode.LOCAL ? "Switch to Online" : "Switch to Local";
                switchModeButton.setText(buttonText);
            }
        });
    }

    private void updateUIForLocalMode() {
        runOnUiThread(() -> {
            // FIXED: Add null checks and proper visibility management
            LinearLayout localModeControls = findViewById(R.id.local_mode_controls);
            LinearLayout onlineModeControls = findViewById(R.id.online_mode_controls);

            if (localModeControls != null) {
                localModeControls.setVisibility(View.VISIBLE);
                Log.d(TAG, "Local mode controls made visible");
            } else {
                Log.w(TAG, "local_mode_controls not found in layout");
            }

            if (onlineModeControls != null) {
                onlineModeControls.setVisibility(View.GONE);
                Log.d(TAG, "Online mode controls hidden");
            } else {
                Log.w(TAG, "online_mode_controls not found in layout");
            }

            // Hide online-specific components
            if (queueInfoPanel != null) queueInfoPanel.setVisibility(View.GONE);
            if (cancelMatchButton != null) cancelMatchButton.setVisibility(View.GONE);
            if (queueStatusText != null) queueStatusText.setVisibility(View.GONE);

            // Update status for local mode
            if (statusText != null) {
                statusText.setText("Local Mode: Press 'Start Game' to begin");
            }

            // Update game info
            if (gameIdText != null) {
                gameIdText.setText("Local Game Mode");
            }
            if (playerRoleText != null) {
                playerRoleText.setText("Two players on same device");
            }

            // FIXED: Update the find match button for local mode
            if (findMatchButton != null) {
                findMatchButton.setText("Start Local Game");
                findMatchButton.setEnabled(true);
            }

            updateModeUI();
        });
    }

    private void updateUIForOnlineMode() {
        runOnUiThread(() -> {
            // FIXED: Add null checks and proper visibility management
            LinearLayout localModeControls = findViewById(R.id.local_mode_controls);
            LinearLayout onlineModeControls = findViewById(R.id.online_mode_controls);

            if (localModeControls != null) {
                localModeControls.setVisibility(View.GONE);
                Log.d(TAG, "Local mode controls hidden");
            } else {
                Log.w(TAG, "local_mode_controls not found in layout");
            }

            if (onlineModeControls != null) {
                onlineModeControls.setVisibility(View.VISIBLE);
                Log.d(TAG, "Online mode controls made visible");
            } else {
                Log.w(TAG, "online_mode_controls not found in layout");
            }

            // Update button text for online mode
            if (findMatchButton != null) {
                findMatchButton.setText("Find Match");
                findMatchButton.setEnabled(true);
            }

            // Reset to no-game state for online mode
            updateUIForNoGame();
            updateModeUI();
        });
    }


    // ===== LOCAL GAME CALLBACK IMPLEMENTATION =====

    @Override
    public void onPlayerTurnChanged(boolean isPlayer1Turn) {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            String currentPlayer = isPlayer1Turn ? "Player 1 (Red)" : "Player 2 (Blue)";
            if (statusText != null) {
                statusText.setText("Current Turn: " + currentPlayer);
            }
            if (playerRoleText != null) {
                playerRoleText.setText("Current Turn: " + currentPlayer);
            }
        });
    }

    @Override
    public void onGameStateUpdated() {
        // Game view will handle visual updates
        Log.d(TAG, "Local game state updated");
    }

    @Override
    public void onGameWon(Player winner) {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            String winnerName = winner.isPlayer1() ? "Player 1 (Red)" : "Player 2 (Blue)";
            String message = winnerName + " wins!";

            if (statusText != null) {
                statusText.setText("Game Over! " + message);
            }

            showGameOverDialog(message);
        });
    }

    @Override
    public void onMoveResult(boolean success, String message) {
        if (isActivityDestroyed || isActivityFinishing) return;

        if (!success) {
            runOnUiThread(() -> {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onGameStarted() {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText("Local Game Started! Player 1's turn");
            }
            if (findMatchButton != null) {
                findMatchButton.setText("Game in Progress");
                findMatchButton.setEnabled(false);
            }
        });
    }

    @Override
    public void onGameReset() {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            updateUIForLocalMode();
        });
    }

    // ===== QUEUE MANAGER CALLBACK IMPLEMENTATION =====

    @Override
    public void onQueueStateChanged(QueueState newState, QueueManager.QueueInfo info) {
        if (isActivityDestroyed || isActivityFinishing || currentGameMode != GameMode.ONLINE) return;

        runOnUiThread(() -> {
            updateQueueUI(newState, info.timeInQueue, info.queueSize, info.status);

            // Update legacy flag for backward compatibility
            isInMatchmakingQueue = (newState == QueueState.IN_QUEUE ||
                    newState == QueueState.JOINING ||
                    newState == QueueState.MATCH_FOUND);

            // Save progress to persistence
            if (newState == QueueState.IN_QUEUE) {
                persistenceManager.saveQueueState(
                        newState,
                        currentPlayerId,
                        currentDeviceId,
                        System.currentTimeMillis() - info.timeInQueue,
                        totalQueueTime,
                        queueAttempts,
                        info.queueSize
                );
            }
        });
    }

    @Override
    public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
        if (isActivityDestroyed || isActivityFinishing || currentGameMode != GameMode.ONLINE) return;

        Log.i(TAG, "Match found via QueueManager - Player: " + playerId + ", isPlayer1: " + isPlayer1);

        // Save successful queue statistics
        long queueTime = queueManager.getTimeInQueue();
        persistenceManager.saveQueueStatistics(queueTime, queueAttempts, true, "match_found");
        persistenceManager.clearQueueState(); // Clear since match was found

        // Process the match
        handleMatchFound(gameState, isPlayer1, playerId);
    }

    @Override
    public void onQueueError(String error) {
        if (isActivityDestroyed || isActivityFinishing || currentGameMode != GameMode.ONLINE) return;

        Log.w(TAG, "Queue error: " + error);

        // Save failed queue statistics
        long queueTime = queueManager.getTimeInQueue();
        persistenceManager.saveQueueStatistics(queueTime, queueAttempts, false, "error: " + error);

        handleMatchmakingFailure(error);
    }

    @Override
    public void onQueueTimeout() {
        if (isActivityDestroyed || isActivityFinishing || currentGameMode != GameMode.ONLINE) return;

        Log.w(TAG, "Queue timeout");

        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("Matchmaking Timeout")
                    .setMessage("Couldn't find a match in reasonable time. Would you like to try again?")
                    .setPositiveButton("Try Again", (dialog, which) -> {
                        queueAttempts++;
                        startMatchmaking();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        queueManager.leaveQueue();
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    public void onQueuePositionUpdated(int position, int estimatedWait) {
        if (isActivityDestroyed || isActivityFinishing || currentGameMode != GameMode.ONLINE) return;

        // Update persistence with current position
        persistenceManager.updateQueuePosition(position, estimatedWait);

        runOnUiThread(() -> {
            // Update queue position display
            if (queuePositionText != null) {
                if (position <= 1) {
                    queuePositionText.setText("You are first in queue!");
                } else {
                    queuePositionText.setText("Queue position: " + position);
                }
            }

            // Update estimated wait time
            if (estimatedWaitText != null && estimatedWait > 0) {
                estimatedWaitText.setVisibility(View.VISIBLE);
                int minutes = estimatedWait / 60;
                int seconds = estimatedWait % 60;
                estimatedWaitText.setText(String.format("Est. wait: %d:%02d", minutes, seconds));
            }
        });
    }

    // ===== QUEUE RESTORATION =====

    private void checkForRestoredQueue() {
        // Only check for queue restoration in online mode
        if (currentGameMode != GameMode.ONLINE) return;

        // Check if persistence manager can restore a queue session
        if (queueManager.resumeQueueIfSaved()) {
            Log.d(TAG, "Successfully resumed queue from persistence");

            runOnUiThread(() -> {
                Toast.makeText(this, "Resuming previous matchmaking session...", Toast.LENGTH_SHORT).show();
            });

            return;
        }

        // Check for manual restoration option
        QueuePersistenceManager.PersistedQueueData data = persistenceManager.loadQueueState();
        if (data.isValid && data.isRecent()) {
            String timeWaiting = formatTime(data.getTimeSinceStart());

            new AlertDialog.Builder(this)
                    .setTitle("Resume Matchmaking?")
                    .setMessage("You were searching for a match when the app was closed. " +
                            "Resume searching? (You've been waiting " + timeWaiting + ")")
                    .setPositiveButton("Resume", (dialog, which) -> {
                        resumeQueueSession(data);
                    })
                    .setNegativeButton("Start Fresh", (dialog, which) -> {
                        persistenceManager.clearQueueState();
                        updateUIForNoGame();
                    })
                    .setCancelable(false)
                    .show();
        } else if (!data.isValid) {
            Log.d(TAG, "No valid queue session to restore: " + data.invalidReason);
            persistenceManager.clearQueueState();
        }
    }

    private void resumeQueueSession(QueuePersistenceManager.PersistedQueueData data) {
        Log.d(TAG, "Resuming queue session");

        currentPlayerId = data.playerId;
        currentDeviceId = data.deviceId;
        queueAttempts = data.queueAttempts;
        totalQueueTime = data.totalQueueTime;

        // Resume with QueueManager
        queueManager.joinQueue(data.playerId, data.deviceId);

        Toast.makeText(this, "Resuming matchmaking...", Toast.LENGTH_SHORT).show();
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return seconds + " seconds";
        }
    }

    // ===== ENHANCED MATCHMAKING METHODS =====

    private void startMatchmaking() {
        if (currentGameMode != GameMode.ONLINE) return;

        Log.d(TAG, "Starting enhanced matchmaking");

        // Increment attempt counter
        queueAttempts++;
        persistenceManager.incrementQueueAttempts();

        // Generate/get current IDs
        if (currentPlayerId == null) {
            currentPlayerId = PlayerIdGenerator.getOrCreatePlayerId();
        }
        if (currentDeviceId == null) {
            currentDeviceId = DeviceIdGenerator.getOrCreateDeviceId();
        }

        // Validate IDs before proceeding
        ValidationResult playerValidation = validatePlayerIdComprehensive(currentPlayerId);
        if (!playerValidation.isSuccess()) {
            Log.e(TAG, "Player ID validation failed: " + playerValidation.getMessage());
            handleMatchmakingFailure("Invalid player ID. Please restart the app.");
            return;
        }

        if (currentDeviceId == null || currentDeviceId.trim().isEmpty()) {
            Log.e(TAG, "Device ID is missing");
            handleMatchmakingFailure("Device identification failed. Please restart the app.");
            return;
        }

        Log.d(TAG, "Starting matchmaking with validated IDs - Player: " +
                currentPlayerId.substring(Math.max(0, currentPlayerId.length() - 8)) +
                ", Device: " + currentDeviceId.substring(Math.max(0, currentDeviceId.length() - 8)));

        if (gameView != null) {
            gameView.setLocalMode(false);
            gameView.setLocalGameManager(null);
        }

        // Use QueueManager to handle the matchmaking
        queueManager.joinQueue(currentPlayerId, currentDeviceId);
    }

    private void cancelMatchmaking() {
        Log.d(TAG, "Cancelling enhanced matchmaking");

        // Save statistics for cancelled queue
        long queueTime = queueManager.getTimeInQueue();
        if (queueTime > 5000) { // Only save if we waited more than 5 seconds
            persistenceManager.saveQueueStatistics(queueTime, queueAttempts, false, "user_cancelled");
        }

        // Use QueueManager to handle cancellation
        queueManager.leaveQueue();
    }

    // ===== UI UPDATE METHODS =====

    private void updateQueueUI(QueueState state, long timeInQueue, int queueSize, String status) {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            switch (state) {
                case IDLE:
                    showIdleUI();
                    break;

                case JOINING:
                    showJoiningUI();
                    break;

                case IN_QUEUE:
                    showInQueueUI(timeInQueue, queueSize, status);
                    break;

                case MATCH_FOUND:
                    showMatchFoundUI();
                    break;

                case LEAVING_QUEUE:
                    showLeavingQueueUI();
                    break;

                case IN_GAME:
                    showInGameUI();
                    break;
            }
        });
    }

    private void showIdleUI() {
        queueInfoPanel.setVisibility(View.GONE);
        findMatchButton.setEnabled(true);
        findMatchButton.setText("Find Match");

        if (statusText != null) {
            statusText.setText("Ready to find match");
        }
    }

    private void showJoiningUI() {
        queueInfoPanel.setVisibility(View.VISIBLE);
        queueStatusHeader.setText("Joining Matchmaking...");
        queueProgress.setIndeterminate(true);
        queueStatusDetail.setText("Connecting to matchmaking service");

        // Hide time/position info while joining
        queueTimeText.setVisibility(View.GONE);
        queuePositionText.setVisibility(View.GONE);
        estimatedWaitText.setVisibility(View.GONE);

        findMatchButton.setEnabled(false);
        findMatchButton.setText("Joining Queue...");

        if (statusText != null) {
            statusText.setText("Joining matchmaking queue...");
        }
    }

    private void showInQueueUI(long timeInQueue, int queueSize, String status) {
        queueInfoPanel.setVisibility(View.VISIBLE);
        queueStatusHeader.setText("Searching for Opponent");
        queueStatusHeader.setTextColor(getResources().getColor(android.R.color.white));
        queueProgress.setIndeterminate(true);

        // Show time in queue
        queueTimeText.setVisibility(View.VISIBLE);
        long minutes = timeInQueue / 60000;
        long seconds = (timeInQueue % 60000) / 1000;
        queueTimeText.setText(String.format("Time in queue: %02d:%02d", minutes, seconds));

        // Show queue size/position
        queuePositionText.setVisibility(View.VISIBLE);
        if (queueSize > 0) {
            if (queueSize == 1) {
                queuePositionText.setText("You are first in queue!");
            } else {
                queuePositionText.setText("Queue size: " + queueSize + " players");
            }
        } else {
            queuePositionText.setText("Checking queue status...");
        }

        // Update status detail
        queueStatusDetail.setText(status != null ? status : "Searching for opponent...");

        // Show cancel button
        cancelQueueButton.setVisibility(View.VISIBLE);
        cancelQueueButton.setEnabled(true);

        findMatchButton.setEnabled(false);
        findMatchButton.setText("In Queue...");

        if (statusText != null) {
            statusText.setText("Waiting for opponent... (" + String.format("%02d:%02d", minutes, seconds) + ")");
        }
    }

    private void showMatchFoundUI() {
        queueInfoPanel.setVisibility(View.VISIBLE);
        queueStatusHeader.setText("Match Found!");
        queueStatusHeader.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        queueProgress.setIndeterminate(true);
        queueStatusDetail.setText("Loading game...");

        // Hide time/position info
        queueTimeText.setVisibility(View.GONE);
        queuePositionText.setVisibility(View.GONE);
        estimatedWaitText.setVisibility(View.GONE);

        // Hide cancel button - can't cancel now
        cancelQueueButton.setVisibility(View.GONE);

        if (statusText != null) {
            statusText.setText("Match found! Loading game...");
        }
    }

    private void showLeavingQueueUI() {
        queueStatusHeader.setText("Leaving Queue...");
        queueStatusDetail.setText("Cancelling matchmaking...");
        queueProgress.setIndeterminate(true);

        // Disable cancel button
        cancelQueueButton.setEnabled(false);

        if (statusText != null) {
            statusText.setText("Cancelling matchmaking...");
        }
    }

    private void showInGameUI() {
        queueInfoPanel.setVisibility(View.GONE);
        findMatchButton.setEnabled(false);
    }

    // ===== EXISTING GAME STATE LISTENER IMPLEMENTATION =====

    @Override
    public void onGameCreated(String gameId) {
        runOnUiThread(() -> {
            updateUIForActiveGame(gameId, true);
            if (statusText != null) {
                statusText.setText("Game created! Waiting for opponent...");
            }
            Toast.makeText(this, "Game created: " + gameId, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onGameCreationFailed(String errorMessage) {
        runOnUiThread(() -> {
            updateUIForNoGame();
            if (statusText != null) {
                statusText.setText("Failed to create game: " + errorMessage);
            }
            Toast.makeText(this, "Game creation failed: " + errorMessage, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onGameJoined(String gameId, boolean isPlayer1) {
        runOnUiThread(() -> {
            updateUIForActiveGame(gameId, isPlayer1);
            String playerRole = isPlayer1 ? "Player 1 (Red)" : "Player 2 (Blue)";
            if (statusText != null) {
                statusText.setText("Joined game as " + playerRole);
            }
            Toast.makeText(this, "Joined game as " + playerRole, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onGameJoinFailed(String errorMessage) {
        runOnUiThread(() -> {
            updateUIForNoGame();
            if (statusText != null) {
                statusText.setText("Failed to join game: " + errorMessage);
            }
            Toast.makeText(this, "Join failed: " + errorMessage, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void updateStatus(String message) {
        if (statusText != null && !isActivityDestroyed && !isActivityFinishing) {
            runOnUiThread(() -> {
                if (statusText != null) {
                    statusText.setText(message);
                }
            });
        }
    }

    @Override
    public void showGameOver(String winner) {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText("Game Over! " + winner);
            }

            // Reset buttons to their appropriate state
            if (findMatchButton != null) {
                findMatchButton.setEnabled(true);
                String buttonText = currentGameMode == GameMode.LOCAL ? "Start Local Game" : "Find Match";
                findMatchButton.setText(buttonText);
            }

            if (cancelMatchButton != null) {
                cancelMatchButton.setVisibility(View.GONE);
            }

            if (queueStatusText != null) {
                queueStatusText.setVisibility(View.GONE);
            }

            // Hide queue panel if it's visible
            if (queueInfoPanel != null) {
                queueInfoPanel.setVisibility(View.GONE);
            }

            // Show the game over dialog
            showGameOverDialog(winner);
        });
    }
    // ===== HELPER METHODS =====

    public void handleMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
        if (isActivityDestroyed || isActivityFinishing) {
            Log.d(TAG, "Ignoring match found - activity destroyed/finishing");
            return;
        }

        Log.d(TAG, "Processing match found - Player: " + playerId + ", isPlayer1: " + isPlayer1);

        // Enhanced validation
        if (gameState == null) {
            Log.e(TAG, "Invalid match data - null game state");
            handleMatchmakingFailure("Invalid match data received");
            return;
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            Log.e(TAG, "Invalid match data - null/empty player ID");
            handleMatchmakingFailure("Invalid player ID received");
            return;
        }

        if (gameState.getGameId() == null || gameState.getGameId().trim().isEmpty()) {
            Log.e(TAG, "Invalid match data - null/empty game ID");
            handleMatchmakingFailure("Invalid game ID received");
            return;
        }

        // Enhanced self-match validation
        if (gameState.getPlayer1Id() != null && gameState.getPlayer2Id() != null) {
            ValidationResult selfMatchValidation = validateNoSelfMatchEnhanced(
                    gameState.getPlayer1Id(), gameState.getPlayer2Id()
            );
            if (!selfMatchValidation.isSuccess()) {
                Log.e(TAG, "CRITICAL SELF-MATCH PREVENTION: " + selfMatchValidation.getMessage());
                handleMatchmakingFailure("Invalid match detected. Please try again.");
                return;
            }

            if (gameState.getPlayer1Id().equals(gameState.getPlayer2Id())) {
                Log.e(TAG, "Detected self-match! Player1 ID equals Player2 ID: " + gameState.getPlayer1Id());
                handleMatchmakingFailure("Invalid match detected (self-match). Please try again.");
                return;
            }
        }

        // Update player ID if changed
        if (!playerId.equals(currentPlayerId)) {
            Log.d(TAG, "Updating player ID: " + currentPlayerId + " -> " + playerId);
            currentPlayerId = playerId;
            PlayerIdGenerator.setPlayerId(playerId);
        }

        currentGameId = gameState.getGameId();

        // Set up game view
        if (gameView != null) {
            gameView.setMatchedGame(gameState, isPlayer1, playerId);
        } else {
            Log.e(TAG, "GameView is null - cannot set matched game");
            handleMatchmakingFailure("Game display error");
            return;
        }

        // Update UI
        runOnUiThread(() -> {
            updateUIForActiveGame(currentGameId, isPlayer1);
            String playerRole = isPlayer1 ? "Player 1 (Red)" : "Player 2 (Blue)";

            // FIXED: Determine correct turn state immediately
            boolean isCurrentPlayerTurn = (isPlayer1 && gameState.isPlayer1Turn()) ||
                    (!isPlayer1 && !gameState.isPlayer1Turn());
            String turnStatus = isCurrentPlayerTurn ? "Your turn" : "Opponent's turn";

            if (statusText != null) {
                statusText.setText(turnStatus); // Show turn status, not generic message
            }

            if (cancelMatchButton != null) {
                cancelMatchButton.setVisibility(View.GONE);
            }
            if (queueStatusText != null) {
                queueStatusText.setVisibility(View.GONE);
            }
            if (queueInfoPanel != null) {
                queueInfoPanel.setVisibility(View.GONE);
            }

            Toast.makeText(this, "Game ready! You are " + playerRole, Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Match successfully processed - Game: " + currentGameId + ", Role: " + playerRole);
        });
    }

    private void handleMatchmakingFailure(String errorMessage) {
        if (isActivityDestroyed || isActivityFinishing) {
            Log.d(TAG, "Ignoring matchmaking failure - activity finishing");
            return;
        }

        Log.w(TAG, "Handling matchmaking failure: " + errorMessage);

        // Let QueueManager handle the failure
        isInMatchmakingQueue = false;

        String finalErrorMessage = errorMessage;

        // Enhanced error analysis and recovery
        if (errorMessage != null) {
            String lowerError = errorMessage.toLowerCase();

            if (lowerError.contains("invalid player id") ||
                    lowerError.contains("expired") ||
                    lowerError.contains("timeout") ||
                    lowerError.contains("timestamp")) {

                Log.d(TAG, "Player ID error detected - will regenerate on next attempt");
                currentPlayerId = null;
                PlayerIdGenerator.forceRegenerateId();
            }

            if (lowerError.contains("validation") || lowerError.contains("invalid")) {
                Log.d(TAG, "Validation error detected - will regenerate IDs on next attempt");
                currentPlayerId = null;
                currentDeviceId = null;
                PlayerIdGenerator.forceRegenerateId();
                DeviceIdGenerator.clearDeviceId();
            }
        }

        // Update UI
        runOnUiThread(() -> {
            if (findMatchButton != null) {
                findMatchButton.setEnabled(true);
                findMatchButton.setText("Find Match");
            }
            if (cancelMatchButton != null) {
                cancelMatchButton.setVisibility(View.GONE);
            }
            if (queueStatusText != null) {
                queueStatusText.setVisibility(View.GONE);
            }
            if (statusText != null) {
                statusText.setText("Matchmaking failed: " + finalErrorMessage);
            }

            Toast.makeText(this, "Matchmaking failed: " + finalErrorMessage, Toast.LENGTH_LONG).show();
        });
    }

    public void sendMoveToServer(int boardX, int boardY, Integer fromX, Integer fromY) {
        if (currentGameId == null || currentPlayerId == null) {
            Log.e(TAG, "Cannot send move - missing game ID or player ID");
            updateStatus("Error: Game not properly initialized");
            return;
        }

        Log.d(TAG, "Sending move to server - Game: " + currentGameId +
                ", Player: " + currentPlayerId + ", Position: (" + boardX + "," + boardY + ")");

        networkService.processMove(currentGameId, currentPlayerId, boardX, boardY, fromX, fromY,
                new NetworkService.GameCallback() {
                    @Override
                    public void onSuccess(ServerGameState gameState) {
                        if (!isActivityDestroyed && !isActivityFinishing && gameView != null) {
                            Log.d(TAG, "Move processed successfully");
                            // GameView will handle the state update through its polling
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isActivityDestroyed && !isActivityFinishing) {
                            Log.e(TAG, "Move failed: " + errorMessage);
                            runOnUiThread(() -> {
                                updateStatus("Move failed: " + errorMessage);
                                Toast.makeText(MainActivity.this, "Move failed: " + errorMessage, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
    }

    // ===== INITIALIZATION METHODS =====

    private void initializeHandlers() {
        matchmakingThread = new HandlerThread("MatchmakingThread");
        matchmakingThread.start();
        matchmakingHandler = new Handler(matchmakingThread.getLooper());
    }

    private void initializeViews() {
        gameView = findViewById(R.id.game_view);
        currentModeText = findViewById(R.id.current_mode_text);
        switchModeButton = findViewById(R.id.switch_mode_button);

        statusText = findViewById(R.id.status_text);
        gameIdText = findViewById(R.id.game_id_text);
        playerRoleText = findViewById(R.id.player_role_text);
        queueStatusText = findViewById(R.id.queue_status_text);
        resetButton = findViewById(R.id.reset_button);
        findMatchButton = findViewById(R.id.find_match_button);
        rulesButton = findViewById(R.id.rules_button);
        cancelMatchButton = findViewById(R.id.cancel_match_button);

        // ADD THESE MISSING findViewById CALLS:
        LinearLayout localModeControls = findViewById(R.id.local_mode_controls);
        LinearLayout onlineModeControls = findViewById(R.id.online_mode_controls);
        Button startLocalGameButton = findViewById(R.id.start_local_game_button);

        // Enhanced Queue UI Components
        queueInfoPanel = findViewById(R.id.queue_info_panel);
        queueStatusHeader = findViewById(R.id.queue_status_header);
        queueProgress = findViewById(R.id.queue_progress);
        queueTimeText = findViewById(R.id.queue_time_text);
        queuePositionText = findViewById(R.id.queue_position_text);
        queueStatusDetail = findViewById(R.id.queue_status_detail);
        estimatedWaitText = findViewById(R.id.estimated_wait_text);
        cancelQueueButton = findViewById(R.id.cancel_queue_button);

        // Validate all views were found
        if (gameView == null || statusText == null || gameIdText == null ||
                playerRoleText == null || queueStatusText == null || resetButton == null ||
                findMatchButton == null || cancelMatchButton == null || queueInfoPanel == null ||
                queueStatusHeader == null || queueProgress == null || queueTimeText == null ||
                queuePositionText == null || queueStatusDetail == null || estimatedWaitText == null ||
                cancelQueueButton == null) {
            throw new RuntimeException("One or more required views not found in layout");
        }
        if (currentModeText == null || switchModeButton == null) {
            throw new RuntimeException("Mode selection UI components not found in layout");
        }

        // Set up GameView listeners
        if (gameView != null) {
            gameView.setUIUpdateListener(this);
            gameView.setGameStateListener(this);
        }

        Log.d(TAG, "All UI components initialized successfully");
    }

    private void setupEventListeners() {
        // FIXED: Add null checks for all button listeners
        if (resetButton != null) {
            resetButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    showResetDialog();  // Only show reset dialog for reset button
                }
            });
        }

        // FIXED: Rules button should only show rules, not reset dialog
        if (rulesButton != null) {
            rulesButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    showRulesDialog();  // Only show rules dialog
                }
            });
        }

        if (findMatchButton != null) {
            findMatchButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    handleFindMatchButton();
                }
            });
        }

        // FIXED: Add null check for switch mode button
        if (switchModeButton != null) {
            switchModeButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    handleSwitchMode();
                }
            });
        } else {
            Log.w(TAG, "switchModeButton is null - button won't respond to clicks");
        }

        // Original cancel button (keep for backward compatibility)
        if (cancelMatchButton != null) {
            cancelMatchButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    cancelMatchmaking();
                }
            });
        }

        // Enhanced cancel queue button (in the queue panel)
        if (cancelQueueButton != null) {
            cancelQueueButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    cancelMatchmaking();
                }
            });
        }

        // FIXED: Add proper null check and listener for start local game button
        Button startLocalGameButton = findViewById(R.id.start_local_game_button);
        if (startLocalGameButton != null) {
            startLocalGameButton.setOnClickListener(v -> {
                if (!isActivityDestroyed && !isActivityFinishing) {
                    startLocalGame();
                }
            });
            Log.d(TAG, "Start local game button listener set up");
        } else {
            Log.w(TAG, "start_local_game_button not found in layout");
        }
    }

    private void handleFindMatchButton() {
        if (currentGameMode == GameMode.LOCAL) {
            startLocalGame();
        } else {
            if (queueManager.getCurrentState() != QueueState.IDLE) {
                Toast.makeText(this, "Already in matchmaking process", Toast.LENGTH_SHORT).show();
                return;
            }
            startMatchmaking();
        }
    }

    private void startLocalGame() {
        Log.d(TAG, "Starting local game");

        if (localGameManager == null && gameView != null &&
                gameView.getGameState() != null && gameView.getBoard() != null) {
            localGameManager = new LocalGameManager(gameView.getGameState(), gameView.getBoard());
            localGameManager.setCallback(this);
        }

        if (localGameManager != null) {
            localGameManager.startNewGame();

            // Set GameView to local mode
            if (gameView != null) {
                gameView.setLocalMode(true);
                gameView.setLocalGameManager(localGameManager);
            }
        } else {
            Toast.makeText(this, "Error: Could not initialize local game", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSwitchMode() {
        Log.d(TAG, "handleSwitchMode called, current mode: " + currentGameMode);

        // Show confirmation dialog
        String currentModeStr = currentGameMode == GameMode.LOCAL ? "Local" : "Online";
        String targetModeStr = currentGameMode == GameMode.LOCAL ? "Online" : "Local";

        new AlertDialog.Builder(this)
                .setTitle("Switch Game Mode")
                .setMessage("Switch from " + currentModeStr + " Mode to " + targetModeStr + " Mode?\n\n" +
                        "This will reset any current game progress.")
                .setPositiveButton("Switch", (dialog, which) -> {
                    try {
                        if (currentGameMode == GameMode.LOCAL) {
                            Log.d(TAG, "Switching from Local to Online mode");
                            switchToOnlineMode();
                        } else {
                            Log.d(TAG, "Switching from Online to Local mode");
                            switchToLocalMode();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error switching modes", e);
                        Toast.makeText(this, "Error switching modes: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void showGameOverDialog(String message) {
        // Prevent multiple dialogs
        if (gameOverDialogShowing) {
            Log.d(TAG, "Game over dialog already showing, ignoring duplicate");
            return;
        }

        gameOverDialogShowing = true;

        // Dismiss any existing dialog first
        if (currentGameOverDialog != null && currentGameOverDialog.isShowing()) {
            currentGameOverDialog.dismiss();
        }

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("Game Over!")
                    .setMessage(message)
                    .setCancelable(false) // Prevent accidental dismissal
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        currentGameOverDialog = null;
                        gameOverDialogShowing = false;
                        resetUIAfterGameEnd();
                    });

            if (currentGameMode == GameMode.LOCAL) {
                builder.setNeutralButton("Play Again", (dialog, which) -> {
                    dialog.dismiss();
                    currentGameOverDialog = null;
                    gameOverDialogShowing = false;
                    resetUIAfterGameEnd();

                    // Add small delay to prevent rapid successive calls
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (localGameManager != null && !isActivityDestroyed && !isActivityFinishing) {
                            localGameManager.startNewGame();
                        }
                    }, 100);
                });
            } else {
                builder.setNeutralButton("Find New Match", (dialog, which) -> {
                    dialog.dismiss();
                    currentGameOverDialog = null;
                    gameOverDialogShowing = false;
                    resetUIAfterGameEnd();

                    // Add small delay to prevent rapid successive calls
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isActivityDestroyed && !isActivityFinishing) {
                            startMatchmaking();
                        }
                    }, 100);
                });
            }

            currentGameOverDialog = builder.create();
            currentGameOverDialog.show();

        } catch (Exception e) {
            Log.w(TAG, "Could not show game over dialog", e);
            gameOverDialogShowing = false;
            currentGameOverDialog = null;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            resetUIAfterGameEnd();
        }
    }
    private void resetUIAfterGameEnd() {
        runOnUiThread(() -> {
            if (currentGameMode == GameMode.LOCAL) {
                // Reset local game UI properly
                if (statusText != null) {
                    statusText.setText("Local Mode: Press 'Start Game' to begin");
                }

                if (findMatchButton != null) {
                    findMatchButton.setText("Start Local Game");
                    findMatchButton.setEnabled(true);
                }

                if (gameIdText != null) {
                    gameIdText.setText("Local Game Mode");
                }

                if (playerRoleText != null) {
                    playerRoleText.setText("Two players on same device");
                }

                // Ensure proper GameView height for local mode
                if (gameView != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) gameView.getLayoutParams();
                    params.weight = 0.75f; // Normal height for local mode
                    gameView.setLayoutParams(params);
                }

            } else {
                updateUIForNoGame();
                // Ensure proper GameView height for online mode
                if (gameView != null) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) gameView.getLayoutParams();
                    params.weight = 0.75f; // Normal height when not in queue
                    gameView.setLayoutParams(params);
                }
            }
        });
    }




    private void setupBackPressedCallback() {
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitDialog();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void showExitDialog() {
        if (isActivityDestroyed || isActivityFinishing) return;

        try {
            new AlertDialog.Builder(this)
                    .setTitle("Exit App")
                    .setMessage("Do you want to exit the game?")
                    .setPositiveButton("Exit", (dialog, which) -> exitApplication())
                    .setNegativeButton("Cancel", null)
                    .setCancelable(true)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show exit dialog", e);
            exitApplication();
        }
    }

    private void showRulesDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("NaarPazham Game Rules")
                    .setMessage("HOW TO PLAY:\n\n" +
                            "1. SETUP PHASE:\n" +
                            "   • Each player places 3 pieces on the board\n" +
                            "   • Player 1 (Red) goes first, then Player 2 (Blue)\n\n" +
                            "2. MOVEMENT PHASE:\n" +
                            "   • After all 6 pieces are placed, players take turns\n" +
                            "   • Move your piece to any adjacent empty space\n" +
                            "   • Adjacent means horizontal, vertical, or diagonal\n\n" +
                            "3. WINNING:\n" +
                            "   • First player to get 3 pieces in a row wins\n" +
                            "   • Winning lines can be horizontal, vertical, or diagonal\n\n" +
                            "4. TURNS:\n" +
                            "   • Players alternate turns throughout the game\n" +
                            "   • Current player is shown in the status area")
                    .setPositiveButton("Got it!", null)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show rules dialog", e);
            Toast.makeText(this, "Rules: Place 3 pieces, then move to adjacent spaces. Get 3 in a row to win!", Toast.LENGTH_LONG).show();
        }
    }

    private void showResetDialog() {
        try {
            String resetMessage = currentGameMode == GameMode.LOCAL ?
                    "Reset the current local game?" :
                    "What would you like to do?";

            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("Reset Game")
                    .setMessage(resetMessage);

            if (currentGameMode == GameMode.LOCAL) {
                builder.setPositiveButton("Reset Game", (dialog, which) -> performLocalReset())
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Exit App", (dialog, which) -> exitApplication());
            } else {
                builder.setPositiveButton("Reset Game", (dialog, which) -> performCompleteReset())
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Exit App", (dialog, which) -> exitApplication());
            }

            builder.show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show reset dialog", e);
            if (currentGameMode == GameMode.LOCAL) {
                performLocalReset();
            } else {
                performCompleteReset();
            }
        }
    }

    private void performLocalReset() {
        Log.d(TAG, "Performing local game reset");

        if (localGameManager != null) {
            localGameManager.resetGame();
        }

        if (gameView != null) {
            gameView.resetGame();
        }

        updateUIForLocalMode();
        Log.d(TAG, "Local game reset completed");
    }

    private void performCompleteReset() {
        Log.d(TAG, "Performing complete reset");

        // Save statistics before reset
        long queueTime = queueManager.getTimeInQueue();
        if (queueTime > 0) {
            persistenceManager.saveQueueStatistics(queueTime, queueAttempts, false, "user_reset");
        }

        // Use QueueManager to leave queue cleanly
        queueManager.leaveQueue();

        if (gameView != null) {
            gameView.resetGame();
        }

        updateUIForNoGame();

        // Clear all IDs for fresh start
        currentGameId = null;
        currentPlayerId = null;
        currentDeviceId = null;
        queueAttempts = 0;
        totalQueueTime = 0;

        // Force fresh ID generation
        PlayerIdGenerator.forceRegenerateId();
        DeviceIdGenerator.clearDeviceId();

        // Clear persistence
        persistenceManager.clearQueueState();

        Log.d(TAG, "Complete reset finished - all IDs cleared for fresh start");
    }

    private void exitApplication() {
        Log.d(TAG, "Exiting application");

        isActivityFinishing = true;
        performImmediateCleanup();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }, 500);
        }
    }

    // ===== LIFECYCLE MANAGEMENT =====

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");

        if (gameView != null) {
            gameView.pauseGameActivity();
        }

        // QueueManager handles its own pause/resume cycle
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");

        if (gameView != null) {
            gameView.resumeGameActivity();
        }

        // QueueManager handles its own pause/resume cycle
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop called - app going to background");

        // QueueManager will persist state automatically
        if (isFinishing()) {
            performImmediateCleanup();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called - cleaning up resources");
        isActivityDestroyed = true;


        performImmediateCleanup();

        if (currentGameOverDialog != null && currentGameOverDialog.isShowing()) {
            currentGameOverDialog.dismiss();
        }
        currentGameOverDialog = null;
        gameOverDialogShowing = false;

        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }

        if (matchmakingThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                matchmakingThread.quitSafely();
            } else {
                matchmakingThread.quit();
            }
            try {
                matchmakingThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for matchmaking thread to finish", e);
                Thread.currentThread().interrupt();
            }
            matchmakingThread = null;
            matchmakingHandler = null;
        }


        super.onDestroy();
    }

    @Override
    public void finish() {
        Log.d(TAG, "finish() called");
        isActivityFinishing = true;

        performImmediateCleanup();
        super.finish();
    }

    private void performImmediateCleanup() {
        Log.d(TAG, "Performing immediate cleanup");

        // Clean up QueueManager
        if (queueManager != null) {
            queueManager.cleanup();
        }

        // Clean up NetworkRetryManager
        if (retryManager != null) {
            retryManager.cleanup();
        }

        if (matchmakingHandler != null) {
            matchmakingHandler.removeCallbacksAndMessages(null);
        }

        if (gameView != null) {
            gameView.destroyView();
        }

        if (networkService != null) {
            networkService.cleanup();
        }

        if (localGameManager != null) {
            localGameManager = null;
        }
        isInMatchmakingQueue = false;

        Log.d(TAG, "Immediate cleanup completed");
    }

    // ===== UTILITY METHODS =====

    private void updateUIForNoGame() {
        if (isActivityDestroyed || isActivityFinishing) return;

        runOnUiThread(() -> {
            if (gameIdText != null) gameIdText.setText(currentGameMode == GameMode.LOCAL ? "Local Game Mode" : "No active game");
            if (playerRoleText != null) playerRoleText.setText(currentGameMode == GameMode.LOCAL ? "Two players on same device" : "");
            if (statusText != null) {
                String statusMsg = currentGameMode == GameMode.LOCAL ?
                        "Local Mode: Press 'Start Local Game' to begin" : "Find a match to begin";
                statusText.setText(statusMsg);
            }
            if (cancelMatchButton != null) cancelMatchButton.setVisibility(View.GONE);
            if (queueStatusText != null) queueStatusText.setVisibility(View.GONE);

            if (findMatchButton != null) {
                findMatchButton.setEnabled(true);
                String buttonText = currentGameMode == GameMode.LOCAL ? "Start Local Game" : "Find Match";
                findMatchButton.setText(buttonText);
            }

            // Hide queue panel
            if (queueInfoPanel != null) {
                queueInfoPanel.setVisibility(View.GONE);
            }
        });

        if (currentGameMode == GameMode.ONLINE) {
            currentGameId = null;
            isInMatchmakingQueue = false;
        }
    }

    private void updateUIForActiveGame(String gameId, boolean isPlayer1) {
        if (gameId == null || gameId.trim().isEmpty() || isActivityDestroyed || isActivityFinishing) {
            updateUIForNoGame();
            return;
        }

        this.currentGameId = gameId;

        runOnUiThread(() -> {
            if (gameIdText != null) gameIdText.setText("Game ID: " + gameId);

            String role = isPlayer1 ? "Player 1 (Red)" : "Player 2 (Blue)";
            if (playerRoleText != null) playerRoleText.setText("You are: " + role);

            if (findMatchButton != null) findMatchButton.setEnabled(false);
            if (cancelMatchButton != null) cancelMatchButton.setVisibility(View.GONE);
            if (queueStatusText != null) queueStatusText.setVisibility(View.GONE);

            // Hide queue panel during game
            if (queueInfoPanel != null) {
                queueInfoPanel.setVisibility(View.GONE);
            }
        });
    }

    // ===== VALIDATION METHODS =====

    private ValidationResult validatePlayerIdComprehensive(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            return new ValidationResult(false, "Player ID cannot be empty");
        }

        String trimmed = playerId.trim();

        // Length validation
        if (trimmed.length() < 8 || trimmed.length() > 64) {
            return new ValidationResult(false, "Player ID length invalid");
        }

        // Format validation
        if (!trimmed.startsWith("ANDROID_PLAYER_")) {
            return new ValidationResult(false, "Player ID format invalid");
        }

        // Structure validation
        String[] parts = trimmed.split("_");
        if (parts.length < 4) {
            return new ValidationResult(false, "Player ID structure invalid");
        }

        // Validate timestamp part
        try {
            long timestamp = Long.parseLong(parts[2]);
            long now = System.currentTimeMillis();
            // Allow 24 hours old maximum
            if (Math.abs(now - timestamp) > 24 * 60 * 60 * 1000) {
                return new ValidationResult(false, "Player ID timestamp too old");
            }
        } catch (NumberFormatException e) {
            return new ValidationResult(false, "Player ID timestamp invalid");
        }

        return new ValidationResult(true, "Player ID valid");
    }

    private String extractDeviceIdentifierFromPlayerId(String playerId) {
        if (playerId == null || !playerId.startsWith("ANDROID_PLAYER_")) {
            return null;
        }

        try {
            String[] parts = playerId.split("_");
            if (parts.length >= 4) {
                return parts[3]; // Device hash part
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not extract device identifier from " + playerId);
        }

        return null;
    }

    private ValidationResult validateNoSelfMatchEnhanced(String player1Id, String player2Id) {
        if (player1Id == null || player2Id == null) {
            return new ValidationResult(false, "Player ID is null");
        }

        if (player1Id.equals(player2Id)) {
            return new ValidationResult(false, "Same player ID detected");
        }

        // Device ID extraction and comparison from player IDs
        String device1FromId = extractDeviceIdentifierFromPlayerId(player1Id);
        String device2FromId = extractDeviceIdentifierFromPlayerId(player2Id);

        if (device1FromId != null && device2FromId != null && device1FromId.equals(device2FromId)) {
            return new ValidationResult(false, "Same device identifier in player IDs");
        }

        // Device ID comparison (if available)
        if (currentDeviceId != null) {
            String otherDeviceFromId = extractDeviceIdentifierFromPlayerId(
                    player1Id.equals(currentPlayerId) ? player2Id : player1Id
            );
            if (otherDeviceFromId != null && currentDeviceId.equals(otherDeviceFromId)) {
                return new ValidationResult(false, "Same device detected");
            }
        }

        // Timestamp proximity check
        if (arePlayerTimestampsSuspiciouslyClose(player1Id, player2Id)) {
            return new ValidationResult(false, "Player creation timestamps too close");
        }

        return new ValidationResult(true, "Players can be matched");
    }

    private boolean arePlayerTimestampsSuspiciouslyClose(String player1Id, String player2Id) {
        try {
            Long timestamp1 = extractTimestampFromPlayerId(player1Id);
            Long timestamp2 = extractTimestampFromPlayerId(player2Id);

            if (timestamp1 != null && timestamp2 != null) {
                long timeDiff = Math.abs(timestamp1 - timestamp2);
                return timeDiff < 2000; // Within 2 seconds is suspicious
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not compare timestamps: " + e.getMessage());
        }

        return false;
    }

    private Long extractTimestampFromPlayerId(String playerId) {
        if (playerId == null || !playerId.startsWith("ANDROID_PLAYER_")) {
            return null;
        }

        try {
            String[] parts = playerId.split("_");
            if (parts.length >= 3) {
                return Long.parseLong(parts[2]);
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to extract timestamp from player ID");
        }

        return null;
    }

    // ===== VALIDATION RESULT CLASS =====

    private static class ValidationResult {
        private final boolean success;
        private final String message;

        public ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}

// ===== ENHANCED PLAYER ID GENERATOR (Same as before, included for completeness) =====

class PlayerIdGenerator {
    private static final String TAG = "PlayerIdGenerator";

    private static final String PREFS_NAME = "naarpazham_player_prefs";
    private static final String KEY_PLAYER_ID = "current_player_id";
    private static final String KEY_DEVICE_FINGERPRINT = "device_fingerprint";
    private static final String KEY_LAST_GENERATED = "last_generated_time";

    private static String currentPlayerId = null;
    private static String deviceFingerprint = null;
    private static long lastGenerationTime = 0;
    private static Context appContext = null;

    private static final long MIN_GENERATION_INTERVAL = 2000;
    private static final long SESSION_VALIDITY_HOURS = 2;

    public static synchronized void initialize(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
            loadPersistedData();
            ensureDeviceFingerprint();
            Log.d(TAG, "PlayerIdGenerator initialized");
        }
    }

    private static void loadPersistedData() {
        if (appContext == null) return;

        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            String savedPlayerId = prefs.getString(KEY_PLAYER_ID, null);
            String savedFingerprint = prefs.getString(KEY_DEVICE_FINGERPRINT, null);
            long savedTime = prefs.getLong(KEY_LAST_GENERATED, 0);

            if (savedPlayerId != null) {
                Log.d(TAG, "Loaded saved player ID: " + savedPlayerId);
                Log.d(TAG, "Saved generation time: " + savedTime + " (" + new Date(savedTime) + ")");
            }

            if (isValidAndFresh(savedPlayerId, savedTime)) {
                currentPlayerId = savedPlayerId;
                lastGenerationTime = savedTime;
                Log.d(TAG, "Restored valid player ID from storage");
            } else if (savedPlayerId != null) {
                Log.d(TAG, "Stored player ID expired or invalid, will generate new one");
            }

            if (isValidFingerprint(savedFingerprint)) {
                deviceFingerprint = savedFingerprint;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading persisted data", e);
        }
    }

    private static void savePlayerData() {
        if (appContext == null) return;

        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            if (currentPlayerId != null) {
                editor.putString(KEY_PLAYER_ID, currentPlayerId);
                editor.putLong(KEY_LAST_GENERATED, lastGenerationTime);
            }

            if (deviceFingerprint != null) {
                editor.putString(KEY_DEVICE_FINGERPRINT, deviceFingerprint);
            }

            editor.apply();
            Log.d(TAG, "Player data saved to storage");

        } catch (Exception e) {
            Log.e(TAG, "Error saving player data", e);
        }
    }

    private static void ensureDeviceFingerprint() {
        if (deviceFingerprint != null) return;

        try {
            StringBuilder fingerprint = new StringBuilder();

            String brand = Build.BRAND != null ? Build.BRAND.replaceAll("[^A-Za-z0-9]", "") : "UNK";
            String model = Build.MODEL != null ? Build.MODEL.replaceAll("[^A-Za-z0-9]", "") : "UNK";

            fingerprint.append(brand).append("_");
            fingerprint.append(model).append("_");
            fingerprint.append(Build.VERSION.SDK_INT).append("_");

            String androidId = null;
            if (appContext != null) {
                try {
                    androidId = Settings.Secure.getString(
                            appContext.getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Could not get Android ID", e);
                }
            }

            if (androidId != null && !androidId.equals("9774d56d682e549c")) {
                fingerprint.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                String hardwareInfo = brand + model + Build.VERSION.SDK_INT;
                fingerprint.append(String.format("%08X", hardwareInfo.hashCode()));
            }

            deviceFingerprint = fingerprint.toString();
            savePlayerData();

            Log.d(TAG, "Generated device fingerprint: " + deviceFingerprint);

        } catch (Exception e) {
            Log.e(TAG, "Error generating device fingerprint", e);
            deviceFingerprint = "FALLBACK_DEVICE";
        }
    }

    public static synchronized String generateUniqueId() {
        if (appContext == null) {
            throw new IllegalStateException("PlayerIdGenerator not initialized. Call initialize(context) first.");
        }

        long currentTime = System.currentTimeMillis();

        Log.d(TAG, "Current system time: " + currentTime);
        Log.d(TAG, "Current date: " + new Date(currentTime).toString());

        long maxAllowedTime = System.currentTimeMillis();
        if (currentTime > maxAllowedTime) {
            Log.w(TAG, "System time appears to be in future, adjusting");
            currentTime = maxAllowedTime;
        }

        if (currentTime - lastGenerationTime < MIN_GENERATION_INTERVAL) {
            long waitTime = MIN_GENERATION_INTERVAL - (currentTime - lastGenerationTime);
            try {
                Thread.sleep(waitTime);
                currentTime = System.currentTimeMillis();
                if (currentTime > System.currentTimeMillis()) {
                    currentTime = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ensureDeviceFingerprint();

        try {
            int deviceHash = Math.abs(deviceFingerprint.hashCode());
            int sessionRandom = (int)(Math.random() * 10000);

            String playerId = String.format("ANDROID_PLAYER_%d_%08X_%04d",
                    currentTime, deviceHash, sessionRandom);

            Log.d(TAG, "Generated player ID: " + playerId);
            Log.d(TAG, "Timestamp component: " + currentTime);
            Log.d(TAG, "Device hash component: " + String.format("%08X", deviceHash));

            if (isValidPlayerIdFormat(playerId)) {
                currentPlayerId = playerId;
                lastGenerationTime = currentTime;
                savePlayerData();

                Log.d(TAG, "Player ID validation passed");
                return playerId;

            } else {
                Log.e(TAG, "Generated player ID failed validation: " + playerId);
                throw new RuntimeException("Failed to generate valid player ID");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error generating player ID", e);
            throw new RuntimeException("Player ID generation failed", e);
        }
    }

    public static synchronized String getOrCreatePlayerId() {
        if (appContext == null) {
            throw new IllegalStateException("PlayerIdGenerator not initialized. Call initialize(context) first.");
        }

        if (isValidAndFresh(currentPlayerId, lastGenerationTime)) {
            Log.d(TAG, "Using existing valid player ID");
            return currentPlayerId;
        }

        Log.d(TAG, "Generating new player ID (existing invalid/expired)");
        return generateUniqueId();
    }

    public static synchronized void forceRegenerateId() {
        Log.d(TAG, "Force regenerating player ID - clearing cache");
        clearPlayerId();
        currentPlayerId = null;
        lastGenerationTime = 0;
    }

    public static synchronized void setPlayerId(String playerId) {
        if (!isValidPlayerIdFormat(playerId)) {
            Log.w(TAG, "Attempted to set invalid player ID: " + playerId);
            return;
        }

        if (!isReasonableForDevice(playerId)) {
            Log.w(TAG, "Player ID doesn't match device fingerprint: " + playerId);
        }

        currentPlayerId = playerId;
        lastGenerationTime = System.currentTimeMillis();
        savePlayerData();

        Log.d(TAG, "Set player ID: " + playerId);
    }

    public static synchronized String getPlayerId() {
        return currentPlayerId;
    }

    public static boolean isValidPlayerId(String playerId) {
        return isValidPlayerIdFormat(playerId);
    }

    public static synchronized void clearPlayerId() {
        currentPlayerId = null;
        lastGenerationTime = 0;

        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .remove(KEY_PLAYER_ID)
                    .remove(KEY_LAST_GENERATED)
                    .apply();
        }

        Log.d(TAG, "Cleared player ID");
    }

    public static String getDeviceFingerprint() {
        ensureDeviceFingerprint();
        return deviceFingerprint;
    }

    private static boolean isValidPlayerIdFormat(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            Log.d(TAG, "Player ID is null or empty");
            return false;
        }

        String trimmed = playerId.trim();

        if (trimmed.length() < 8 || trimmed.length() > 64) {
            Log.d(TAG, "Player ID length invalid: " + trimmed.length());
            return false;
        }

        if (!trimmed.startsWith("ANDROID_PLAYER_")) {
            Log.d(TAG, "Player ID prefix invalid");
            return false;
        }

        String[] parts = trimmed.split("_");
        if (parts.length < 4) {
            Log.d(TAG, "Player ID structure invalid, parts: " + parts.length);
            return false;
        }

        try {
            long timestamp = Long.parseLong(parts[2]);
            long now = System.currentTimeMillis();
            long age = now - timestamp;

            Log.d(TAG, "Validating timestamp: " + timestamp + " vs current: " + now + ", age: " + age);

            if (age < -60000) {
                Log.d(TAG, "Player ID timestamp is too far in future: " + age);
                return false;
            }
            if (age > 24 * 60 * 60 * 1000) {
                Log.d(TAG, "Player ID timestamp too old: " + age);
                return false;
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "Player ID timestamp invalid: " + parts[2]);
            return false;
        }

        try {
            Integer.parseInt(parts[3], 16);
        } catch (NumberFormatException e) {
            Log.d(TAG, "Player ID device hash invalid: " + parts[3]);
            return false;
        }

        if (parts.length >= 5) {
            try {
                Integer.parseInt(parts[4]);
            } catch (NumberFormatException e) {
                Log.d(TAG, "Player ID random component invalid: " + parts[4]);
                return false;
            }
        }

        return true;
    }

    private static boolean isValidAndFresh(String playerId, long generationTime) {
        if (!isValidPlayerIdFormat(playerId)) {
            return false;
        }

        try {
            String[] parts = playerId.split("_");
            long playerIdTimestamp = Long.parseLong(parts[2]);
            long now = System.currentTimeMillis();

            if (playerIdTimestamp > now + 60000) {
                Log.w(TAG, "Player ID timestamp is in future: " + playerIdTimestamp + " vs " + now);
                Log.w(TAG, "Future timestamp date: " + new Date(playerIdTimestamp));
                Log.w(TAG, "Current date: " + new Date(now));
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not validate player ID timestamp", e);
            return false;
        }

        long age = System.currentTimeMillis() - generationTime;
        long maxAge = SESSION_VALIDITY_HOURS * 60 * 60 * 1000;

        if (age > maxAge) {
            Log.d(TAG, "Player ID expired (age: " + (age / 60000) + " minutes)");
            return false;
        }

        return true;
    }

    private static boolean isReasonableForDevice(String playerId) {
        if (deviceFingerprint == null) {
            return true;
        }

        try {
            String[] parts = playerId.split("_");
            if (parts.length >= 4) {
                String playerDeviceHash = parts[3];
                String expectedDeviceHash = String.format("%08X", Math.abs(deviceFingerprint.hashCode()));

                return playerDeviceHash.equals(expectedDeviceHash);
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not validate device consistency", e);
        }

        return true;
    }

    private static boolean isValidFingerprint(String fingerprint) {
        return fingerprint != null &&
                fingerprint.length() > 5 &&
                fingerprint.matches("^[A-Za-z0-9_]+$");
    }
}

// ===== DEVICE ID GENERATOR (Same as before, included for completeness) =====

class DeviceIdGenerator {
    private static final String TAG = "DeviceIdGenerator";

    private static final String PREFS_NAME = "naarpazham_device_prefs";
    private static final String KEY_DEVICE_ID = "device_id";

    private static String currentDeviceId = null;
    private static Context appContext = null;

    public static synchronized void initialize(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
            loadPersistedDeviceId();
            Log.d(TAG, "DeviceIdGenerator initialized");
        }
    }

    private static void loadPersistedDeviceId() {
        if (appContext == null) return;

        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String savedDeviceId = prefs.getString(KEY_DEVICE_ID, null);

            if (isValidDeviceId(savedDeviceId)) {
                currentDeviceId = savedDeviceId;
                Log.d(TAG, "Restored device ID from storage: " + currentDeviceId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading device ID", e);
        }
    }

    public static synchronized String getOrCreateDeviceId() {
        if (appContext == null) {
            throw new IllegalStateException("DeviceIdGenerator not initialized. Call initialize(context) first.");
        }

        if (currentDeviceId != null && isValidDeviceId(currentDeviceId)) {
            return currentDeviceId;
        }

        currentDeviceId = generateDeviceId();
        saveDeviceId();

        return currentDeviceId;
    }

    private static String generateDeviceId() {
        try {
            StringBuilder deviceId = new StringBuilder("DEVICE_");

            String brand = Build.BRAND != null ? Build.BRAND.replaceAll("[^A-Za-z0-9]", "") : "UNK";
            String model = Build.MODEL != null ? Build.MODEL.replaceAll("[^A-Za-z0-9]", "") : "UNK";

            deviceId.append(brand).append("_");
            deviceId.append(model).append("_");
            deviceId.append(Build.VERSION.SDK_INT).append("_");

            String androidId = null;
            if (appContext != null) {
                try {
                    androidId = Settings.Secure.getString(
                            appContext.getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Could not get Android ID for device ID", e);
                }
            }

            if (androidId != null && !androidId.equals("9774d56d682e549c")) {
                deviceId.append(androidId.substring(0, Math.min(8, androidId.length())));
            } else {
                String fallbackId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                deviceId.append(fallbackId);
            }

            String result = deviceId.toString();
            Log.d(TAG, "Generated device ID: " + result);
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error generating device ID", e);
            return "DEVICE_FALLBACK_" + System.currentTimeMillis();
        }
    }

    private static void saveDeviceId() {
        if (appContext == null || currentDeviceId == null) return;

        try {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_DEVICE_ID, currentDeviceId).apply();
            Log.d(TAG, "Device ID saved to storage");
        } catch (Exception e) {
            Log.e(TAG, "Error saving device ID", e);
        }
    }

    private static boolean isValidDeviceId(String deviceId) {
        return deviceId != null &&
                deviceId.startsWith("DEVICE_") &&
                deviceId.length() > 10 &&
                deviceId.matches("^[A-Za-z0-9_]+$");
    }

    public static String getDeviceId() {
        return currentDeviceId;
    }

    public static synchronized void clearDeviceId() {
        currentDeviceId = null;

        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(KEY_DEVICE_ID).apply();
        }

        Log.d(TAG, "Device ID cleared");
    }
}