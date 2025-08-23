package com.gfg.NaarPazham;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View implements GamePollingService.PollingCallback {
    // Core game components
    private GameState gameState;
    private Board board;
    private GameLogic gameLogic;
    private GameRenderer gameRenderer;

    // Screen dimensions
    private int screenWidth;
    private int screenHeight;
    private boolean isInitialized = false;

    // UI
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable turnUpdateRunnable = null;

    // UPDATED: Multiplayer state with player authentication
    private String currentGameId = null;
    private String currentPlayerId = null; // ADD: Store player ID for authentication
    private boolean isGameReady = false;
    private boolean isLocalPlayer1 = true; // Which player this client is
    private boolean waitingForSecondPlayer = false;
    private GamePollingService pollingService = null;

    private UIUpdateListener uiListener;

    public GameView(Context context, Player player1, Player player2) {
        super(context);
        gameState = new GameState(player1, player2);
        initializeComponents();
    }

    public GameView(Context context) {
        super(context);
        gameState = new GameState();
        initializeComponents();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        gameState = new GameState();
        initializeComponents();
    }

    private void initializeComponents() {
        board = new Board();
        gameLogic = new GameLogic(gameState, board, new NetworkService());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isInitialized) {
            screenWidth = canvas.getWidth();
            screenHeight = canvas.getHeight();

            board.initialize(screenWidth, screenHeight);
            gameRenderer = new GameRenderer(board, gameState);

            gameState.getPlayer1().setPlayerSpriteSize(board.getHoleSize());
            gameState.getPlayer2().setPlayerSpriteSize(board.getHoleSize());

            isInitialized = true;
        }

        gameRenderer.render(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isInitialized) {
            return false;
        }

        if (!isGameReady || currentGameId == null) {
            if (uiListener != null) {
                uiListener.updateStatus("Game not ready. Please start or join a game first.");
            }
            return false;
        }

        if (waitingForSecondPlayer) {
            if (uiListener != null) {
                uiListener.updateStatus("Waiting for second player to join...");
            }
            return false;
        }

        if (gameState.isGameOver()) {
            if (uiListener != null) {
                uiListener.showGameOver("Player " + (gameState.getWinner().isPlayer1() ? "1" : "2") + " wins!");
            }
            return true;
        }

        // Check if it's this player's turn
        if (!isMyTurn()) {
            if (uiListener != null) {
                showTemporaryMsg("Wait for opponent's turn");
            }
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
                    invalidate();
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

    private boolean isMyTurn() {
        // Check if current turn matches this client's player
        boolean isPlayer1Turn = gameState.getCurrentPlayer().isPlayer1();
        return (isLocalPlayer1 && isPlayer1Turn) || (!isLocalPlayer1 && !isPlayer1Turn);
    }

    private void handlePlacement(int touchX, int touchY) {
        // Temporarily disable polling during move processing
        if (pollingService != null) {
            pollingService.stopPolling();
        }

        // UPDATED: Pass player ID for authentication
        gameLogic.validatePlacement(touchX, touchY, currentGameId, currentPlayerId, new GameLogic.PlacementCallback() {
            @Override
            public void onPlacementSuccess(ServerGameState serverGameState) {
                updateFromServerState(serverGameState);

                // Resume polling after successful move
                if (pollingService != null && !gameState.isGameOver()) {
                    pollingService.setLastKnownTotalMoves(serverGameState.getTotalMoves());
                    pollingService.startPolling();
                }
            }

            @Override
            public void onPlacementFailure(String message) {
                showTemporaryMsg(message);

                // Resume polling even after failure
                if (pollingService != null && !gameState.isGameOver()) {
                    pollingService.startPolling();
                }
            }
        });
    }

    private void handleMovement(Point boardPos) {
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

                // Temporarily stop polling during move
                if (pollingService != null) {
                    pollingService.stopPolling();
                }

                // UPDATED: Pass player ID for authentication
                gameLogic.validateMovement(fromPos, boardPos, currentGameId, currentPlayerId, new GameLogic.MovementCallback() {
                    @Override
                    public void onMovementSuccess(ServerGameState serverGameState) {
                        gameState.deselectPiece();
                        updateFromServerState(serverGameState);

                        // Resume polling
                        if (pollingService != null && !gameState.isGameOver()) {
                            pollingService.setLastKnownTotalMoves(serverGameState.getTotalMoves());
                            pollingService.startPolling();
                        }
                    }

                    @Override
                    public void onMovementFailure(String message) {
                        showTemporaryMsg(message);

                        // Resume polling even after failure
                        if (pollingService != null && !gameState.isGameOver()) {
                            pollingService.startPolling();
                        }
                    }
                });
            } else {
                showTemporaryMsg("No piece selected");
            }
        }
        invalidate();
    }

    private Point findPiecePosition(Player piece) {
        int spriteSize = board.getHoleSize() * 2;
        int centerX = piece.getX() + spriteSize / 2;
        int centerY = piece.getY() + spriteSize / 2;
        return new Point(centerX, centerY);
    }

    private void updateFromServerState(ServerGameState serverGameState) {
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

        invalidate();
    }

    private void updateTurnStatus() {
        if (uiListener == null) return;

        if (waitingForSecondPlayer) {
            uiListener.updateStatus("Waiting for second player to join...");
        } else if (isMyTurn()) {
            uiListener.updateStatus("Your turn");
        } else {
            uiListener.updateStatus("Opponent's turn");
        }
    }

    // Polling callbacks
    @Override
    public void onGameStateUpdated(ServerGameState gameState) {
        // Update UI periodically even without changes
        updateTurnStatus();
    }

    @Override
    public void onOpponentMove(ServerGameState serverGameState) {
        updateFromServerState(serverGameState);

        // Show notification of opponent's move
        if (uiListener != null && !gameState.isGameOver()) {
            showTemporaryMsg("Opponent made a move!");
        }
    }

    @Override
    public void onGameEnded(ServerGameState serverGameState) {
        updateFromServerState(serverGameState);
    }

    @Override
    public void onPollingError(String error) {
        if (uiListener != null) {
            showTemporaryMsg("Connection error: " + error);
        }
    }

    @Override
    public void onPlayerJoined() {
        // Second player joined, game can now start
        waitingForSecondPlayer = false;

        if (uiListener != null) {
            showTemporaryMsg("Opponent joined! Game started!");
        }

        // Refresh game state to get latest status
        if (currentGameId != null) {
            NetworkService networkService = new NetworkService();
            networkService.getGameState(currentGameId, new NetworkService.GameCallback() {
                @Override
                public void onSuccess(ServerGameState serverGameState) {
                    updateFromServerState(serverGameState);
                }

                @Override
                public void onFailure(String errorMessage) {
                    // Still update status even if refresh fails
                    updateTurnStatus();
                }
            });
        }
    }

    // UI
    private void showTemporaryMsg(String msg) {
        if (uiListener != null) {
            uiListener.updateStatus(msg);

            if (turnUpdateRunnable != null) {
                uiHandler.removeCallbacks(turnUpdateRunnable);
            }

            turnUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!gameState.isGameOver()) {
                        updateTurnStatus();
                    }
                }
            };
            uiHandler.postDelayed(turnUpdateRunnable, 2000);
        }
    }

    // Public methods
    public GameState getGameState() {
        return gameState;
    }

    public Board getBoard() {
        return board;
    }

    public void setListenerMethod(UIUpdateListener listener) {
        this.uiListener = listener;
    }

    public void resetGame() {
        stopPolling();
        gameState.reset();
        isGameReady = false;
        currentGameId = null;
        currentPlayerId = null; // UPDATED: Reset player ID
        waitingForSecondPlayer = false;

        if (isInitialized) {
            invalidate();
        }
        if (uiListener != null) {
            uiListener.updateStatus("Game reset. Start or join a game to begin");
        }
    }

    // UPDATED: Create new game with proper player ID assignment
    public void createNewGame() {
        NetworkService networkService = new NetworkService();
        networkService.createGame(new NetworkService.GameCallback() {
            @Override
            public void onSuccess(ServerGameState gameState) {
                currentGameId = gameState.getGameId();
                currentPlayerId = "CREATOR"; // Creator always has this ID
                isGameReady = true;
                isLocalPlayer1 = true; // Creator is always Player 1
                waitingForSecondPlayer = true;

                GameView.this.gameState.reset();

                // Start polling immediately to detect when second player joins
                startPolling();

                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).onGameCreated(currentGameId);
                }

                if (uiListener != null) {
                    uiListener.updateStatus("Game created! Share ID: " + currentGameId + " | Waiting for opponent...");
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                currentGameId = null;
                currentPlayerId = null;
                isGameReady = false;

                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).onGameCreationFailed(errorMessage);
                }

                if (uiListener != null) {
                    uiListener.updateStatus("Failed to create game: " + errorMessage);
                }
            }
        });
    }

    // UPDATED: Join game with proper player ID handling
    public void joinGame(String gameId) {
        NetworkService networkService = new NetworkService();
        networkService.joinGame(gameId, new NetworkService.JoinGameCallback() {
            @Override
            public void onSuccess(ServerGameState serverGameState, boolean isPlayer1, String playerId) {
                currentGameId = gameId;
                currentPlayerId = playerId; // UPDATED: Store assigned player ID
                isGameReady = true;
                isLocalPlayer1 = isPlayer1;

                // Check if game is active or waiting for players
                waitingForSecondPlayer = !"ACTIVE".equals(serverGameState.getGameStatus()) ||
                        !serverGameState.isPlayer1Assigned() ||
                        !serverGameState.isPlayer2Assigned();

                // Update local state with server state
                updateFromServerState(serverGameState);

                // Start polling for opponent moves or game activation
                startPolling();

                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).onGameJoined(gameId, isPlayer1);
                }

                String playerRole = isPlayer1 ? "Player 1" : "Player 2";
                if (waitingForSecondPlayer) {
                    if (uiListener != null) {
                        uiListener.updateStatus("Joined as " + playerRole + " | Waiting for game to start...");
                    }
                } else {
                    if (uiListener != null) {
                        uiListener.updateStatus("Joined as " + playerRole + " | " +
                                (isMyTurn() ? "Your turn" : "Opponent's turn"));
                    }
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                currentGameId = null;
                currentPlayerId = null;
                isGameReady = false;

                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).onGameJoinFailed(errorMessage);
                }

                if (uiListener != null) {
                    uiListener.updateStatus("Failed to join game: " + errorMessage);
                }
            }
        });
    }

    private void startPolling() {
        if (currentGameId != null && pollingService == null) {
            pollingService = new GamePollingService(currentGameId, new NetworkService(), this);
            pollingService.startPolling();
        } else if (pollingService != null && !pollingService.isPolling()) {
            pollingService.startPolling();
        }
    }

    private void stopPolling() {
        if (pollingService != null) {
            pollingService.stopPolling();
        }
    }

    public String getCurrentGameId() {
        return currentGameId;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public boolean isLocalPlayer1() {
        return isLocalPlayer1;
    }
}