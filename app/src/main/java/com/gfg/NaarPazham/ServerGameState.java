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
    private int totalMoves = 0;

    //IDs for player
    private String player1Id = null;
    private String player2Id = null;
    private boolean isPlayer1Assigned = false;

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

    public int getTotalMoves() {return totalMoves; }
    public void setTotalMoves(int totalMoves) {this.totalMoves = totalMoves;}


    // For player tracking (if you want to implement Option B later)
    public String getPlayer1Id() {
        return player1Id;
    }

    public void setPlayer1Id(String player1Id) {
        this.player1Id = player1Id;
    }

    public String getPlayer2Id() {
        return player2Id;
    }

    public void setPlayer2Id(String player2Id) {
        this.player2Id = player2Id;
    }

    public boolean isPlayer1Assigned() {
        return isPlayer1Assigned;
    }

    public void setPlayer1Assigned(boolean player1Assigned) {
        this.isPlayer1Assigned = player1Assigned;
    }
}