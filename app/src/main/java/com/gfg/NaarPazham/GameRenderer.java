package com.gfg.NaarPazham;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;

/**
 * Handles all rendering operations for the game
 */
public class GameRenderer {
    private Paint paint;
    private Board board;
    private GameState gameState;

    public GameRenderer(Board board, GameState gameState) {
        this.board = board;
        this.gameState = gameState;
        initializePaint();
    }

    private void initializePaint() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(3);
    }

    /**
     * Main rendering method - draws everything
     * @param canvas The canvas to draw on
     */
    public void render(Canvas canvas) {
        drawBoard(canvas);
        drawAllPlayers(canvas);
    }

    /**
     * Draws the complete game board
     * @param canvas The canvas to draw on
     */
    private void drawBoard(Canvas canvas) {
        Point centerPoint = board.getCenterPoint();
        int cellSize = board.getCellSize();

        Point start = new Point(centerPoint.x - cellSize, centerPoint.y - cellSize);
        Point end = new Point(centerPoint.x + cellSize, centerPoint.y + cellSize);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(android.graphics.Color.BLACK);

        // Draw outer rectangle
        canvas.drawRect(start.x, start.y, end.x, end.y, paint);

        // Draw diagonal lines
        drawBoardLine(canvas, start, centerPoint, true, false);
        drawBoardLine(canvas, centerPoint, end, false, true);
        drawBoardLine(canvas, new Point(centerPoint.x - cellSize, centerPoint.y + cellSize), centerPoint, true, false);
        drawBoardLine(canvas, centerPoint, new Point(centerPoint.x + cellSize, centerPoint.y - cellSize), false, true);

        // Draw horizontal lines
        drawBoardLine(canvas, new Point(centerPoint.x - cellSize, centerPoint.y), centerPoint, true, false);
        drawBoardLine(canvas, centerPoint, new Point(centerPoint.x + cellSize, centerPoint.y), false, true);

        // Draw vertical lines
        drawBoardLine(canvas, new Point(centerPoint.x, centerPoint.y - cellSize), centerPoint, true, false);
        drawBoardLine(canvas, centerPoint, new Point(centerPoint.x, centerPoint.y + cellSize), false, true);

        // Draw center square
        int holeSize = board.getHoleSize();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(-1); // White
        canvas.drawRect(centerPoint.x - holeSize, centerPoint.y - holeSize,
                centerPoint.x + holeSize, centerPoint.y + holeSize, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(-16777216); // Black
        canvas.drawRect(centerPoint.x - holeSize, centerPoint.y - holeSize,
                centerPoint.x + holeSize, centerPoint.y + holeSize, paint);
    }

    /**
     * Draws a board line with optional holes at start and end
     */
    private void drawBoardLine(Canvas canvas, Point lineStart, Point lineEnd,
                               boolean addSquareHoleAtStart, boolean addSquareHoleAtEnd) {
        int holeSize = board.getHoleSize();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(android.graphics.Color.BLACK);
        canvas.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y, paint);

        if (addSquareHoleAtStart) {
            drawHole(canvas, lineStart, holeSize);
        }
        if (addSquareHoleAtEnd) {
            drawHole(canvas, lineEnd, holeSize);
        }
    }

    /**
     * Draws a square hole at the specified point
     */
    private void drawHole(Canvas canvas, Point center, int holeSize) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(-1); // White
        canvas.drawRect(center.x - holeSize, center.y - holeSize,
                center.x + holeSize, center.y + holeSize, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(-16777216); // Black
        canvas.drawRect(center.x - holeSize, center.y - holeSize,
                center.x + holeSize, center.y + holeSize, paint);
    }

    /**
     * Draws all players' pieces on the board
     * @param canvas The canvas to draw on
     */
    private void drawAllPlayers(Canvas canvas) {
        // Draw player 1 pieces
        for (Player player : gameState.getPlayer1Moves()) {
            drawPlayer(canvas, player);
        }

        // Draw player 2 pieces
        for (Player player : gameState.getPlayer2Moves()) {
            drawPlayer(canvas, player);
        }
    }

    /**
     * Draws a single player piece
     * @param canvas The canvas to draw on
     * @param player The player piece to draw
     */
    private void drawPlayer(Canvas canvas, Player player) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(player.getColor());
        canvas.drawRect(player.getX(), player.getY(),
                player.getX() + player.getPlayerSpriteSize(),
                player.getY() + player.getPlayerSpriteSize(), paint);

        if (gameState.getSelectedPiece() == player) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(android.graphics.Color.YELLOW);
            canvas.drawRect(player.getX()-4, player.getY()-4,player.getX() + player.getPlayerSpriteSize()+4, player.getY() + player.getPlayerSpriteSize()+4, paint);
        }
    }
}