package com.gfg.NaarPazham;

import android.graphics.Point;

import java.util.ArrayList;

/**
 * Handles game logic including piece placement validation and piece creation
 * FIXED: Proper NetworkService integration and player authentication
 */
public class GameLogic {
    private final GameState gameState;
    private final Board board;
    private final NetworkService networkService;

    public GameLogic(GameState gameState, Board board, NetworkService networkService) {
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
     * FIXED: Async placement validation with proper NetworkService calls
     */
    public void validatePlacement(int x, int y, String gameId, String playerId, PlacementCallback callback) {
        if (gameId == null) {
            callback.onPlacementFailure("Game is not ready yet. Please wait.");
            return;
        }

        if (playerId == null) {
            callback.onPlacementFailure("Player ID is missing. Please restart the game.");
            return;
        }

        Point validPos = board.findValidPos(x, y);
        if (validPos == null) {
            callback.onPlacementFailure("Invalid Position");
            return;
        }

        // Convert to board coordinates
        int[] boardPos = board.getGridPos(validPos.x, validPos.y);
        if (boardPos[0] == -1) {
            callback.onPlacementFailure("Invalid board position");
            return;
        }

        int boardX = boardPos[1]; // Column
        int boardY = boardPos[0]; // Row

        // FIXED: Use NetworkService properly for placement
        networkService.makeMove(gameId, boardX, boardY, playerId, new NetworkService.GameCallback() {
            @Override
            public void onSuccess(ServerGameState serverGameState) {
                callback.onPlacementSuccess(serverGameState);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onPlacementFailure(errorMessage);
            }
        });
    }

    /**
     * FIXED: Async movement validation with proper NetworkService calls
     */
    public void validateMovement(Point fromPos, Point toPos, String gameId, String playerId, MovementCallback callback) {
        if (gameId == null) {
            callback.onMovementFailure("Game is not ready yet. Please wait.");
            return;
        }

        if (playerId == null) {
            callback.onMovementFailure("Player ID is missing. Please restart the game.");
            return;
        }

        if (gameState.isGameOver()) {
            callback.onMovementFailure("Game is over");
            return;
        }

        if (!gameState.isMovementPhase()) {
            callback.onMovementFailure("Not in movement phase");
            return;
        }

        // Basic client-side validation first
        Player pieceToMove = getPieceAtPosition(fromPos);
        if (pieceToMove == null) {
            callback.onMovementFailure("No piece at selected position");
            return;
        }

        if (!isCurrentPlayersPiece(pieceToMove)) {
            callback.onMovementFailure("Can't move other player's piece");
            return;
        }

        if (!board.areAdjacent(fromPos, toPos)) {
            callback.onMovementFailure("Can only move to adjacent positions");
            return;
        }

        if (isOccupied(toPos.x, toPos.y)) {
            callback.onMovementFailure("Can't move to occupied position");
            return;
        }

        // Convert positions to board coordinates
        int[] fromBoardPos = board.getGridPos(fromPos.x, fromPos.y);
        int[] toBoardPos = board.getGridPos(toPos.x, toPos.y);

        if (fromBoardPos[0] == -1 || toBoardPos[0] == -1) {
            callback.onMovementFailure("Invalid board position");
            return;
        }

        int fromX = fromBoardPos[1]; // Column
        int fromY = fromBoardPos[0]; // Row
        int toX = toBoardPos[1]; // Column
        int toY = toBoardPos[0]; // Row

        // FIXED: Use NetworkService properly for movement
        networkService.makeMoveMovement(gameId, fromX, fromY, toX, toY, playerId, new NetworkService.GameCallback() {
            @Override
            public void onSuccess(ServerGameState serverGameState) {
                callback.onMovementSuccess(serverGameState);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onMovementFailure(errorMessage);
            }
        });
    }

    /**
     * Checks if a board position is occupied by any player's piece
     */
    private boolean isOccupied(int boardX, int boardY) {
        int spriteSize = board.getHoleSize() * 2;

        // Convert board position to expected player sprite position
        int expectedPlayerX = boardX - spriteSize / 2;
        int expectedPlayerY = boardY - spriteSize / 2;

        int tolerance = 5; // Small tolerance for rounding errors

        // Check player1 moves
        for (Player player : gameState.getPlayer1Moves()) {
            if (Math.abs(player.getX() - expectedPlayerX) <= tolerance &&
                    Math.abs(player.getY() - expectedPlayerY) <= tolerance) {
                return true;
            }
        }

        // Check player2 moves
        for (Player player : gameState.getPlayer2Moves()) {
            if (Math.abs(player.getX() - expectedPlayerX) <= tolerance &&
                    Math.abs(player.getY() - expectedPlayerY) <= tolerance) {
                return true;
            }
        }

        return false;
    }

    public boolean checkWinCondition(ArrayList<Player> playerMoves) {
        if (playerMoves.size() < 3) {
            return false;
        }

        // Convert player sprite positions to board positions (center points)
        ArrayList<Point> boardPositions = new ArrayList<>();
        int spriteSize = board.getHoleSize() * 2;

        for (Player p : playerMoves) {
            // Convert from sprite position (top-left) to board position (center)
            int boardX = p.getX() + spriteSize / 2;
            int boardY = p.getY() + spriteSize / 2;
            boardPositions.add(new Point(boardX, boardY));
        }

        // Check all possible combinations of 3 positions
        for (int i = 0; i < boardPositions.size() - 2; i++) {
            for (int j = i + 1; j < boardPositions.size() - 1; j++) {
                for (int k = j + 1; k < boardPositions.size(); k++) {
                    Point p1 = boardPositions.get(i);
                    Point p2 = boardPositions.get(j);
                    Point p3 = boardPositions.get(k);

                    if (areThreeInLine(p1, p2, p3)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Helper method to check if three points form a line
    private boolean areThreeInLine(Point p1, Point p2, Point p3) {
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
        if (crossProduct == 0 && p1.x != p2.x && p1.y != p2.y) {
            return true;
        }

        return false;
    }

    public Player checkAndSetWinner() {
        if (checkWinCondition(gameState.getPlayer1Moves())) {
            gameState.setWinner(gameState.getPlayer1());
            return gameState.getPlayer1();
        }
        if (checkWinCondition(gameState.getPlayer2Moves())) {
            gameState.setWinner(gameState.getPlayer2());
            return gameState.getPlayer2();
        }
        return null;
    }

    public Player getWinner() {
        return gameState.getWinner();
    }

    private boolean isPlayerAtPosition(Player player, Point boardPos) {
        int spriteSize = board.getHoleSize() * 2;
        int expectedX = boardPos.x - spriteSize / 2;
        int expectedY = boardPos.y - spriteSize / 2;

        // Use small tolerance to handle any rounding errors
        int tolerance = 2;
        return Math.abs(player.getX() - expectedX) <= tolerance &&
                Math.abs(player.getY() - expectedY) <= tolerance;
    }

    public Player findPlayerAtPosition(Point boardPos) {
        for (Player player : gameState.getPlayer1Moves()) {
            if (isPlayerAtPosition(player, boardPos)) {
                return player;
            }
        }
        for (Player player : gameState.getPlayer2Moves()) {
            if (isPlayerAtPosition(player, boardPos)) {
                return player;
            }
        }
        return null;
    }

    private Player getPieceAtPosition(Point boardPos) {
        // Check current player's pieces first
        for (Player player : gameState.getCurrentPlayerMoves()) {
            if (isPlayerAtPosition(player, boardPos)) {
                return player;
            }
        }
        return null;
    }

    private boolean isCurrentPlayersPiece(Player piece) {
        return piece.isPlayer1() == (gameState.getCurrentPlayer().isPlayer1());
    }

    // DEPRECATED: Keep for backward compatibility but don't use
    public ValidationResult validateMove(Point fromPos, Point toPos) {
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
    }
}