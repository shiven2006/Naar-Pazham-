package com.gfg.NaarPazham;

class GameStatusResponse {
    private String status;
    private String message;
    private String gameStatus;
    private Integer currentPlayer;
    private String winner;

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getGameStatus() {
        return gameStatus;
    }

    public Integer getCurrentPlayer() {
        return currentPlayer;
    }

    public String getWinner() {
        return winner;
    }
}