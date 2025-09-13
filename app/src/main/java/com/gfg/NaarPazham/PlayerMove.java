package com.gfg.NaarPazham;

public class PlayerMove {
    private Integer boardX;  // Changed to Integer for consistency
    private Integer boardY;  // Changed to Integer for consistency
    private boolean isPlayer1;

    public PlayerMove() {}

    public PlayerMove(Integer boardX, Integer boardY, boolean isPlayer1) {
        this.boardX = boardX;
        this.boardY = boardY;
        this.isPlayer1 = isPlayer1;
    }

    // Getters and setters
    public Integer getBoardX() { return boardX; }
    public void setBoardX(Integer boardX) { this.boardX = boardX; }

    public Integer getBoardY() { return boardY; }
    public void setBoardY(Integer boardY) { this.boardY = boardY; }

    public boolean isPlayer1() { return isPlayer1; }
    public void setPlayer1(boolean player1) { isPlayer1 = player1; }

    @Override
    public String toString() {
        return String.format("PlayerMove{x=%d, y=%d, isPlayer1=%b}", boardX, boardY, isPlayer1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PlayerMove that = (PlayerMove) obj;
        return isPlayer1 == that.isPlayer1 &&
                java.util.Objects.equals(boardX, that.boardX) &&
                java.util.Objects.equals(boardY, that.boardY);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(boardX, boardY, isPlayer1);
    }
}