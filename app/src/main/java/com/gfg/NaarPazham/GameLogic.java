package com.gfg.NaarPazham;

import android.graphics.Point;
import android.util.Log;

import java.util.ArrayList;

/**
 * FIXED: Enhanced GameLogic with comprehensive error handling and null safety
 */
public class GameLogic {
    private static final String TAG = "GameLogic";

    private final GameState gameState;
    private final Board board;
    private final NetworkService networkService;

    public GameLogic(GameState gameState, Board board, NetworkService networkService) {
        if (gameState == null) {
            throw new IllegalArgumentException("GameState cannot be null");
        }
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        if (networkService == null) {
            throw new IllegalArgumentException("NetworkService cannot be null");
        }

        this.gameState = gameState;
        this.board = board;
        this.networkService = networkService;
    }

    public interface PlacementCallback {
        void onPlacementSuccess(ServerGameState gameState);
        void onPlacementFailure(String message);
    }

    public interface MovementCallback {
        void onMovementSuccess(ServerGameState gameState);
        void onMovementFailure(String message);
    }

    /**
     * FIXED: Enhanced placement validation with comprehensive error handling
     */
    public void validatePlacement(int x, int y, String gameId, String playerId, PlacementCallback callback) {
        // FIXED: Null callback check
        if (callback == null) {
            Log.e(TAG, "Placement callback is null");
            return;
        }

        try {
            // FIXED: Enhanced input validation
            if (!isValidGameId(gameId)) {
                callback.onPlacementFailure("Game is not ready yet. Please wait.");
                return;
            }

            if (playerId == null || playerId.trim().isEmpty()) {
                callback.onPlacementFailure("Player ID is missing or invalid. Please restart the game.");
                return;
            }

            if (!"local-player".equals(playerId) && !PlayerIdGenerator.isValidPlayerId(playerId)) {
                callback.onPlacementFailure("Player ID is invalid. Please restart the game.");
                return;
            }

            // FIXED: Board validation with error handling
            Point validPos = validateBoardPosition(x, y);
            if (validPos == null) {
                callback.onPlacementFailure("Invalid position on board");
                return;
            }

            // Convert to board coordinates with error handling
            int[] boardPos = board.getGridPos(validPos.x, validPos.y);
            if (boardPos == null || boardPos.length < 2 || boardPos[0] == -1) {
                callback.onPlacementFailure("Cannot determine board position");
                return;
            }

            int boardX = boardPos[1]; // Column
            int boardY = boardPos[0]; // Row

            Log.d(TAG, "Attempting placement at board position (" + boardX + "," + boardY + ")");

            // FIXED: Use processMove instead of makeMove for consistency
            networkService.processMove(gameId, playerId, boardX, boardY, null, null,
                    new NetworkService.GameCallback() {
                        @Override
                        public void onSuccess(ServerGameState serverGameState) {
                            try {
                                if (serverGameState == null) {
                                    callback.onPlacementFailure("Invalid server response received");
                                    return;
                                }

                                Log.d(TAG, "Placement successful");
                                callback.onPlacementSuccess(serverGameState);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing placement success", e);
                                callback.onPlacementFailure("Error processing server response: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            try {
                                Log.w(TAG, "Placement failed: " + errorMessage);
                                String safeErrorMessage = errorMessage != null && !errorMessage.trim().isEmpty()
                                        ? errorMessage : "Placement failed for unknown reason";
                                callback.onPlacementFailure(safeErrorMessage);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing placement failure", e);
                            }
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in validatePlacement", e);
            callback.onPlacementFailure("Unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * FIXED: Enhanced movement validation with comprehensive error handling
     */
    public void validateMovement(Point fromPos, Point toPos, String gameId, String playerId, MovementCallback callback) {
        // FIXED: Null callback check
        if (callback == null) {
            Log.e(TAG, "Movement callback is null");
            return;
        }

        try {
            // FIXED: Enhanced input validation
            if (!isValidGameId(gameId)) {
                callback.onMovementFailure("Game is not ready yet. Please wait.");
                return;
            }

            if (playerId == null || playerId.trim().isEmpty()) {
                callback.onMovementFailure("Player ID is missing or invalid. Please restart the game.");
                return;
            }

            if (!"local-player".equals(playerId) && !PlayerIdGenerator.isValidPlayerId(playerId)) {
                callback.onMovementFailure("Player ID is invalid. Please restart the game.");
                return;
            }

            if (fromPos == null || toPos == null) {
                callback.onMovementFailure("Invalid movement positions");
                return;
            }

            // FIXED: Enhanced game state validation
            String gameStateError = validateGameStateForMovement();
            if (gameStateError != null) {
                callback.onMovementFailure(gameStateError);
                return;
            }

            // FIXED: Enhanced movement validation with null safety
            String movementError = validateMovementLogic(fromPos, toPos);
            if (movementError != null) {
                callback.onMovementFailure(movementError);
                return;
            }

            // Convert positions to board coordinates with error handling
            int[] fromBoardPos = board.getGridPos(fromPos.x, fromPos.y);
            int[] toBoardPos = board.getGridPos(toPos.x, toPos.y);

            if (fromBoardPos == null || fromBoardPos.length < 2 || fromBoardPos[0] == -1 ||
                    toBoardPos == null || toBoardPos.length < 2 || toBoardPos[0] == -1) {
                callback.onMovementFailure("Cannot determine board positions for movement");
                return;
            }

            int fromX = fromBoardPos[1]; // Column
            int fromY = fromBoardPos[0]; // Row
            int toX = toBoardPos[1]; // Column
            int toY = toBoardPos[0]; // Row

            Log.d(TAG, "Attempting movement from (" + fromX + "," + fromY + ") to (" + toX + "," + toY + ")");

            // FIXED: Use processMove for consistency
            networkService.processMove(gameId, playerId, toX, toY, fromX, fromY,
                    new NetworkService.GameCallback() {
                        @Override
                        public void onSuccess(ServerGameState serverGameState) {
                            try {
                                if (serverGameState == null) {
                                    callback.onMovementFailure("Invalid server response received");
                                    return;
                                }

                                Log.d(TAG, "Movement successful");
                                callback.onMovementSuccess(serverGameState);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing movement success", e);
                                callback.onMovementFailure("Error processing server response: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            try {
                                Log.w(TAG, "Movement failed: " + errorMessage);
                                String safeErrorMessage = errorMessage != null && !errorMessage.trim().isEmpty()
                                        ? errorMessage : "Movement failed for unknown reason";
                                callback.onMovementFailure(safeErrorMessage);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing movement failure", e);
                            }
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in validateMovement", e);
            callback.onMovementFailure("Unexpected error occurred: " + e.getMessage());
        }
    }

    // FIXED: Enhanced helper methods with null safety
    private boolean isValidGameId(String gameId) {
        return gameId != null && (!gameId.trim().isEmpty() || gameId.equals("local-game"));
    }

    private Point validateBoardPosition(int x, int y) {
        try {
            Point validPos = board.findValidPos(x, y);
            if (validPos == null) {
                Log.w(TAG, "No valid position found for coordinates (" + x + "," + y + ")");
            }
            return validPos;
        } catch (Exception e) {
            Log.e(TAG, "Error validating board position", e);
            return null;
        }
    }

    private String validateGameStateForMovement() {
        try {
            if (gameState.isGameOver()) {
                return "Game is over";
            }

            if (!gameState.isMovementPhase()) {
                return "Not in movement phase";
            }

            return null; // No error
        } catch (Exception e) {
            Log.e(TAG, "Error validating game state", e);
            return "Error checking game state";
        }
    }

    private String validateMovementLogic(Point fromPos, Point toPos) {
        try {
            // Check if there's a piece to move
            Player pieceToMove = getPieceAtPosition(fromPos);
            if (pieceToMove == null) {
                return "No piece at selected position";
            }

            // Check if it's the current player's piece
            if (!isCurrentPlayersPiece(pieceToMove)) {
                return "Can't move other player's piece";
            }

            // Check adjacency
            if (!board.areAdjacent(fromPos, toPos)) {
                return "Can only move to adjacent positions";
            }

            // Check if destination is occupied
            if (isOccupied(toPos.x, toPos.y)) {
                return "Can't move to occupied position";
            }

            return null; // No error
        } catch (Exception e) {
            Log.e(TAG, "Error validating movement logic", e);
            return "Error validating movement";
        }
    }

    /**
     * FIXED: Enhanced occupation check with null safety
     */
    private boolean isOccupied(int boardX, int boardY) {
        try {
            int spriteSize = board.getHoleSize() * 2;

            // Convert board position to expected player sprite position
            int expectedPlayerX = boardX - spriteSize / 2;
            int expectedPlayerY = boardY - spriteSize / 2;

            int tolerance = 5; // Small tolerance for rounding errors

            // Check player1 moves with null safety
            ArrayList<Player> player1Moves = gameState.getPlayer1Moves();
            if (player1Moves != null) {
                for (Player player : player1Moves) {
                    if (player != null &&
                            Math.abs(player.getX() - expectedPlayerX) <= tolerance &&
                            Math.abs(player.getY() - expectedPlayerY) <= tolerance) {
                        return true;
                    }
                }
            }

            // Check player2 moves with null safety
            ArrayList<Player> player2Moves = gameState.getPlayer2Moves();
            if (player2Moves != null) {
                for (Player player : player2Moves) {
                    if (player != null &&
                            Math.abs(player.getX() - expectedPlayerX) <= tolerance &&
                            Math.abs(player.getY() - expectedPlayerY) <= tolerance) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking occupation", e);
            return true; // Assume occupied on error for safety
        }
    }

    /**
     * FIXED: Enhanced win condition checking with null safety
     */
    public boolean checkWinCondition(ArrayList<Player> playerMoves) {
        try {
            if (playerMoves == null || playerMoves.size() < 3) {
                return false;
            }

            // Convert player sprite positions to board positions (center points)
            ArrayList<Point> boardPositions = new ArrayList<>();
            int spriteSize = board.getHoleSize() * 2;

            for (Player p : playerMoves) {
                if (p != null) {
                    // Convert from sprite position (top-left) to board position (center)
                    int boardX = p.getX() + spriteSize / 2;
                    int boardY = p.getY() + spriteSize / 2;
                    boardPositions.add(new Point(boardX, boardY));
                }
            }

            if (boardPositions.size() < 3) {
                return false;
            }

            // Check all possible combinations of 3 positions
            for (int i = 0; i < boardPositions.size() - 2; i++) {
                for (int j = i + 1; j < boardPositions.size() - 1; j++) {
                    for (int k = j + 1; k < boardPositions.size(); k++) {
                        Point p1 = boardPositions.get(i);
                        Point p2 = boardPositions.get(j);
                        Point p3 = boardPositions.get(k);

                        if (p1 != null && p2 != null && p3 != null && areThreeInLine(p1, p2, p3)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking win condition", e);
            return false;
        }
    }

    // FIXED: Enhanced line checking with null safety
    private boolean areThreeInLine(Point p1, Point p2, Point p3) {
        try {
            if (p1 == null || p2 == null || p3 == null) {
                return false;
            }

            // Check if all three points have same X (vertical line)
            if (p1.x == p2.x && p2.x == p3.x) {
                return true;
            }

            // Check if all three points have same Y (horizontal line)
            if (p1.y == p2.y && p2.y == p3.y) {
                return true;
            }

            // Check diagonal using cross product (slope check)
            int crossProduct = (p2.y - p1.y) * (p3.x - p1.x) - (p3.y - p1.y) * (p2.x - p1.x);

            // Points are collinear AND it's actually a diagonal (not horizontal/vertical)
            return crossProduct == 0 && p1.x != p2.x && p1.y != p2.y;
        } catch (Exception e) {
            Log.e(TAG, "Error checking line alignment", e);
            return false;
        }
    }

    /**
     * FIXED: Enhanced winner checking with null safety
     */
    public Player checkAndSetWinner() {
        try {
            if (checkWinCondition(gameState.getPlayer1Moves())) {
                Player winner = gameState.getPlayer1();
                gameState.setWinner(winner);
                return winner;
            }
            if (checkWinCondition(gameState.getPlayer2Moves())) {
                Player winner = gameState.getPlayer2();
                gameState.setWinner(winner);
                return winner;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error checking and setting winner", e);
            return null;
        }
    }

    public Player getWinner() {
        try {
            return gameState.getWinner();
        } catch (Exception e) {
            Log.e(TAG, "Error getting winner", e);
            return null;
        }
    }

    /**
     * FIXED: Enhanced position checking with null safety
     */
    private boolean isPlayerAtPosition(Player player, Point boardPos) {
        try {
            if (player == null || boardPos == null) {
                return false;
            }

            int spriteSize = board.getHoleSize() * 2;
            int expectedX = boardPos.x - spriteSize / 2;
            int expectedY = boardPos.y - spriteSize / 2;

            // Use small tolerance to handle any rounding errors
            int tolerance = 2;
            return Math.abs(player.getX() - expectedX) <= tolerance &&
                    Math.abs(player.getY() - expectedY) <= tolerance;
        } catch (Exception e) {
            Log.e(TAG, "Error checking player position", e);
            return false;
        }
    }

    /**
     * FIXED: Enhanced player finding with null safety
     */
    public Player findPlayerAtPosition(Point boardPos) {
        try {
            if (boardPos == null) {
                return null;
            }

            // Check player1 moves
            ArrayList<Player> player1Moves = gameState.getPlayer1Moves();
            if (player1Moves != null) {
                for (Player player : player1Moves) {
                    if (player != null && isPlayerAtPosition(player, boardPos)) {
                        return player;
                    }
                }
            }

            // Check player2 moves
            ArrayList<Player> player2Moves = gameState.getPlayer2Moves();
            if (player2Moves != null) {
                for (Player player : player2Moves) {
                    if (player != null && isPlayerAtPosition(player, boardPos)) {
                        return player;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error finding player at position", e);
            return null;
        }
    }

    /**
     * FIXED: Enhanced piece position finding with null safety
     */
    private Player getPieceAtPosition(Point boardPos) {
        try {
            if (boardPos == null) {
                return null;
            }

            // Check current player's pieces first
            ArrayList<Player> currentPlayerMoves = gameState.getCurrentPlayerMoves();
            if (currentPlayerMoves != null) {
                for (Player player : currentPlayerMoves) {
                    if (player != null && isPlayerAtPosition(player, boardPos)) {
                        return player;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting piece at position", e);
            return null;
        }
    }

    /**
     * FIXED: Enhanced piece ownership checking with null safety
     */
    private boolean isCurrentPlayersPiece(Player piece) {
        try {
            if (piece == null) {
                return false;
            }

            Player currentPlayer = gameState.getCurrentPlayer();
            if (currentPlayer == null) {
                return false;
            }

            return piece.isPlayer1() == currentPlayer.isPlayer1();
        } catch (Exception e) {
            Log.e(TAG, "Error checking piece ownership", e);
            return false;
        }
    }

    /**
     * FIXED: Added missing ValidationResult class
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final String message;

        public ValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getMessage() {
            return message != null ? message : "";
        }
    }

    // DEPRECATED: Keep for backward compatibility but don't use
    public ValidationResult validateMove(Point fromPos, Point toPos) {
        try {
            if (gameState.isGameOver()) {
                return new ValidationResult(false, "Game is over");
            }

            if (!gameState.isMovementPhase()) {
                return new ValidationResult(false, "Not in movement phase");
            }

            Player pieceToMove = getPieceAtPosition(fromPos);
            if (pieceToMove == null) {
                return new ValidationResult(false, "No piece at selected position");
            }

            //Validate move
            if (!board.areAdjacent(fromPos, toPos)) {
                return new ValidationResult(false, "Can only move to adjacent positions");
            }
            if (isOccupied(toPos.x, toPos.y)) {
                return new ValidationResult(false, "Can't move to occupied position");
            }
            if (!isCurrentPlayersPiece(pieceToMove)) {
                return new ValidationResult(false, "Can't move other player's piece");
            }

            //Update piece's position
            int spriteSize = board.getHoleSize() * 2;
            int newX = toPos.x - spriteSize / 2;
            int newY = toPos.y - spriteSize / 2;
            pieceToMove.setPos(newX, newY);
            return new ValidationResult(true, "Move Successful");
        } catch (Exception e) {
            Log.e(TAG, "Error in deprecated validateMove", e);
            return new ValidationResult(false, "Error validating move: " + e.getMessage());
        }
    }
}