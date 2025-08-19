package com.gfg.NaarPazham;

import java.util.ArrayList;
import java.util.List;

public class ServerGameState {
    private String gameId;
    private List<PlayerMove> player1Moves = new ArrayList<>();
    private List<PlayerMove> player2Moves = new ArrayList<>();
    private boolean isPlayer1Turn = true;
    private String winner = null;
    private boolean isPlacementPhase = true;

    // Constructors
    public ServerGameState() {}

    public ServerGameState(String gameId) {
        this.gameId = gameId;
    }

    // Getters and setters (we'll add these)
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public List<PlayerMove> getPlayer1Moves() { return player1Moves; }
    public void setPlayer1Moves(List<PlayerMove> player1Moves) { this.player1Moves = player1Moves; }

    public List<PlayerMove> getPlayer2Moves() { return player2Moves; }
    public void setPlayer2Moves(List<PlayerMove> player2Moves) { this.player2Moves = player2Moves; }

    public boolean isPlayer1Turn() { return isPlayer1Turn; }
    public void setPlayer1Turn(boolean player1Turn) { isPlayer1Turn = player1Turn; }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public boolean isPlacementPhase() { return isPlacementPhase; }
    public void setPlacementPhase(boolean placementPhase) { isPlacementPhase = placementPhase; }
}