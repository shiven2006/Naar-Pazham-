import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.*;
public class Game extends JPanel {

    private final ArrayList<Player> player1Moves = new ArrayList<>(); // List of player 1's moves
    private final ArrayList<Player> player2Moves = new ArrayList<>(); // List of player 2's moves

    private Player player1; // Player 1
    private Player player2; // Player 2
    private Board board; // Game board
    private int counter = 0;
    private JFrame frame;
    private final int frameWidth; 
    private final int frameHeight;
    public int playerSpriteSize; 

    private int selectedPieceIndex = -1; 
    private boolean isPieceSelected = false; 
    private JLabel statusLabel;

    ArrayList<Point> validPositions;
    ArrayList<ArrayList<Point>> validPos2D;
    


    public Game(int width, int height) {
        this.frameWidth = width; 
        this.frameHeight = height; 
        setupUI();
        initialize();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e);
            }
        });

        };

    

 public int getPlayerMoveIndex(int clickX, int clickY, Player currPlayer) {
    ArrayList<Player> moves = getPlayerMoveList();
    
    for (int i = 0; i < moves.size(); i++) {
        Player piece = moves.get(i);
        // Check if click is within the piece's bounding box
        if (clickX >= piece.getX() && clickX <= piece.getX() + playerSpriteSize &&
            clickY >= piece.getY() && clickY <= piece.getY() + playerSpriteSize) {
            return i;
        }
    }
    return -1;
}

public void handleMouseClick(MouseEvent e) {
    Player currentPlayer = getCurrentPlayer(counter);
    ArrayList<Player> currentPlayerMoves = getPlayerMoveList();
    
    // PLACEMENT PHASE (First 3 pieces)
    if (currentPlayerMoves.size() < 3) {
        if (validatePlacement(e.getX(), e.getY())) {
            repaint();
            if (checkWinCondition(currentPlayerMoves)) {
        currentPlayer = getCurrentPlayer(counter);
        String playerName = (currentPlayer == player1) ? "Player 1" : "Player 2";
        statusLabel.setText("Game Over. " + playerName + " wins!");
        statusLabel.revalidate(); 
        statusLabel.repaint();
        resetButton();
        return; // Exit the method, don't continue with turn switching
    }
            nextTurn();
            updateGameStatus();
        } else {
            statusLabel.setText("Invalid placement! Try again.");
            statusLabel.revalidate();
            statusLabel.repaint();
        }
    } 
    // MOVEMENT PHASE (After 3 pieces)
    else {
        if (!isPieceSelected) {
            // SELECTION PHASE
            if (isOccupied(e.getX(), e.getY())) {
                Player pieceOwner = getPlayerForThePiece(e.getX(), e.getY());
                if (pieceOwner == currentPlayer) {
                    selectedPieceIndex = getPlayerMoveIndex(e.getX(), e.getY(), currentPlayer);
                    isPieceSelected = true;
                    updateGameStatus();
                } else {
                    statusLabel.setText("Select your own piece!");
                    statusLabel.revalidate();
                    statusLabel.repaint();
                }
            } else {
                statusLabel.setText("Click on one of your pieces to select it");
                statusLabel.revalidate(); 
                statusLabel.repaint(); 
            }
        } else {
           // MOVEMENT PHASE
if (validateMove(e.getX(), e.getY(), selectedPieceIndex)) {
    repaint();
    
    // Check win condition AFTER the move is made but BEFORE switching turns
    currentPlayerMoves = getPlayerMoveList();
    if (checkWinCondition(currentPlayerMoves)) {
        currentPlayer = getCurrentPlayer(counter);
        String playerName = (currentPlayer == player1) ? "Player 1" : "Player 2";
        statusLabel.setText("Game Over. " + playerName + " wins!");
        statusLabel.revalidate(); 
        statusLabel.repaint();
        resetButton(); 
        return; // Exit the method, don't continue with turn switching
    }
    
    // Only switch turns if no win condition
    nextTurn();
    isPieceSelected = false;
    selectedPieceIndex = -1;
    updateGameStatus();
} else {
    statusLabel.setText("Invalid move! Must be adjacent to current position and not occupied space");
    statusLabel.revalidate(); 
    statusLabel.repaint(); 
}
}
}
}
public boolean checkWinCondition(ArrayList<Player> playerMoves) {
    if (playerMoves.size() < 3) {
        return false; // Not enough moves to win
    }
    
    ArrayList<Point> playerCoord = new ArrayList<>();
    for (Player p:playerMoves) {
        playerCoord.add(new Point(p.getX(), p.getY())); 
    }

    // Check row - all points have same Y coordinate
    boolean sameRow = true;
    for (int i = 0; i < playerCoord.size() - 1; i++) {
        if (playerCoord.get(i).getY() != playerCoord.get(i+1).getY()) {
            sameRow = false;
            break;
        }
    }
    if (sameRow) return true;

    // Check column - all points have same X coordinate
    boolean sameCol = true;
    for (int i = 0; i < playerCoord.size() - 1; i++) {
        if (playerCoord.get(i).getX() != playerCoord.get(i+1).getX()) {
            sameCol = false;
            break;
        }
    }
    if (sameCol) return true;

    // Check diagonal using slope
    if (playerCoord.size() >= 3) {
        Point p1 = playerCoord.get(0);
        Point p2 = playerCoord.get(1);
        
        // Check if all points are collinear (diagonal line)
        boolean diagonal = true;
        for (int i = 2; i < playerCoord.size(); i++) {
            Point p3 = playerCoord.get(i);
            
            // Use cross product to check if points are collinear(same slope)
            double crossProduct = (p2.getY() - p1.getY()) * (p3.getX() - p1.getX()) - 
                              (p3.getY() - p1.getY()) * (p2.getX() - p1.getX());
            
            if (crossProduct != 0) {
                diagonal = false;
                break;
            }
        }
        
        // Make sure it's actually diagonal (not horizontal or vertical)
        if (diagonal && p1.getX() != p2.getX() && p1.getY() != p2.getY()) {
            return true;
        }
    }
    return false; // No winner yet
}


