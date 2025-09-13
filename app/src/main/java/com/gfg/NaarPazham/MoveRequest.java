
package com.gfg.NaarPazham;

public class MoveRequest {
    private Integer boardX;
    private Integer boardY;
    private Integer fromX;
    private Integer fromY;
    private Integer toX;
    private Integer toY;
    private String playerId;

    // Placement constructor
    public MoveRequest(int boardX, int boardY) {
        this.boardX = boardX;
        this.boardY = boardY;
    }

    // Movement constructor
    public MoveRequest(int fromX, int fromY, int toX, int toY) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    // Getters
    public Integer getBoardX() { return boardX; }
    public Integer getBoardY() { return boardY; }
    public Integer getFromX() { return fromX; }
    public Integer getFromY() { return fromY; }
    public Integer getToX() { return toX; }
    public Integer getToY() { return toY; }
    public String getPlayerId() { return playerId; }
}