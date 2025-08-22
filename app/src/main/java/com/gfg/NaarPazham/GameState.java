package com.gfg.NaarPazham;
import java.util.ArrayList;


/**
 * Manages the current state of the game including players, moves, and turn tracking
 */
public class GameState {
    // Game Variables
    private final ArrayList<Player> player1Moves = new ArrayList<>(); // List of player 1's moves
    private final ArrayList<Player> player2Moves = new ArrayList<>(); // List of player 2's moves
    private Player player1; // Player 1
    private Player player2; // Player 2
    private int counter = 0;
    private Player selectedPiece = null;
    private Player winner = null;


    public GameState() {
        player1 = new Player(0, 0, 0, true);
        player2 = new Player(0, 0, 0, false);
    }

    public GameState(Player player1, Player player2) {
        this.player1 = player1;
        this.player2 = player2;
    }

    // Getter methods for player moves
    public ArrayList<Player> getPlayer1Moves() {
        return player1Moves;
    }

    public ArrayList<Player> getPlayer2Moves() {
        return player2Moves;
    }

    public Player getCurrentPlayer() {
        return counter % 2 == 0 ? player1 : player2;
    }
    public ArrayList<Player> getCurrentPlayerMoves() {
        return counter % 2 == 0 ? player1Moves : player2Moves;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    //Turn Management
    public void nextTurn() {
        counter++;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    //Manage movement
    public void selectPiece(Player piece) {
        selectedPiece = piece;
    }
    public void deselectPiece() {
        selectedPiece = null;
    }
    public Player getSelectedPiece() {
        return selectedPiece;
    }
    public boolean hasSelectedPiece() {
        return selectedPiece != null;
    }

    public void addMove(Player player, boolean isPlayer1) {
        if (isPlayer1) {
            player1Moves.add(player);
        } else {
            player2Moves.add(player);
        }
        nextTurn();
    }

    public boolean canPlacePiece() {
        return getCurrentPlayerMoves().size() < 3;
    }

    //Game state queries

    public boolean isPlacementPhase() {
        return player1Moves.size() < 3 || player2Moves.size() < 3;
    }

    public boolean isMovementPhase() {
        return player1Moves.size() == 3 && player2Moves.size() == 3;
    }

    public boolean isGameOver() {
        return getWinner() != null;
    }
    public Player getWinner() {
        return winner;
    }

    public void setWinner(Player winner) {
        this.winner = winner;
    }




    public boolean checkWinConAfterMove(GameLogic gamelogic) {
        ArrayList<Player> currentPlayerMoves = getCurrentPlayerMoves();
        return gamelogic.checkWinCondition(currentPlayerMoves);
    }
    public void reset() {
        player1Moves.clear();
        player2Moves.clear();
        counter = 0;
        winner = null;
    }





}