private Player getPlayerForThePiece(int x, int y) {
    int tolerance = Math.round(playerSpriteSize / 2);
    
    // Check player1 moves
    for (Player p : player1Moves) {
        if (Math.abs(p.getX() - x) <= tolerance && 
            Math.abs(p.getY() - y) <= tolerance) {
            return player1;
        }
    }
    
    // Check player2 moves
    for (Player p : player2Moves) {
        if (Math.abs(p.getX() - x) <= tolerance && 
            Math.abs(p.getY() - y) <= tolerance) {
            return player2;
        }
    }
    return null;
}
    private void initialize() {
        board = new Board(frameWidth, frameHeight);
        validPositions = board.getValidPos(); 
        validPos2D = board.convertToTwoD(validPositions);
        playerSpriteSize = Math.round(board.findCellSize(frameWidth, frameHeight)/5); 
        // Create players and add them to the board
        player1 = new Player(50, 50, playerSpriteSize, true); // Player 1 at (100, 100)
        player2 = new Player(350, 50, playerSpriteSize, false); // Player 2 at (200, 200)
        updateGameStatus();
    }

    //setups window and adds game panel
    private void setupUI() {
         
        frame = new JFrame("Game Board");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(frameWidth, frameHeight);

        //status bar 
        statusLabel = new JLabel("Player 1's turn - Place your piece"); 
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder()); 

        frame.add(this, BorderLayout.CENTER); // Add the game panel to the frame
        frame.add(statusLabel, BorderLayout.SOUTH); 
        frame.setVisible(true);

            // Add these lines to force UI refresh
        statusLabel.revalidate();
        statusLabel.repaint();
    }

    
    public Player getCurrentPlayer(int counter) {
        return counter % 2 == 0 ? player1:player2; 
    }



private boolean validatePlacement(int x, int y) {
    Point validPos = findValidPos(x, y); 
    if (validPos == null) {
        return false; 
    }
    if (isOccupied(validPos.x, validPos.y)) {
        return false; 
    }
    ArrayList<Player> currentPlayerMoves = getPlayerMoveList(); 
    if (currentPlayerMoves.size() >= 3) {
        return false; 
    }

    boolean isPlayer1 = (counter % 2 == 0);
    createNewPiece(validPos, isPlayer1); 
    return true;   

}

public boolean validateMove(int finalX, int finalY, int pieceIndex) {
    Point validDestination = findValidPos(finalX, finalY); 
    if (validDestination == null) {
        return false; 
    }

    if (isOccupied(finalX, finalY)) {
        return false; 
    }

    ArrayList<Player> currentPlayerMoves = getPlayerMoveList(); 
    if (currentPlayerMoves.size() >= 3) {
        Player selectedPiece = currentPlayerMoves.get(pieceIndex); 
        if (checkAdj(selectedPiece.getX(), selectedPiece.getY(), validDestination.x, validDestination.y)) {
            movePiece(pieceIndex, validDestination); 
            return true; 
        }
        else {
            return false; 
        }
    }
    else {
        return false; 
    }
}


public void resetButton() {
    JButton reset = new JButton("Reset Game"); 
    reset.addActionListener(e -> resetGame());
    
    frame.add(reset, BorderLayout.NORTH);
    frame.revalidate();
    frame.repaint();
}
public void resetGame() {
    player1Moves.clear(); 
    player2Moves.clear(); 

    counter = 0; 
    isPieceSelected = false; 
    selectedPieceIndex = -1; 

    updateGameStatus();
    repaint();
}

 //   Check if place is occupied
private boolean isOccupied(int x, int y) {
    playerSpriteSize = player1.getPlayerSpriteSize();
    int tolerance = Math.round(playerSpriteSize / 2);
    
    // Check player1 moves
    for (Player player : player1Moves) {
        if (Math.abs(player.getX() - x) <= tolerance && 
            Math.abs(player.getY() - y) <= tolerance) {
            return true;
        }
    }
    
    // Check player2 moves
    for (Player player : player2Moves) {
        if (Math.abs(player.getX() - x) <= tolerance && 
            Math.abs(player.getY() - y) <= tolerance) {
            return true;
        }
    }
    
    return false;
}


