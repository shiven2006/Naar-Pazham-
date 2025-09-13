package com.gfg.NaarPazham;

import android.graphics.Point;
import android.util.Log;

/**
 * FIXED LocalGameManager - Enhanced to work exactly like online mode
 * Uses same coordinate system and validation logic as GameLogic
 */
public class LocalGameManager {
    private static final String TAG = "LocalGameManager";

    private final GameState gameState;
    private final Board board;
    private final GameLogic gameLogic;

    private boolean isGameActive = false;
    private Player winner = null;
    private Player selectedPiece = null; // Track selected piece for movement phase

    public interface LocalGameCallback {
        void onPlayerTurnChanged(boolean isPlayer1Turn);

        void onGameStateUpdated();

        void onGameWon(Player winner);

        void onMoveResult(boolean success, String message);

        void onGameStarted();

        void onGameReset();
    }

    private LocalGameCallback callback;

    public LocalGameManager(GameState gameState, Board board) {
        if (gameState == null) throw new IllegalArgumentException("GameState cannot be null");
        if (board == null) throw new IllegalArgumentException("Board cannot be null");

        this.gameState = gameState;
        this.board = board;

        // Create a mock NetworkService for GameLogic (not used in local mode)
        NetworkService mockNetworkService = new MockNetworkService();
        this.gameLogic = new GameLogic(gameState, board, mockNetworkService);

        Log.d(TAG, "LocalGameManager initialized with enhanced infrastructure");
    }

    public void setCallback(LocalGameCallback callback) {
        this.callback = callback;
    }

    /**
     * Start a new local game
     */
    public void startNewGame() {
        Log.d(TAG, "Starting new local game");

        try {
            gameState.reset();
            isGameActive = true;
            winner = null;
            selectedPiece = null;

            if (callback != null) {
                callback.onGameStarted();
                callback.onPlayerTurnChanged(gameState.getCurrentPlayer().isPlayer1());
                callback.onGameStateUpdated();
            }

            Log.d(TAG, "Local game started - Player 1's turn");
        } catch (Exception e) {
            Log.e(TAG, "Error starting new game", e);
            notifyMoveResult(false, "Error starting game: " + e.getMessage());
        }
    }

