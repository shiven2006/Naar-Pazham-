package com.gfg.NaarPazham;

import android.graphics.Point;

import java.util.ArrayList;

/**
 * Handles game logic including piece placement validation and piece creation
 */
public class GameLogic {
    private final GameState gameState;
    private final Board board;

    public GameLogic(GameState gameState, Board board) {
        this.gameState = gameState;
        this.board = board;
    }

    /**
     * Validates and processes a piece placement attempt
     * @param x Touch x coordinate
     * @param y Touch y coordinate
     * @return true if placement was successful, false otherwise
     */
    public ValidationResult validatePlacement(int x, int y) {
        Point validPos = board.findValidPos(x, y);
        if (validPos == null) {
            return new ValidationResult(false, "Invalid Position");
        }
        if (gameState.isGameOver()) {
            return new ValidationResult(false, "Game Over");
        }
        if (isOccupied(validPos.x, validPos.y)) {
            return new ValidationResult(false, "Space is already occupied");
        }

        if (!gameState.canPlacePiece()) {
            return new ValidationResult(false, "Can't place more pieces");
        }
        boolean isPlayer1 = gameState.getCurrentPlayer().isPlayer1();
        Player newPiece = createNewPiece(validPos, isPlayer1);
        gameState.addMove(newPiece, isPlayer1);
        return new ValidationResult(true, "Piece placed successufully");
    }
    /**
     * Creates a new game piece at the specified valid position
     * @param validPoint The valid board position
     * @param isPlayer1 Whether this piece belongs to player 1
     * @return The created Player object
     */
    private Player createNewPiece(Point validPoint, boolean isPlayer1) {
        int offset = Math.round(board.getCellSize() / 5) / 2;
        int spriteSize = board.getHoleSize() * 2; // Assuming both players have same sprite size

        // Position player so its CENTER is at validPoint (like holes)
        int playerX = validPoint.x - spriteSize / 2;
        int playerY = validPoint.y - spriteSize / 2;

        return new Player(playerX, playerY, spriteSize, isPlayer1);
    }

    /**
     * Checks if a board position is occupied by any player's piece
     * @param boardX X coordinate of board position (center point)
     * @param boardY Y coordinate of board position (center point)
     * @return true if position is occupied
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

        return false; // No three in a row found
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
        // Cross product = 0 means points are collinear
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
    // Add this method to check current game winner
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


}