private int[] getGridPos(int gridX, int gridY) {
    int tolerance = Math.round(playerSpriteSize / 2); // Smaller, more reasonable tolerance
    
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

private boolean checkAdj(int x1, int y1, int x2, int y2) {
    int[] pos1 = getGridPos(x1, y1); 
    int[] pos2 = getGridPos(x2, y2); 

    if (isOccupied(x2, y2)) {
        return false; 
    }
    
    // Check if both positions are valid
    if (pos1[0] == -1 || pos1[1] == -1 || pos2[0] == -1 || pos2[1] == -1) {
        return false; // Invalid position(s)
    }

    int[][] edgePositions = {
    {0, 1},  // top edge
    {1, 0},  // left edge
    {1, 2},  // right edge
    {2, 1}   // bottom edge
};

    int rowDiff = Math.abs(pos1[0] - pos2[0]);
    int colDiff = Math.abs(pos1[1] - pos2[1]);

    if (containEdge(edgePositions, pos2) && containEdge(edgePositions, pos1)) {
        return false; 
    }

    
    return (rowDiff == 1 && colDiff == 0) || // Adjacent vertically
           (rowDiff == 0 && colDiff == 1) || // Adjacent horizontally
           (rowDiff == 1 && colDiff == 1);   // Diagonal adjacency
}    

public static boolean containEdge(int[][] array, int[] target) {
        for (int[] array1 : array) {
            if (Arrays.equals(array1, target)) {
                return true;
            }
        }
    return false;
}
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics g2 = (Graphics2D) g;
        // Draw the board
        board.paintComponent(g2);

        for (Player player: player1Moves) {
            g2.setColor(player.getColor()); 
            g2.fillRect(player.getX(), player.getY(), player.getPlayerSpriteSize(), player.getPlayerSpriteSize());
        }

        for (Player player: player2Moves) {
            g2.setColor(player.getColor()); 
            g2.fillRect(player.getX(), player.getY(), player.getPlayerSpriteSize(), player.getPlayerSpriteSize());
        }


    }

private Point findValidPos(int x, int y) {
for (int i = 0; i < validPos2D.size(); i++) {
        for (int j = 0; j < validPos2D.get(i).size(); j++) {
            Point validPos = validPos2D.get(i).get(j);
            
            // Use boardHoleSize/2 instead of playerSpriteSize for hit detection
            int hitRadius = board.getCellSize() / 2; 
            
            if (x >= validPos.x - hitRadius && x <= validPos.x + hitRadius &&
                y >= validPos.y - hitRadius && y <= validPos.y + hitRadius) {
                    return validPos;
            }
        }
}
    return null; 
}

    private void createNewPiece(Point validPoint, boolean isPlayer1) {
        int offset = Math.round(board.getCellSize() / 5) / 2;
        Player player = new Player(validPoint.x - offset, validPoint.y - offset, playerSpriteSize, isPlayer1); 
        if (isPlayer1) {
            player1Moves.add(player); 
        }
        else {
            player2Moves.add(player); 
        }
    }

    

    private void movePiece(int pieceIndex, Point destination) {
        boolean isPlayer1 = (counter % 2 == 0 ); 
        int offset = Math.round(board.getCellSize() / 5) / 2;
        Player movedPlayer = new Player(destination.x - offset, destination.y - offset, playerSpriteSize, isPlayer1); 

        if (isPlayer1) {
            player1Moves.set(pieceIndex, movedPlayer); 
        }
        else {
            player2Moves.set(pieceIndex, movedPlayer); 
        }
    }

    public Player getPlayer1() {
        return player1; // Return Player 1
    }

    public Player getPlayer2() {
        return player2; // Return Player 2
    }

    public ArrayList<Player> getPlayerMoveList() {
        return counter % 2 == 0 ? player1Moves : player2Moves;
    }
    private void nextTurn() {
        counter++; 
        updateGameStatus();
    }

    private void updateGameStatus() {
        Player currentPlayer = getCurrentPlayer(counter); 
        String playerName = (currentPlayer == player1) ? "Player 1" : "Player 2"; 

        if (getPlayerMoveList().size() < 3) {
            statusLabel.setText(playerName + "'s turn - Place your piece"); 
        }
        else if (!isPieceSelected) {
            statusLabel.setText(playerName + "'s turn - Select a piece for movement"); 
        }
        else {
            statusLabel.setText(playerName + " - Click where you want to move within the bounds"); 
        }
        statusLabel.revalidate(); 
        statusLabel.repaint(); 
    }
    public static void main(String[] args) {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize(); //get screen size
    int width = (int) screenSize.getWidth();
    int height = (int) screenSize.getHeight();
    new Game(width, height);
}
}




