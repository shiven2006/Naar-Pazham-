package com.gfg.NaarPazham;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Manages board structure, valid positions, and coordinate calculations
 */

public class Board {
    private int cellSize;
    private Point centerPoint;
    private int holeSize;
    private ArrayList<Point> validPos = new ArrayList<>();
    private ArrayList<ArrayList<Point>> validPos2D;


    public Board() {}

    public void initialize(int screenWidth, int screenHeight) {
        centerPoint = new Point(screenWidth / 2, screenHeight / 2);
        cellSize = findCellSize(screenWidth, screenHeight);
        holeSize = Math.round(cellSize / 10.0f);
        initializeValidPos();
        validPos2D = convertToTwoD(validPos);
    }

    public int findCellSize(int screenWidth, int screenHeight) {
        return Math.min(screenWidth/3, screenHeight/3);
    }

    private void initializeValidPos() {
        validPos.clear();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = centerPoint.x + (col - 1) * cellSize; // -1, 0, 1 * cellSize
                int y = centerPoint.y + (row - 1) * cellSize; // -1, 0, 1 * cellSize
                validPos.add(new Point(x, y));
            }
        }
    }
    public Point findValidPos(int x, int y) {
        for (int i = 0; i < validPos2D.size(); i++) {
            for (int j = 0; j < validPos2D.get(i).size(); j++) {
                Point validPos = validPos2D.get(i).get(j);
                int hitRadius = cellSize / 5;

                if (x >= validPos.x - hitRadius && x <= validPos.x + hitRadius &&
                        y >= validPos.y - hitRadius && y <= validPos.y + hitRadius) {
                    return validPos;
                }
            }
        }
        return null;
    }

    public boolean areAdjacent(Point p1, Point p2) {
        int[] pos1 = getGridPos(p1.x, p1.y);
        int[] pos2 = getGridPos(p2.x, p2.y);

        // Check if both positions are valid
        if (pos1[0] == -1 || pos2[0] == -1) {
            return false;
        }

        int row1 = pos1[0], col1 = pos1[1];
        int row2 = pos2[0], col2 = pos2[1];
        int rowDiff = Math.abs(row1 - row2);
        int colDiff = Math.abs(col1 - col2);

        // Standard horizontal/vertical adjacency
        if ((rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)) {
            return true;
        }

        // Special diagonal rules for Nine Men's Morris
        // Only center position (1,1) connects diagonally to corners
        if (rowDiff == 1 && colDiff == 1) {
            // One position must be center (1,1)
            return (row1 == 1 && col1 == 1) || (row2 == 1 && col2 == 1);
        }

        return false;
    }

    private static boolean containEdge(int[][] array, int[] target) {
        for (int[] array1 : array) {
            if (Arrays.equals(array1, target)) {
                return true;
            }
        }
        return false;
    }


    private int[] getGridPos(int gridX, int gridY) {
        int tolerance = Math.round(holeSize / 2); // Smaller, more reasonable tolerance

        for (int i = 0; i < validPos2D.size(); i++) {
            for (int j = 0; j < validPos2D.get(i).size(); j++) {
                int validX = validPos2D.get(i).get(j).x;
                int validY = validPos2D.get(i).get(j).y;

                if (Math.abs(gridX - validX) <= tolerance &&
                        Math.abs(gridY - validY) <= tolerance) {
                    return new int[]{i, j};
                }
            }
        }

        return new int[]{-1, -1}; // Not found
    }

    public ArrayList<ArrayList<Point>> convertToTwoD(ArrayList<Point> array) {
        ArrayList<ArrayList<Point>> newArr = new ArrayList<>();
        for (int row = 0; row < 3; row ++) {
            ArrayList<Point> rowList = new ArrayList<>();
            //add null elements to each point in grid
            for (int col = 0; col < 3; col++) {
                rowList.add(null);
            }
            newArr.add(rowList);
        }
        int i = -1, j = -1;

        //i for row, j for col
        for (Point p : array) {
            if (p.y < centerPoint.y) {
                i = 0;
            }
            else if (p.y == centerPoint.y) {
                i = 1;
            }
            else if (p.y > centerPoint.y) {
                i = 2;
            }

            if (p.x < centerPoint.x) {
                j = 0;
            }
            else if (p.x == centerPoint.x) {
                j = 1;
            }
            else if (p.x > centerPoint.x) {
                j = 2;
            }
            newArr.get(i).set(j,p);
        }
        return newArr;
    }




    //Getter methods
    public int getCellSize() {
        return cellSize;
    }

    public Point getCenterPoint() {
        return centerPoint;
    }

    public int getHoleSize() {
        return holeSize;
    }

    public ArrayList<Point> getValidPos() {
        return validPos;
    }

    public ArrayList<ArrayList<Point>> getValidPos2D() {
        return validPos2D;
    }

}
