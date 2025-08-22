package com.gfg.NaarPazham;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Main game view - handles UI events and coordinates between game components
 */
public class GameView extends View{
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

    // Game state
    private String currentGameId = null;
    private boolean isGameReady = false; // Add this flag

    private UIUpdateListener uiListener;

    public GameView(Context context, Player player1, Player player2) {
        super(context);
        gameState = new GameState(player1, player2);
        initializeComponents();
    }

    public GameView(Context context) {
        super(context);
        gameState = new GameState(); // Uses default players
        initializeComponents();
    }

    public GameView (Context context, AttributeSet attrs) {
        super(context, attrs);
        gameState = new GameState();
        initializeComponents();
    }

    private void initializeComponents() {
        board = new Board();
        gameLogic = new GameLogic(gameState, board, new NetworkService() );
        // GameRenderer will be initialized after we have screen dimensions
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Initialize board and renderer on first draw when we have screen dimensions
        if (!isInitialized) {
            screenWidth = canvas.getWidth();
            screenHeight = canvas.getHeight();

            board.initialize(screenWidth, screenHeight);
            gameRenderer = new GameRenderer(board, gameState);

            // set the sprite sizes based on initialized board
            gameState.getPlayer1().setPlayerSpriteSize(board.getHoleSize());
            gameState.getPlayer2().setPlayerSpriteSize(board.getHoleSize());

            isInitialized = true;
        }

        // Render the game
        gameRenderer.render(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Check if system is ready
        if (!isInitialized) {
            return false; // Not ready to handle touch events yet
        }

        // Check if game is ready
        if (!isGameReady || currentGameId == null) {
            if (uiListener != null) {
                uiListener.updateStatus("Game not ready. Please start a new game first.");
            }
            return false;
        }

        if (gameState.isGameOver()) {
            if (uiListener != null) {
                uiListener.showGameOver("Player " + (gameState.getWinner().isPlayer1() ? "1" : "2") + " wins!");
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

    private void handlePlacement(int touchX, int touchY) {
        gameLogic.validatePlacement(touchX, touchY, currentGameId, new GameLogic.PlacementCallback() {
            @Override
            public void onPlacementSuccess(ServerGameState serverGameState) {
                // 1. Convert server state to local state (includes winner!)
                GameStateConverter.convertAndUpdateLocalState(serverGameState, gameState, board);
                System.out.println("Received from server - totalMoves: " + serverGameState.getTotalMoves());
                System.out.println("Player1 moves: " + serverGameState.getPlayer1Moves().size());
                System.out.println("Player2 moves: " + serverGameState.getPlayer2Moves().size());

                // 2. Check if game is over (server already determined this)
                if (gameState.isGameOver()) {
                    if (uiListener != null) {
                        String winnerName = gameState.getWinner().isPlayer1() ? "Player 1" : "Player 2";
                        uiListener.showGameOver(winnerName + " wins!");
                    }
                } else {
                    // 3. Update turn status for ongoing game
                    if (uiListener != null) {
                        String currentPlayerName = gameState.getCurrentPlayer().isPlayer1() ? "Player 1" : "Player 2";
                        uiListener.updateStatus(currentPlayerName + "'s Turn");
                    }
                }

                // 4. Redraw the game
                invalidate();
            }

            @Override
            public void onPlacementFailure(String message) {
                showTemporaryMsg(message);
            }
        });
    }

    private void handleMovement(Point boardPos) {
        Player pieceAtPosition = gameLogic.findPlayerAtPosition(boardPos);

        if (pieceAtPosition != null) {
            // Player clicked on a piece
            if (pieceAtPosition.isPlayer1() == gameState.getCurrentPlayer().isPlayer1()) {
                // It's current player's piece - select it
                gameState.selectPiece(pieceAtPosition);
            } else {
                if (!gameState.isGameOver() && uiListener != null) {
                    showTemporaryMsg("Can't click on other player's piece");
                }
            }
        } else {
            // Player clicked on empty space
            if (gameState.hasSelectedPiece()) {
                // We have a selected piece, try to move it
                Point fromPos = findPiecePosition(gameState.getSelectedPiece());
                ValidationResult moveSuccessful = gameLogic.validateMove(fromPos, boardPos);
                if (moveSuccessful.isSuccess()) {
                    gameState.deselectPiece();
                    gameState.nextTurn();

                    Player winner = gameLogic.checkAndSetWinner();
                    if (winner != null) {
                        if (uiListener != null) {
                            uiListener.showGameOver("Player " + (winner.isPlayer1() ? "1" : "2") + " wins!");
                        }
                    } else {
                        // Only update turn status if game is NOT over
                        if (uiListener != null) {
                            uiListener.updateStatus("Player " + (gameState.getCurrentPlayer().isPlayer1() ? "1" : "2") + "'s Turn");
                        }
                    }
                } else {
                    // Move was not successful
                    if (!gameState.isGameOver() && uiListener != null) {
                        showTemporaryMsg(moveSuccessful.getMessage());
                    }
                }
            } else {
                // No piece selected
                if (!gameState.isGameOver() && uiListener != null) {
                    showTemporaryMsg("No piece selected");
                }
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
                    if (!gameState.isGameOver() && uiListener != null) {
                        uiListener.updateStatus("Player " + (gameState.getCurrentPlayer().isPlayer1() ? "1" : "2") + "'s Turn");
                    }
                }
            };
            uiHandler.postDelayed(turnUpdateRunnable, 1000);
        }
    }

    // Public methods for external access if needed
    public GameState getGameState () {
        return gameState;
    }

    public Board getBoard () {
        return board;
    }

    public void setListenerMethod(UIUpdateListener listener) {
        this.uiListener = listener;
    }

    public void resetGame () {
        gameState.reset();
        // Reset game ready state
        isGameReady = false;
        currentGameId = null;

        if (isInitialized) {
            invalidate(); // Trigger redraw
        }
        if (uiListener != null) {
            uiListener.updateStatus("Game reset. Tap 'Start New Game' to begin");
        }
    }

    // Updated createNewGame method with proper callbacks
    public void createNewGame() {
        NetworkService networkService = new NetworkService();
        networkService.createGame(new NetworkService.GameCallback() {
            @Override
            public void onSuccess(ServerGameState gameState) {
                currentGameId = gameState.getGameId(); // Save the real game ID
                isGameReady = true; // Mark game as ready

                // Reset local game state to match server
                GameView.this.gameState.reset();

                // Notify MainActivity of successful creation
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).onGameCreated();
                }

                // Update UI
                if (uiListener != null) {
                    uiListener.updateStatus("Game ready! Player 1's turn");
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                currentGameId = null;
                isGameReady = false;

                // Notify MainActivity of failure
                if (getContext() instanceof MainActivity) {
                    ((MainActivity) getContext()).onGameCreationFailed(errorMessage);
                }

                if (uiListener != null) {
                    uiListener.updateStatus("Failed to create game: " + errorMessage);
                }
            }
        });
    }
}

interface UIUpdateListener {
    public void updateStatus(String message);
    public void showGameOver(String winner);
}