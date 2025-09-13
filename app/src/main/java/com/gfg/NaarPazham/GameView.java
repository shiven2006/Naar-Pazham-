package com.gfg.NaarPazham;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.ref.WeakReference;

public class GameView extends View implements GamePollingService.PollingCallback {
    private static final String TAG = "GameView";

    // Core game components
    private GameState gameState;
    private Board board;
    private GameLogic gameLogic;
    private GameRenderer gameRenderer;
    private final NetworkService networkService;

    // Screen dimensions
    private int screenWidth;
    private int screenHeight;
    private boolean isInitialized = false;

    // FIXED: Proper handler management
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TurnUpdateTask turnUpdateTask = null;

    private MainActivity mainActivity;

    // Multiplayer state with proper authentication
    private String currentGameId = null;
    private String currentPlayerId = null;
    private boolean isGameReady = false;
    private boolean isLocalPlayer1 = true;
    private boolean waitingForSecondPlayer = false;
    private GamePollingService pollingService = null;

    // Local Mode support
    private boolean isLocalMode = false;
    private LocalGameManager localGameManager = null;

    // FIXED: Interface-based communication
    private UIUpdateListener uiListener;
    private GameStateListener gameStateListener;

    // FIXED: Lifecycle management
    private volatile boolean isActivityPaused = false;
    private volatile boolean isViewDestroyed = false;

    // FIXED: Enhanced task class with proper lifecycle management
    private static class TurnUpdateTask implements Runnable {
        private final WeakReference<GameView> gameViewRef;
        private volatile boolean isCancelled = false;

        TurnUpdateTask(GameView gameView) {
            this.gameViewRef = new WeakReference<>(gameView);
        }

        public void cancel() {
            isCancelled = true;
        }

        @Override
        public void run() {
            GameView gameView = gameViewRef.get();
            if (gameView != null && !gameView.gameState.isGameOver() &&
                    !gameView.isActivityPaused && !gameView.isViewDestroyed && !isCancelled) {
                gameView.updateTurnStatus();
            }
        }
    }

    public GameView(Context context, Player player1, Player player2) {
        super(context);
        this.networkService = NetworkService.getInstance(context);
        gameState = new GameState(player1, player2);
        initializeComponents();
    }

    public GameView(Context context) {
        super(context);
        this.networkService = NetworkService.getInstance(context);
        gameState = new GameState();
        initializeComponents();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.networkService = NetworkService.getInstance(context);
        gameState = new GameState();
        initializeComponents();
    }

    private void initializeComponents() {
        board = new Board();
        gameLogic = new GameLogic(gameState, board, networkService);
    }

    public void setLocalMode(boolean localMode) {
        this.isLocalMode = localMode;
        Log.d(TAG, "Local mode set to: " + localMode);
    }

    public void setLocalGameManager(LocalGameManager manager) {
        this.localGameManager = manager;
        if (manager != null) {
            manager.setCallback(new LocalGameManager.LocalGameCallback() {
                @Override
                public void onPlayerTurnChanged(boolean isPlayer1Turn) {
                    if (!isViewDestroyed && !isActivityPaused) {
                        updateTurnStatus();
                        invalidate();
                    }
                }

                @Override
                public void onGameStateUpdated() {
                    if (!isViewDestroyed && !isActivityPaused) {
                        invalidate();
                    }
                }

                @Override
                public void onGameWon(Player winner) {
                    if (uiListener != null && !isViewDestroyed) {
                        String winnerName = winner.isPlayer1() ? "Player 1 (Red)" : "Player 2 (Blue)";
                        uiListener.showGameOver(winnerName + " wins!");
                    }
                }

                @Override
                public void onMoveResult(boolean success, String message) {
                    if (uiListener != null && !isViewDestroyed && !success) {
                        showStatusMessage(message);
                    }
                }

                @Override
                public void onGameStarted() {
                    if (!isViewDestroyed && !isActivityPaused) {
                        isGameReady = true;
                        showStatusMessage("Local game started!");
                        invalidate();
                    }
                }

                @Override
                public void onGameReset() {
                    if (!isViewDestroyed && !isActivityPaused) {
                        isGameReady = false;
                        showStatusMessage("Game reset");
                        invalidate();
                    }
                }
            });
        }
        Log.d(TAG, "LocalGameManager set: " + (manager != null));
    }


    public LocalGameManager getLocalGameManager() {
        return localGameManager;
    }