    /**
     * ADDED: Handle touch move - matches GameView's touch handling for local mode
     * This is the missing method that GameView calls
     */
    public void handleTouchMove(int touchX, int touchY, Player currentSelectedPiece) {
        if (!isGameActive) {
            notifyMoveResult(false, "Game is not active");
            return;
        }

        if (gameState.isGameOver()) {
            notifyMoveResult(false, "Game is already over");
            return;
        }

        try {
            Log.d(TAG, "Processing touch at screen position: (" + touchX + "," + touchY + ")");

            // Find valid board position from touch coordinates
            Point boardPos = board.findValidPos(touchX, touchY);
            if (boardPos == null) {
                notifyMoveResult(false, "Invalid touch position - not on board");
                if (gameState.isMovementPhase()) {
                    gameState.deselectPiece();
                    selectedPiece = null;
                }
                return;
            }

            Log.d(TAG, "Valid board position found: (" + boardPos.x + "," + boardPos.y + ")");

            if (gameState.isPlacementPhase()) {
                handlePlacement(boardPos);
            } else if (gameState.isMovementPhase()) {
                handleMovementTouch(boardPos);
            } else {
                notifyMoveResult(false, "Invalid game phase");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling touch move", e);
            notifyMoveResult(false, "Error processing touch: " + e.getMessage());
        }
    }

    /** Handle placement phase - uses same logic as online mode */
    /**
     * Handle placement phase - uses GameLogic validation
     */
    private void handlePlacement(Point boardPos) {
        try {
            // Use GameLogic validatePlacement with mock IDs
            gameLogic.validatePlacement(boardPos.x, boardPos.y, "local-game", "local-player",
                    new GameLogic.PlacementCallback() {
                        @Override
                        public void onPlacementSuccess(ServerGameState serverGameState) {
                            // For local mode, manually update game state
                            int spriteSize = board.getHoleSize() * 2;
                            Player newPiece = new Player(
                                    boardPos.x - spriteSize / 2,
                                    boardPos.y - spriteSize / 2,
                                    spriteSize,
                                    gameState.getCurrentPlayer().isPlayer1()
                            );

                            if (gameState.getCurrentPlayer().isPlayer1()) {
                                gameState.getPlayer1Moves().add(newPiece);
                            } else {
                                gameState.getPlayer2Moves().add(newPiece);
                            }
// Check win condition
                            Player gameWinner = gameLogic.checkAndSetWinner();
                            if (gameWinner != null) {
                                endGame(gameWinner);
                                return;
                            }

                            // Switch turns - no manual phase transition needed
                            switchTurns();
                            notifyMoveResult(true, "Piece placed successfully");
                        }

                        @Override
                        public void onPlacementFailure(String message) {
                            notifyMoveResult(false, message);
                        }});

        } catch (Exception e) {
            Log.e(TAG, "Error handling placement", e);
            notifyMoveResult(false, "Error placing piece: " + e.getMessage());
        }
    }

    /** Handle movement phase touch - matches online mode logic */
    /**
     * Handle movement phase touch - uses GameLogic validation
     */
    private void handleMovementTouch(Point boardPos) {
        try {
            Player pieceAtPosition = gameLogic.findPlayerAtPosition(boardPos);

            if (pieceAtPosition != null) {
                // Player clicked on a piece
                if (isCurrentPlayersPiece(pieceAtPosition)) {
                    selectedPiece = pieceAtPosition;
                    gameState.selectPiece(pieceAtPosition);
                    notifyMoveResult(true, "Piece selected");
                } else {
                    notifyMoveResult(false, "Cannot select opponent's piece");
                }
            } else {
                // Player clicked on empty space
                if (selectedPiece != null) {
                    Point fromPos = getCurrentPiecePosition(selectedPiece);
                    if (fromPos == null) {
                        selectedPiece = null;
                        gameState.deselectPiece();
                        notifyMoveResult(false, "Cannot find selected piece position");
                        return;
                    }

                    // Use GameLogic validateMovement
                    gameLogic.validateMovement(fromPos, boardPos, "local-game", "local-player",
                            new GameLogic.MovementCallback() {
                                @Override
                                public void onMovementSuccess(ServerGameState serverGameState) {
                                    // For local mode, manually update piece position
                                    int spriteSize = board.getHoleSize() * 2;
                                    selectedPiece.setPos(
                                            boardPos.x - spriteSize / 2,
                                            boardPos.y - spriteSize / 2
                                    );

                                    // Clear selection
                                    selectedPiece = null;
                                    gameState.deselectPiece();

                                    // Check win condition
                                    Player gameWinner = gameLogic.checkAndSetWinner();
                                    if (gameWinner != null) {
                                        endGame(gameWinner);
                                        return;
                                    }

                                    // Switch turns - works like online mode
                                    switchTurns();
                                    notifyMoveResult(true, "Piece moved successfully");
                                }

                                @Override
                                public void onMovementFailure(String message) {
                                    notifyMoveResult(false, message);
                                }
                            });

                } else {
                    notifyMoveResult(false, "No piece selected");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling movement touch", e);
            notifyMoveResult(false, "Error processing movement: " + e.getMessage());
        }
    }

    /**
     * Handle player move in local game - KEPT for backward compatibility
     */
    public boolean handleMove(int boardX, int boardY, Integer fromX, Integer fromY) {
        if (!isGameActive) {
            notifyMoveResult(false, "Game is not active");
            return false;
        }

        if (gameState.isGameOver()) {
            notifyMoveResult(false, "Game is already over");
            return false;
        }

        try {
            Log.d(TAG, "Processing move at board position: (" + boardX + "," + boardY + ")");

            if (gameState.isPlacementPhase()) {
                return handlePlacementDirect(boardX, boardY);
            } else if (gameState.isMovementPhase()) {
                return handleMovementDirect(boardX, boardY, fromX, fromY);
            } else {
                notifyMoveResult(false, "Invalid game phase");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling move", e);
            notifyMoveResult(false, "Error processing move: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle direct placement with board coordinates
     */
    private boolean handlePlacementDirect(int boardX, int boardY) {
        try {
            Point boardPos = new Point(boardX, boardY);

            // Validate position using board's validation
            Point validPos = board.findValidPos(boardX, boardY);
            if (validPos == null) {
                notifyMoveResult(false, "Invalid board position");
                return false;
            }
            boardPos = validPos;

            // Check if position is occupied
            Player existingPiece = gameLogic.findPlayerAtPosition(boardPos);
            if (existingPiece != null) {
                notifyMoveResult(false, "Position is already occupied");
                return false;
            }

            // Create new piece at the board position
            int spriteSize = board.getHoleSize() * 2;
            Player newPiece = new Player(
                    boardPos.x - spriteSize / 2,
                    boardPos.y - spriteSize / 2,
                    spriteSize,
                    gameState.getCurrentPlayer().isPlayer1()
            );

            // Add piece to game state
            if (newPiece.isPlayer1()) {
                gameState.getPlayer1Moves().add(newPiece);
            } else {
                gameState.getPlayer2Moves().add(newPiece);
            }

            String playerName = newPiece.isPlayer1() ? "Player 1 (Red)" : "Player 2 (Blue)";
            Log.d(TAG, "Placed piece for " + playerName + " at position (" + boardX + "," + boardY + ")");

            // Check win condition
            Player gameWinner = gameLogic.checkAndSetWinner();
            if (gameWinner != null) {
                endGame(gameWinner);
                return true;
            }

// Switch turns if game continues
            switchTurns();
            notifyMoveResult(true, "Piece placed successfully");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error handling direct placement", e);
            notifyMoveResult(false, "Error placing piece: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle direct movement with board coordinates
     */
    private boolean handleMovementDirect(int boardX, int boardY, Integer fromX, Integer fromY) {
        try {
            Point toPos = new Point(boardX, boardY);

            // Validate destination position
            Point validToPos = board.findValidPos(boardX, boardY);
            if (validToPos == null) {
                notifyMoveResult(false, "Invalid destination position");
                return false;
            }
            toPos = validToPos;

            // If we have fromX and fromY, this is a complete move
            if (fromX != null && fromY != null) {
                return handleCompleteMoveDirect(fromX, fromY, toPos);
            }

            // No from position - handle piece selection or direct move
            return handlePieceSelectionOrMoveDirect(toPos);

        } catch (Exception e) {
            Log.e(TAG, "Error handling direct movement", e);
            notifyMoveResult(false, "Error processing movement: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle complete move with from and to positions
     */
    private boolean handleCompleteMoveDirect(int fromX, int fromY, Point toPos) {
        try {
            Point fromPos = board.findValidPos(fromX, fromY);
            if (fromPos == null) {
                notifyMoveResult(false, "Invalid source position");
                return false;
            }

            // Find piece at source position
            Player pieceToMove = gameLogic.findPlayerAtPosition(fromPos);
            if (pieceToMove == null) {
                notifyMoveResult(false, "No piece at source position");
                return false;
            }

            // Validate it's current player's piece
            if (!isCurrentPlayersPiece(pieceToMove)) {
                notifyMoveResult(false, "Cannot move opponent's piece");
                return false;
            }

            // Use GameLogic to validate and execute the move
            GameLogic.ValidationResult validation = gameLogic.validateMove(fromPos, toPos);
            if (!validation.isValid()) {
                notifyMoveResult(false, validation.getMessage());
                return false;
            }

// Actually move the piece
            int spriteSize = board.getHoleSize() * 2;
            pieceToMove.setPos(
                    toPos.x - spriteSize / 2,
                    toPos.y - spriteSize / 2
            );

            Log.d(TAG, "Moved piece from (" + fromX + "," + fromY + ") to (" + toPos.x + "," + toPos.y + ")");

// Clear selection
            selectedPiece = null;
            gameState.deselectPiece();

// Check win condition
            Player gameWinner = gameLogic.checkAndSetWinner();
            if (gameWinner != null) {
                endGame(gameWinner);
                return true;
            }

// Switch turns - like online mode
            switchTurns();
            notifyMoveResult(true, "Piece moved successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error handling complete direct move", e);
            notifyMoveResult(false, "Error moving piece: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle piece selection or move to clicked position
     */
    private boolean handlePieceSelectionOrMoveDirect(Point clickPos) {
        try {
            // Check if there's a piece at clicked position
            Player pieceAtPos = gameLogic.findPlayerAtPosition(clickPos);

            if (pieceAtPos != null && isCurrentPlayersPiece(pieceAtPos)) {
                // Selecting current player's piece
                selectedPiece = pieceAtPos;
                gameState.selectPiece(pieceAtPos);
                notifyMoveResult(true, "Piece selected");
                return true;

            } else if (selectedPiece != null) {
                // Have selected piece, try to move it to clicked position
                Point fromPos = getCurrentPiecePosition(selectedPiece);
                if (fromPos == null) {
                    selectedPiece = null;
                    gameState.deselectPiece();
                    notifyMoveResult(false, "Cannot find selected piece position");
                    return false;
                }
// Validate and execute move
                GameLogic.ValidationResult validation = gameLogic.validateMove(fromPos, clickPos);
                if (!validation.isValid()) {
                    notifyMoveResult(false, validation.getMessage());
                    return false;
                }

// Actually move the piece
                int spriteSize = board.getHoleSize() * 2;
                selectedPiece.setPos(
                        clickPos.x - spriteSize / 2,
                        clickPos.y - spriteSize / 2
                );

                Log.d(TAG, "Moved selected piece to (" + clickPos.x + "," + clickPos.y + ")");

// Clear selection
                selectedPiece = null;
                gameState.deselectPiece();

// Check win condition
                Player gameWinner = gameLogic.checkAndSetWinner();
                if (gameWinner != null) {
                    endGame(gameWinner);
                    return true;
                }

// Switch turns - like online mode
                switchTurns();
                notifyMoveResult(true, "Piece moved successfully");
                return true;

            } else if (pieceAtPos != null) {
                // Clicked opponent's piece
                notifyMoveResult(false, "Cannot select opponent's piece");
                return false;

            } else {
                // Clicked empty space with no piece selected
                notifyMoveResult(false, "No piece selected");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling piece selection/move", e);
            notifyMoveResult(false, "Error processing action: " + e.getMessage());
            return false;
        }
    }

    /**
     * End the game with a winner
     */
    private void endGame(Player gameWinner) {
        winner = gameWinner;
        isGameActive = false;
        selectedPiece = null;

        String winnerName = winner.isPlayer1() ? "Player 1 (Red)" : "Player 2 (Blue)";
        Log.d(TAG, "Game won by " + winnerName);

        if (callback != null) {
            callback.onGameWon(winner);
            callback.onGameStateUpdated();
        }
    }

    /**
     * Get current position of a piece - matches online mode logic
     */
    private Point getCurrentPiecePosition(Player piece) {
        try {
            if (piece == null) {
                return null;
            }

            int spriteSize = board.getHoleSize() * 2;
            int centerX = piece.getX() + spriteSize / 2;
            int centerY = piece.getY() + spriteSize / 2;
            return new Point(centerX, centerY);
        } catch (Exception e) {
            Log.e(TAG, "Error getting piece position", e);
            return null;
        }
    }

    /**
     * Check if piece belongs to current player - matches online mode logic
     */
    private boolean isCurrentPlayersPiece(Player piece) {
        try {
            if (piece == null || gameState.getCurrentPlayer() == null) {
                return false;
            }

            return piece.isPlayer1() == gameState.getCurrentPlayer().isPlayer1();
        } catch (Exception e) {
            Log.e(TAG, "Error checking piece ownership", e);
            return false;
        }
    }

    /**
     * Switch turns and notify
     */
    /** Switch turns and notify */
    private void switchTurns() {
        try {
            // Log current state before switching
            String beforePlayer = gameState.getCurrentPlayer().isPlayer1() ? "Player 1 (Red)" : "Player 2 (Blue)";
            Log.d(TAG, "Before switch: " + beforePlayer);

            gameState.nextTurn();

            String afterPlayer = gameState.getCurrentPlayer().isPlayer1() ? "Player 1 (Red)" : "Player 2 (Blue)";
            Log.d(TAG, "After switch: " + afterPlayer + " (Turn switched successfully)");

            if (callback != null) {
                callback.onPlayerTurnChanged(gameState.getCurrentPlayer().isPlayer1());
                callback.onGameStateUpdated();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error switching turns", e);
        }
    }
    /**
     * Reset the game
     */
    public void resetGame() {
        try {
            Log.d(TAG, "Resetting local game");

            gameState.reset();
            isGameActive = false;
            winner = null;
            selectedPiece = null;

            if (callback != null) {
                callback.onGameReset();
                callback.onGameStateUpdated();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting game", e);
            notifyMoveResult(false, "Error resetting game: " + e.getMessage());
        }
    }

    /**
     * Notify move result
     */
    private void notifyMoveResult(boolean success, String message) {
        if (callback != null) {
            try {
                callback.onMoveResult(success, message);
                if (success) {
                    callback.onGameStateUpdated();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error notifying move result", e);
            }
        }
    }

    // Getters
    public boolean isGameActive() {
        return isGameActive;
    }

    public Player getWinner() {
        return winner;
    }

    public GameState getGameState() {
        return gameState;
    }

    public Board getBoard() {
        return board;
    }

    public Player getSelectedPiece() {
        return selectedPiece;
    }

    /**
     * Mock NetworkService for GameLogic dependency
     * Since local games don't need network functionality
     */
    private static class MockNetworkService extends NetworkService {
        @Override
        public void processMove(String gameId, String playerId, int x, int y,
                                Integer fromX, Integer fromY, GameCallback callback) {
            // For local games, always return success to let GameLogic handle validation
            if (callback != null) {
                // Create a minimal ServerGameState for local mode
                ServerGameState mockState = new ServerGameState();
                mockState.setGameId("local-game");
                mockState.setPlayer1Id("local-player");
                mockState.setPlayer2Id("local-player");
                callback.onSuccess(mockState);
            }
        }

        @Override
        public void getGameState(String gameId, String playerId, GameCallback callback) {
            if (callback != null) {
                ServerGameState mockState = new ServerGameState();
                mockState.setGameId("local-game");
                callback.onSuccess(mockState);
            }
        }

        @Override
        public void leaveGame(String gameId, String playerId, GameCallback callback) {
            if (callback != null) {
                callback.onSuccess(null);
            }
        }

        @Override
        public void cleanup() {
            // Nothing to cleanup for mock service
        }
    }
}