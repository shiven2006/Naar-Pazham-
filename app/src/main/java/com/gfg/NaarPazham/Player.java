package com.gfg.NaarPazham;


import android.graphics.Color;

public class Player {

    private int x, y; // Player position
    private final int color; // Player color
    private final boolean isPlayer1; // Flag to identify player
    int playerSpriteSize;

    // private static final int SIZE = 25; // Size of the player square
    public Player(int x, int y, int playerSpriteSize, boolean isPlayer1) {
        this.x = x;
        this.y = y;
        this.playerSpriteSize = playerSpriteSize;
        this.isPlayer1 = isPlayer1;
        this.color = isPlayer1 ? Color.RED : Color.BLUE; // Assign color based on player
    }
    public void setPlayerSpriteSize(int size) {
        this.playerSpriteSize = size;
    }
    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }

    public int getPlayerSpriteSize() {
        return playerSpriteSize;
    }

    public int getColor() {
        return color;
    }
    public boolean isPlayer1() {
        return isPlayer1;
    }

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }
}