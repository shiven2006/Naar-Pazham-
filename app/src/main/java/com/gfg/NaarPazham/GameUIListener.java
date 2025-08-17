package com.gfg.NaarPazham;

public interface GameUIListener {
    void onTurnChanged(boolean isPlyaer1Turn);
    void onGameOver(Player winner);
    void onInvalidMove(String message);
}