    public boolean isLocalMode() {
        return isLocalMode;
    }

    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isViewDestroyed) {
            Log.d(TAG, "Skipping draw - view destroyed");
            return;
        }

        super.onDraw(canvas);

        if (!isInitialized) {
            screenWidth = canvas.getWidth();
            screenHeight = canvas.getHeight();

            board.initialize(screenWidth, screenHeight);
            gameRenderer = new GameRenderer(board, gameState);

            gameState.getPlayer1().setPlayerSpriteSize(board.getHoleSize());
            gameState.getPlayer2().setPlayerSpriteSize(board.getHoleSize());

            isInitialized = true;
            Log.d(TAG, "GameView initialized with screen size: " + screenWidth + "x" + screenHeight);
        }

        if (gameRenderer != null) {
            gameRenderer.render(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isInitialized || isActivityPaused || isViewDestroyed) {
            Log.d(TAG, "Touch ignored - not initialized or activity paused/destroyed");
            return false;
        }

        // ADDED: Handle local mode
        if (isLocalMode) {
            if (localGameManager == null) {
                showStatusMessage("Local game not initialized");
                return false;
            }
            return handleLocalTouch(e);
        }


        if (!isGameReady || currentGameId == null) {
            showStatusMessage("Game not ready. Please start or join a game first.");
            return false;
        }

        if (waitingForSecondPlayer) {
            showStatusMessage("Waiting for second player to join...");
            return false;
        }

        if (gameState.isGameOver()) {
            if (uiListener != null) {
                String winnerMsg = "Player " + (gameState.getWinner().isPlayer1() ? "1" : "2") + " wins!";
                uiListener.showGameOver(winnerMsg);
            }
            return true;
        }

        // Check if it's this player's turn
        if (!isMyTurn()) {
            showTemporaryMsg("Wait for opponent's turn");
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            int touchX = (int) e.getX();
            int touchY = (int) e.getY();

            Point boardPos = board.findValidPos(touchX, touchY);
            if (boardPos == null) {
                showTemporaryMsg("Invalid touch position - not on board");
                if (gameState.isMovementPhase()) {
                    gameState.deselectPiece();
                    if (!isActivityPaused && !isViewDestroyed) {
                        invalidate();
                    }
                }
                return true;
            }

            if (gameState.isPlacementPhase()) {
                handlePlacement(touchX, touchY);
            } else if (gameState.isMovementPhase()) {
                handleMovement(boardPos);
            }
        }
        return true;
    }


    // FIXED: Improved lifecycle management
    public void pauseGameActivity() {
        Log.d(TAG, "Pausing game activity");
        isActivityPaused = true;

        // Pause polling when activity is paused
        pausePollingIfActive();

        // Cancel any pending UI updates
        cancelTurnUpdateTask();
    }

    public void resumeGameActivity() {
        Log.d(TAG, "Resuming game activity");
        isActivityPaused = false;

        // Only resume if view isn't destroyed
        if (!isViewDestroyed) {
            // Resume polling if we have an active game
            if (isGameReady && !gameState.isGameOver() && currentGameId != null) {
                resumePollingIfActive();
            }

            // Update turn status
            if (!gameState.isGameOver()) {
                updateTurnStatus();
            }
        }
    }

    private boolean handleLocalTouch(MotionEvent e) {
        if (gameState.isGameOver()) {
            if (uiListener != null) {
                String winnerMsg = "Player " + (gameState.getWinner().isPlayer1() ? "1" : "2") + " wins!";
                uiListener.showGameOver(winnerMsg);
            }
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            int touchX = (int) e.getX();
            int touchY = (int) e.getY();

            // Use LocalGameManager's handleTouchMove method directly
            // It will handle board position validation internally
            localGameManager.handleTouchMove(touchX, touchY, gameState.getSelectedPiece());

            // Always invalidate to refresh the display
            invalidate();
        }
        return true;
    }

    // FIXED: Comprehensive view destruction
    public void destroyView() {
        Log.d(TAG, "Destroying GameView");
        isViewDestroyed = true;

        // Stop all activities immediately
        stopPolling();
        cleanupResources();

        // Clear references
        uiListener = null;
        gameStateListener = null;
    }

    // FIXED: Enhanced cleanup
    private void cleanupResources() {
        Log.d(TAG, "Cleaning up GameView resources");

        cancelTurnUpdateTask();

        if (pollingService != null) {
            try {
                pollingService.stopPolling();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping polling service", e);
            }
            pollingService = null;
        }

        try {
            if (networkService != null) {
                networkService.cleanup();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up network service", e);
        }

        // Clear any pending UI handler tasks
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
    }

    private boolean isMyTurn() {
        boolean isPlayer1Turn = gameState.getCurrentPlayer().isPlayer1();
        return (isLocalPlayer1 && isPlayer1Turn) || (!isLocalPlayer1 && !isPlayer1Turn);
    }

    // UPDATED: Use NetworkService's processMove method
    // FIXED: handlePlacement method in GameView.java
    private void handlePlacement(int touchX, int touchY) {
        if (isViewDestroyed || isActivityPaused) {
            Log.d(TAG, "Ignoring placement - view destroyed or paused");
            return;
        }

        // Pause polling during network operations
        pausePollingIfActive();

        // Validate player ID
        if (!PlayerIdGenerator.isValidPlayerId(currentPlayerId)) {
            showTemporaryMsg("Invalid player authentication. Please restart the game.");
            resumePollingIfActive();
            return;
        }

        // Convert touch coordinates to board coordinates
        Point boardPos = board.findValidPos(touchX, touchY);
        if (boardPos == null) {
            showTemporaryMsg("Invalid placement position");
            resumePollingIfActive();
            return;
        }

        // CRITICAL FIX: Convert board position to grid coordinates
        int[] gridCoords = board.getGridPos(boardPos.x, boardPos.y);
        if (gridCoords == null || gridCoords.length < 2 || gridCoords[0] == -1 || gridCoords[1] == -1) {
            showTemporaryMsg("Cannot determine grid position");
            resumePollingIfActive();
            return;
        }

        // Grid coordinates: gridCoords[0] = row, gridCoords[1] = column
        int gridX = gridCoords[1]; // Column
        int gridY = gridCoords[0]; // Row

        Log.d(TAG, "Processing placement at grid position: " + gridX + "," + gridY);

        // Use NetworkService to process the placement move with GRID coordinates
        networkService.processMove(currentGameId, currentPlayerId,
                gridX, gridY, null, null,  // Pass grid coordinates, not board coordinates
                new NetworkService.GameCallback() {
                    @Override
                    public void onSuccess(ServerGameState serverGameState) {
                        if (!isViewDestroyed && !isActivityPaused) {
                            try {
                                updateFromServerState(serverGameState);
                                resumePollingIfActive();
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating game state", e);
                                showTemporaryMsg("Error updating game state: " + e.getMessage());
                                resumePollingIfActive();
                            }
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        if (!isViewDestroyed && !isActivityPaused) {
                            showTemporaryMsg(message != null ? message : "Placement failed");
                            resumePollingIfActive();
                        }
                    }
                });
    }

    // UPDATED: Use NetworkService's processMove method for movements
    private void handleMovement(Point boardPos) {
        if (isViewDestroyed || isActivityPaused) {
            Log.d(TAG, "Ignoring movement - view destroyed or paused");
            return;
        }

        Player pieceAtPosition = gameLogic.findPlayerAtPosition(boardPos);

        if (pieceAtPosition != null) {
            // Player clicked on a piece
            if (pieceAtPosition.isPlayer1() == isLocalPlayer1) {
                // It's this player's piece - select it
                gameState.selectPiece(pieceAtPosition);
            } else {
                showTemporaryMsg("Can't click on opponent's piece");
            }
        } else {
            // Player clicked on empty space
            if (gameState.hasSelectedPiece()) {
                // Try to move selected piece
                Point fromPos = findPiecePosition(gameState.getSelectedPiece());
                if (fromPos == null) {
                    showTemporaryMsg("Error finding piece position");
                    return;
                }

                pausePollingIfActive();

                // Validate player ID
                if (!PlayerIdGenerator.isValidPlayerId(currentPlayerId)) {
                    showTemporaryMsg("Invalid player authentication. Please restart the game.");
                    resumePollingIfActive();
                    return;
                }

                // Convert positions to grid coordinates
                int[] fromGridCoords = board.getGridPos(fromPos.x, fromPos.y);
                int[] toGridCoords = board.getGridPos(boardPos.x, boardPos.y);

                if (fromGridCoords[0] == -1 || fromGridCoords[1] == -1 ||
                        toGridCoords[0] == -1 || toGridCoords[1] == -1) {
                    showTemporaryMsg("Invalid move coordinates");
                    resumePollingIfActive();
                    return;
                }

                Log.d(TAG, "Processing movement from grid " + fromGridCoords[1] + "," + fromGridCoords[0] +
                        " to " + toGridCoords[1] + "," + toGridCoords[0]);

                // Use NetworkService to process the movement
                // Note: Server expects (x, y) where x is column, y is row
                networkService.processMove(currentGameId, currentPlayerId,
                        toGridCoords[1], toGridCoords[0],
                        fromGridCoords[1], fromGridCoords[0],
                        new NetworkService.GameCallback() {
                            @Override
                            public void onSuccess(ServerGameState serverGameState) {
                                if (!isViewDestroyed && !isActivityPaused) {
                                    try {
                                        gameState.deselectPiece();
                                        updateFromServerState(serverGameState);
                                        resumePollingIfActive();
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error updating game state", e);
                                        showTemporaryMsg("Error updating game state: " + e.getMessage());
                                        resumePollingIfActive();
                                    }
                                }
                            }

                            @Override
                            public void onFailure(String message) {
                                if (!isViewDestroyed && !isActivityPaused) {
                                    showTemporaryMsg(message != null ? message : "Movement failed");
                                    resumePollingIfActive();
                                }
                            }
                        });
            } else {
                showTemporaryMsg("No piece selected");
            }
        }

        if (!isViewDestroyed && !isActivityPaused) {
            invalidate();
        }
    }

    // FIXED: Enhanced piece position finding with null safety
    private Point findPiecePosition(Player piece) {
        if (piece == null) {
            Log.e(TAG, "Cannot find position of null piece");
            return null;
        }

        try {
            int spriteSize = board.getHoleSize() * 2;
            int centerX = piece.getX() + spriteSize / 2;
            int centerY = piece.getY() + spriteSize / 2;
            return new Point(centerX, centerY);
        } catch (Exception e) {
            Log.e(TAG, "Error finding piece position", e);
            return null;
        }
    }

    // FIXED: Enhanced error handling for server state updates
    private void updateFromServerState(ServerGameState serverGameState) {
        if (isViewDestroyed || isActivityPaused) {
            Log.d(TAG, "Ignoring server state update - view destroyed or paused");
            return;
        }

        try {
            if (serverGameState == null) {
                throw new IllegalArgumentException("Server game state is null");
            }

            GameStateConverter.convertAndUpdateLocalState(serverGameState, gameState, board);

            if (gameState.isGameOver()) {
                if (uiListener != null) {
                    String winnerName = gameState.getWinner().isPlayer1() ? "Player 1" : "Player 2";
                    uiListener.showGameOver(winnerName + " wins!");
                }
                stopPolling();
            } else {
                updateTurnStatus();
            }

            if (!isViewDestroyed && !isActivityPaused) {
                invalidate();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing server response", e);
            showTemporaryMsg("Error processing server response: " + e.getMessage());
        }
    }

    private void updateTurnStatus() {
        if (uiListener == null || isViewDestroyed || isActivityPaused) return;

        try {
            if (isLocalMode) {
                // For local mode, show which player's turn it is
                String currentPlayerName = gameState.getCurrentPlayer().isPlayer1() ?
                        "Player 1 (Red)" : "Player 2 (Blue)";
                uiListener.updateStatus(currentPlayerName + "'s turn");
            } else if (waitingForSecondPlayer) {
                uiListener.updateStatus("Waiting for second player to join...");
            } else if (isMyTurn()) {
                uiListener.updateStatus("Your turn");
            } else {
                uiListener.updateStatus("Opponent's turn");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error updating turn status", e);
        }
    }
    // FIXED: Enhanced polling callbacks with better error handling
    @Override
    public void onGameStateUpdated(ServerGameState gameState) {
        if (isActivityPaused || isViewDestroyed) {
            Log.d(TAG, "Ignoring game state update - activity paused or destroyed");
            return;
        }

        try {
            updateTurnStatus();
        } catch (Exception e) {
            Log.w(TAG, "Error updating turn status", e);
        }
    }

    @Override
    public void onOpponentMove(ServerGameState serverGameState) {
        if (isActivityPaused || isViewDestroyed) {
            Log.d(TAG, "Ignoring opponent move - activity paused or destroyed");
            return;
        }

        try {
            updateFromServerState(serverGameState);

            // Show notification of opponent's move
            if (uiListener != null && !gameState.isGameOver()) {
                showTemporaryMsg("Opponent made a move!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing opponent's move", e);
            showTemporaryMsg("Error processing opponent's move: " + e.getMessage());
        }
    }

    @Override
    public void onGameEnded(ServerGameState serverGameState) {
        if (isViewDestroyed) {
            Log.d(TAG, "Ignoring game end - view destroyed");
            return;
        }

        try {
            updateFromServerState(serverGameState);
        } catch (Exception e) {
            Log.e(TAG, "Error processing game end", e);
            if (uiListener != null) {
                uiListener.showGameOver("Game ended unexpectedly");
            }
        }
    }

    @Override
    public void onPollingError(String error) {
        if (isActivityPaused || isViewDestroyed) {
            Log.d(TAG, "Ignoring polling error - activity paused or destroyed");
            return;
        }

        if (uiListener != null) {
            showTemporaryMsg("Connection error: " + (error != null ? error : "Unknown error"));
        }
    }

    @Override
    public void onPlayerJoined() {
        if (isActivityPaused || isViewDestroyed) {
            Log.d(TAG, "Ignoring player joined - activity paused or destroyed");
            return;
        }

        waitingForSecondPlayer = false;

        if (uiListener != null) {
            showTemporaryMsg("Opponent joined! Game started!");
        }

        // Refresh game state to get latest status
        if (currentGameId != null && currentPlayerId != null && !isActivityPaused && !isViewDestroyed) {
            try {
                networkService.getGameState(currentGameId, currentPlayerId, new NetworkService.GameCallback() {
                    @Override
                    public void onSuccess(ServerGameState serverGameState) {
                        if (!isActivityPaused && !isViewDestroyed) {
                            updateFromServerState(serverGameState);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isActivityPaused && !isViewDestroyed) {
                            updateTurnStatus();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing game state", e);
                if (!isActivityPaused && !isViewDestroyed) {
                    updateTurnStatus();
                }
            }
        }
    }

    // FIXED: Enhanced UI message handling with proper cleanup
    private void showTemporaryMsg(String msg) {
        if (isActivityPaused || isViewDestroyed) {
            Log.d(TAG, "Skipping temporary message - activity paused or destroyed");
            return;
        }

        showStatusMessage(msg);

        // Cancel previous task
        cancelTurnUpdateTask();

        // Schedule turn status update
        if (!isActivityPaused && !isViewDestroyed) {
            turnUpdateTask = new TurnUpdateTask(this);
            uiHandler.postDelayed(turnUpdateTask, 2000);
        }
    }

    private void showStatusMessage(String msg) {
        if (uiListener != null && !isViewDestroyed) {
            try {
                uiListener.updateStatus(msg != null ? msg : "Unknown status");
            } catch (Exception e) {
                Log.w(TAG, "Error updating UI status", e);
            }
        }
    }

    private void cancelTurnUpdateTask() {
        if (turnUpdateTask != null) {
            turnUpdateTask.cancel();
            uiHandler.removeCallbacks(turnUpdateTask);
            turnUpdateTask = null;
        }
    }

    // FIXED: Resource management helpers
    private void pausePollingIfActive() {
        if (pollingService != null && pollingService.isPolling()) {
            pollingService.stopPolling();
        }
    }

    private void resumePollingIfActive() {
        if (pollingService != null && !gameState.isGameOver() &&
                isGameReady && !isViewDestroyed && !isActivityPaused) {
            pollingService.startPolling();
        }
    }

    // Public methods with improved error handling
    public GameState getGameState() {
        return gameState;
    }

    public Board getBoard() {
        return board;
    }

    // FIXED: Use interface instead of direct coupling
    public void setUIUpdateListener(UIUpdateListener listener) {
        this.uiListener = listener;
    }

    public void setGameStateListener(GameStateListener listener) {
        this.gameStateListener = listener;
    }

    // FIXED: Enhanced cleanup
    public void resetGame() {
        try {
            Log.d(TAG, "Resetting game");

            stopPolling();
            cleanupResources();

            gameState.reset();
            isGameReady = false;
            currentGameId = null;
            currentPlayerId = null;
            waitingForSecondPlayer = false;
            isActivityPaused = false;

            if (isInitialized && !isViewDestroyed) {
                invalidate();
            }

            showStatusMessage("Game reset. Start or join a game to begin");
        } catch (Exception e) {
            Log.e(TAG, "Error during game reset", e);
            showStatusMessage("Error during game reset: " + e.getMessage());
        }
    }

    // REMOVED: createNewGame() - this should be handled by the matchmaking system
    // REMOVED: joinGame() - this should be handled by the matchmaking system

    private void startPolling() {
        if (isViewDestroyed) {
            Log.d(TAG, "Not starting polling - view destroyed");
            return;
        }

        try {
            if (currentGameId != null && currentPlayerId != null && pollingService == null) {
                pollingService = new GamePollingService(
                        currentGameId,
                        currentPlayerId,
                        networkService,
                        this // GameView implements GamePollingService.PollingCallback
                );
                pollingService.startPolling();
            } else if (pollingService != null && !pollingService.isPolling()) {
                pollingService.startPolling();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting polling", e);
            showStatusMessage("Error starting game polling: " + e.getMessage());
        }
    }

    private void stopPolling() {
        if (pollingService != null) {
            try {
                pollingService.stopPolling();
            } catch (Exception e) {
                Log.w(TAG, "Error stopping polling", e);
            }
        }
    }

    // UPDATED: Method called when matchmaking finds a game
    public void setMatchedGame(ServerGameState serverGameState, boolean isPlayer1, String playerId) {
        if (isViewDestroyed) {
            Log.d(TAG, "Ignoring matched game - view destroyed");
            return;
        }

        try {
            // CRITICAL: Additional validation before accepting match
            if (serverGameState == null || playerId == null) {
                showStatusMessage("Invalid matchmaking data received");
                return;
            }

            // Validate game state integrity
            String player1Id = serverGameState.getPlayer1Id();
            String player2Id = serverGameState.getPlayer2Id();

            if (player1Id == null || player2Id == null) {
                Log.e(TAG, "Missing player IDs in matched game");
                showStatusMessage("Invalid match data - missing player information");
                return;
            }

            // CRITICAL: Final self-match check
            if (player1Id.equals(player2Id)) {
                Log.e(TAG, "CRITICAL: Self-match detected in setMatchedGame! Player: " + player1Id);
                showStatusMessage("Match error detected - please try again");

                // Notify activity about invalid match
                if (gameStateListener != null) {
                    gameStateListener.onGameJoinFailed("Invalid match detected");
                }
                return;
            }

            // Verify current player is in the match
            if (!playerId.equals(player1Id) && !playerId.equals(player2Id)) {
                Log.e(TAG, "Current player not found in match - Player: " + playerId +
                        ", P1: " + player1Id + ", P2: " + player2Id);
                showStatusMessage("Player not found in match");
                return;
            }

            Log.d(TAG, "Setting up matched game - Game: " + serverGameState.getGameId() +
                    ", Player: " + playerId + ", isPlayer1: " + isPlayer1);

            currentGameId = serverGameState.getGameId();
            currentPlayerId = playerId;
            isGameReady = true;
            isLocalPlayer1 = isPlayer1;
            waitingForSecondPlayer = false;

            gameState.reset();
            updateFromServerState(serverGameState);
            startPolling();

            if (gameStateListener != null) {
                gameStateListener.onGameJoined(currentGameId, isPlayer1);
            }

            String playerRole = isPlayer1 ? "Player 1" : "Player 2";
            showStatusMessage("Matched as " + playerRole + "! " +
                    (isMyTurn() ? "Your turn" : "Opponent's turn"));

            Log.i(TAG, "Successfully set up matched game");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up matched game", e);
            showStatusMessage("Error setting up matched game: " + e.getMessage());

            if (gameStateListener != null) {
                gameStateListener.onGameJoinFailed("Error setting up game: " + e.getMessage());
            }
        }
    }

    // NEW: Method to leave current game
    public void leaveCurrentGame() {
        if (currentGameId == null || currentPlayerId == null) {
            Log.d(TAG, "No active game to leave");
            return;
        }

        if (isViewDestroyed) {
            Log.d(TAG, "Ignoring leave game - view destroyed");
            return;
        }

        String gameIdToLeave = currentGameId;
        String playerIdToLeave = currentPlayerId;

        // Reset game state immediately
        resetGame();

        // Send leave request to server
        networkService.leaveGame(gameIdToLeave, playerIdToLeave, new NetworkService.GameCallback() {
            @Override
            public void onSuccess(ServerGameState gameState) {
                Log.d(TAG, "Successfully left game: " + gameIdToLeave);
                if (!isViewDestroyed) {
                    showStatusMessage("Left game successfully");
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w(TAG, "Failed to leave game: " + errorMessage);
                if (!isViewDestroyed) {
                    showStatusMessage("Left game (with errors)");
                }
            }
        });
    }

    // Getters
    public String getCurrentGameId() {
        return currentGameId;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public boolean isLocalPlayer1() {
        return isLocalPlayer1;
    }

    public boolean isGameReady() {
        return isGameReady;
    }
}