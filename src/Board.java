
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*; 

public class Board extends JPanel {
    private int cellSize;
    private final Point centerPoint;  
    private final int boardHoleSize; 
    private ArrayList<Point> validPositions = new ArrayList<>(); 


    @SuppressWarnings("OverridableMethodCallInConstructor")
    public Board(int frameWidth, int frameHeight) {
        centerPoint = new Point(frameWidth/2, frameHeight / 2);
        cellSize = findCellSize(frameWidth, frameHeight);
        boardHoleSize = Math.round(cellSize / 5); 
        initalizeValidPos();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        Point start = new Point(centerPoint.x - cellSize, centerPoint.y - cellSize); 
        Point end = new Point(centerPoint.x + cellSize, centerPoint.y + cellSize); 

        //Outer Box
        g2.drawRect(start.x, start.y, cellSize * 2, cellSize * 2);

        //Diagonal Lines
        drawBoardLine(g2, start, centerPoint, true, false);
        drawBoardLine(g2, centerPoint, end, false, true); 
        drawBoardLine(g2, new Point(centerPoint.x - cellSize, centerPoint.y + cellSize), centerPoint, true, false); 
        drawBoardLine(g2, centerPoint, new Point(centerPoint.x + cellSize, centerPoint.y - cellSize), false, true); 

        // Vertical Line 
        drawBoardLine(g2, new Point(centerPoint.x, centerPoint.y - cellSize), centerPoint, true, false);
        drawBoardLine(g2, centerPoint, new Point(centerPoint.x, centerPoint.y + cellSize), false, true); 
        
        //Horizontal Line 
        drawBoardLine(g2, new Point(centerPoint.x - cellSize, centerPoint.y), centerPoint, true, false); 
        drawBoardLine(g2, centerPoint, new Point(centerPoint.x + cellSize, centerPoint.y), false, true); 

        //Square at center 
        g2.setColor(Color.white); //filling
        g2.fillRect(centerPoint.x - boardHoleSize / 2, centerPoint.y - boardHoleSize / 2, boardHoleSize, boardHoleSize); 
        g2.setColor(Color.black); //border
        g2.drawRect(centerPoint.x - boardHoleSize / 2, centerPoint.y - boardHoleSize / 2, boardHoleSize, boardHoleSize); 
    }

    protected void drawBoardLine(Graphics2D g2,Point lineStart, Point lineEnd, boolean addSquareHoleAtStart, boolean addSquareHoleAtEnd) {
        g2.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y); 
        if (addSquareHoleAtStart) {
            g2.setColor(Color.white);
            g2.fillRect(lineStart.x - boardHoleSize / 2, lineStart.y - boardHoleSize / 2 , boardHoleSize, boardHoleSize);
            g2.setColor(Color.black);
            g2.drawRect(lineStart.x - boardHoleSize / 2, lineStart.y - boardHoleSize / 2, boardHoleSize, boardHoleSize);
        }
        if (addSquareHoleAtEnd) {
            g2.setColor(Color.white);
            g2.fillRect(lineEnd.x - boardHoleSize / 2, lineEnd.y - boardHoleSize / 2, boardHoleSize, boardHoleSize); 
            g2.setColor(Color.black); 
            g2.drawRect(lineEnd.x- boardHoleSize / 2, lineEnd.y- boardHoleSize / 2, boardHoleSize, boardHoleSize); 
        }
    }

    public int findCellSize(int frameWidth, int frameHeight) {
        return Math.min(frameWidth / 4, frameHeight / 4); 
    }

    public int getCellSize() {
        return cellSize; 
    }
    
   private void initalizeValidPos() {
    validPositions.clear();
    
    // Generate all 9 positions in a 3x3 grid
    for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 3; col++) {
            int x = centerPoint.x + (col - 1) * cellSize; // -1, 0, 1 * cellSize
            int y = centerPoint.y + (row - 1) * cellSize; // -1, 0, 1 * cellSize
            validPositions.add(new Point(x, y));
        }
    }
}

    public ArrayList<ArrayList<Point>> convertToTwoD(ArrayList<Point> array) {
        //initalize 3 x 3 array
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

    public ArrayList<Point> getValidPos() {
        return validPositions; 
    }


}
