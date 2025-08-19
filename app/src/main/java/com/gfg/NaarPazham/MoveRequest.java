package com.gfg.NaarPazham;

public class MoveRequest {
    private int boardX;
    private int boardY;

    public MoveRequest() {}

    public MoveRequest(int boardX, int boardY) {
        this.boardX = boardX;
        this.boardY = boardY;
    }

    // Getters and setters
    public int getBoardX() { return boardX; }
    public void setBoardX(int boardX) { this.boardX = boardX; }

    public int getBoardY() { return boardY; }
    public void setBoardY(int boardY) { this.boardY = boardY; }
}