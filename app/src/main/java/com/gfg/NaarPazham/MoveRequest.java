package com.gfg.NaarPazham;

public class MoveRequest {
    private int boardX;  // Destination X (or piece placement X for placement phase)
    private int boardY;  // Destination Y (or piece placement Y for placement phase)

    // New fields for movement phase
    private Integer fromX;  // Source X position (null for placement phase)
    private Integer fromY;  // Source Y position (null for placement phase)
    private boolean isMovement = false;  // Flag to indicate if this is a movement vs placement

    // ADD: Player authentication
    private String playerId;  // Player ID for authentication

    // Default constructor
    public MoveRequest() {}

    // Constructor for placement moves (existing)
    public MoveRequest(int boardX, int boardY) {
        this.boardX = boardX;
        this.boardY = boardY;
        this.isMovement = false;
    }

    // Constructor for movement moves (new)
    public MoveRequest(int fromX, int fromY, int boardX, int boardY) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.boardX = boardX;
        this.boardY = boardY;
        this.isMovement = true;
    }

    // Getters and setters
    public int getBoardX() { return boardX; }
    public void setBoardX(int boardX) { this.boardX = boardX; }

    public int getBoardY() { return boardY; }
    public void setBoardY(int boardY) { this.boardY = boardY; }

    public Integer getFromX() { return fromX; }
    public void setFromX(Integer fromX) { this.fromX = fromX; }

    public Integer getFromY() { return fromY; }
    public void setFromY(Integer fromY) { this.fromY = fromY; }

    public boolean isMovement() { return isMovement; }
    public void setMovement(boolean movement) { this.isMovement = movement; }

    // NEW: Player ID getter and setter
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
}