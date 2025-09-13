package com.gfg.NaarPazham;

/**
 * Interface for UI updates from GameView to MainActivity
 */
public interface UIUpdateListener {
    /**
     * Update the status text in the UI
     * @param message The status message to display
     */
    void updateStatus(String message);

    /**
     * Show game over dialog/message
     * @param winner The winner message to display
     */
    void showGameOver(String winner);
}
