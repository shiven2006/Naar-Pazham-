package com.gfg.NaarPazham;

public interface GameStateListener {
    void onGameCreated(String gameId);
    void onGameCreationFailed(String errorMessage);
    void onGameJoined(String gameId, boolean isPlayer1);
    void onGameJoinFailed(String errorMessage);
}