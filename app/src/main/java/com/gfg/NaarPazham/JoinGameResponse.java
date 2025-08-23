package com.gfg.NaarPazham;

class JoinGameResponse {
    private String status;
    private String message;
    private ServerGameState game;
    private Integer playerNumber;
    private String playerId; // ADD: Return the assigned player ID

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public ServerGameState getGame() {
        return game;
    }

    public Integer getPlayerNumber() {
        return playerNumber;
    }

    // NEW: Player ID getter
    public String getPlayerId() {
        return playerId;
    }
}