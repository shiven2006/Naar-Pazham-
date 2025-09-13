package com.gfg.NaarPazham;

public interface JoinGameCallback {
    void onSuccess(ServerGameState gameState, boolean isPlayer1, String playerId);
    void onFailure(String errorMessage);
}