package com.gfg.NaarPazham;

import com.google.gson.annotations.SerializedName;

public class PlayerMove {
    private int boardX;
    private int boardY;

    @SerializedName("player1")  // Maps JSON "player1" to this field
    private boolean isPlayer1;

    public PlayerMove() {}

    public PlayerMove(int boardX, int boardY, boolean isPlayer1) {
        this.boardX = boardX;
        this.boardY = boardY;
        this.isPlayer1 = isPlayer1;
    }

    // Getters and setters
    public int getBoardX() { return boardX; }
    public void setBoardX(int boardX) { this.boardX = boardX; }

    public int getBoardY() { return boardY; }
    public void setBoardY(int boardY) { this.boardY = boardY; }

    public boolean isPlayer1() { return isPlayer1; }
    public void setPlayer1(boolean player1) { isPlayer1 = player1; }
}