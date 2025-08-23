package com.gfg.NaarPazham;

// Response classes for parsing backend responses
class GameResponse {
    private String status;
    private String message;
    private ServerGameState game;

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public ServerGameState getGame() { return game; }
}
