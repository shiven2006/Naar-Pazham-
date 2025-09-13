// MatchmakingResponse.java
package com.gfg.NaarPazham;

public class MatchmakingResponse {
    private String status; // "match_found", "waiting", "already_in_queue", "error"
    private String message;
    private String gameId;
    private String playerId;
    private Integer playerNumber; // 1 or 2
    private ServerGameState game;

    // Constructors
    public MatchmakingResponse() {}

    public MatchmakingResponse(String status, String message, String gameId,
                               String playerId, Integer playerNumber, ServerGameState game) {
        this.status = status;
        this.message = message;
        this.gameId = gameId;
        this.playerId = playerId;
        this.playerNumber = playerNumber;
        this.game = game;
    }

    // Getters and setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public Integer getPlayerNumber() { return playerNumber; }
    public void setPlayerNumber(Integer playerNumber) { this.playerNumber = playerNumber; }

    public ServerGameState getGame() { return game; }
    public void setGame(ServerGameState game) { this.game = game; }
}
