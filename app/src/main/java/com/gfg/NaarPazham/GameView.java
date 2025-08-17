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
        gameLogic = new GameLogic(gameState, board);
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
        if (!isInitialized) {
            return false; // Not ready to handle touch events yet
        }
        if (gameState.isGameOver()) {
            uiListener.showGameOver("Player " + (gameState.getWinner().isPlayer1() ? "1" : "2") + " wins!");
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
        ValidationResult placementSuccessful =  gameLogic.validatePlacement(touchX, touchY);

        if (placementSuccessful.isSuccess()) {
            Player winner = gameLogic.checkAndSetWinner();
            if (winner != null) {
                if (uiListener != null) {
                    uiListener.showGameOver("Player " + (winner.isPlayer1() ? "1" : "2"));
                }
            }
            else {
                if (uiListener != null) {
                    showTemporaryMsg(placementSuccessful.getMessage());
                }
            }
            invalidate();

        }
        else {
            if (!gameState.isGameOver() && uiListener != null) {
                uiListener.updateStatus("Player " + (gameState.getCurrentPlayer().isPlayer1() ? "1" : "2") + "'s Turn");
            }
        }
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
                            uiListener.showGameOver("Player " + (winner.isPlayer1() ? "1" : "2"));
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
        if (isInitialized) {
            invalidate(); // Trigger redraw
        }
        if (uiListener != null) {
            uiListener.updateStatus("New Game started. Player 1's Turn");
        }
    }

}

interface UIUpdateListener {
    public void updateStatus(String message);
    public void showGameOver(String winner);
}