package com.gfg.NaarPazham;

import android.graphics.Point;

public class GameStateConverter  {

    public static void convertAndUpdateLocalState(ServerGameState serverGameState, GameState localState,
                                                  Board board) {
        // Clear existing data in local
        localState.getPlayer1Moves().clear();
        localState.getPlayer2Moves().clear();

        // Convert Player1 moves - use the isPlayer1 field from JSON
        for (PlayerMove move: serverGameState.getPlayer1Moves()) {
            Player player = convertMoveToPlayer(move, board);
            localState.getPlayer1Moves().add(player);
        }

        // Convert Player2 moves - use the isPlayer1 field from JSON
        for (PlayerMove move: serverGameState.getPlayer2Moves()) {
            Player player = convertMoveToPlayer(move, board);
            localState.getPlayer2Moves().add(player);
        }

        localState.setCounter(serverGameState.getTotalMoves());

        // Winner logic
        if (serverGameState.getWinner() != null) {
            if (serverGameState.getWinner().equals("PLAYER 1")) {
                localState.setWinner(localState.getPlayer1());
            } else if (serverGameState.getWinner().equals("PLAYER 2")) {
                localState.setWinner(localState.getPlayer2());
            }
        } else {
            localState.setWinner(null);
        }
    }

    private static Player convertMoveToPlayer(PlayerMove move, Board board) {
        Point screenPos = board.getValidPos2D().get(move.getBoardY()).get(move.getBoardX());
        int spriteSize = board.getHoleSize() * 2;
        int playerX = screenPos.x - spriteSize / 2;
        int playerY = screenPos.y - spriteSize / 2;

        // Use the isPlayer1 value directly from the JSON data
        boolean isPlayer1 = move.isPlayer1();

        System.out.println("Converting move at (" + move.getBoardX() + "," + move.getBoardY() +
                ") with isPlayer1=" + isPlayer1 +
                ", color will be " + (isPlayer1 ? "RED" : "BLUE"));

        return new Player(playerX, playerY, spriteSize, isPlayer1);
    }
}