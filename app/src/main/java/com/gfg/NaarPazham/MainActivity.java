package com.gfg.NaarPazham;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements UIUpdateListener {
    private GameView gameView;
    private TextView statusText;
    private Button resetButton;
    private Button newGameButton; // Add this new button

    @Override
    public void updateStatus(String message) {
        statusText.setText(message);
    }

    public void showGameOver(String winner) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Game Over! " + winner);
                // Re-enable new game button when game is over
                newGameButton.setEnabled(true);
                newGameButton.setText("Start New Game");
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //XML layout used
        setContentView(R.layout.activity_main);

        //Views by ID
        gameView = findViewById(R.id.game_view);
        statusText = findViewById(R.id.status_text);
        resetButton = findViewById(R.id.reset_button);
        newGameButton = findViewById(R.id.new_game_button); // You'll need to add this to your XML

        // Initialize with proper messaging
        statusText.setText("Tap 'Start New Game' to begin");

        // Reset button for local game state only
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.resetGame();
                statusText.setText("Game reset. Tap 'Start New Game' to begin");
                newGameButton.setEnabled(true);
                newGameButton.setText("Start New Game");
            }
        });

        // New game button to create server game
        newGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disable button and show loading
                newGameButton.setEnabled(false);
                newGameButton.setText("Creating Game...");
                statusText.setText("Creating new game on server...");

                // Create the game
                gameView.createNewGame();
            }
        });

        gameView.setListenerMethod(this);
        gameView.createNewGame();
    }

    // Add method to handle successful game creation
    public void onGameCreated() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                newGameButton.setText("Game Active");
                statusText.setText("Game created! Player 1's turn");
            }
        });
    }

    // Add method to handle game creation failure
    public void onGameCreationFailed(String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                newGameButton.setEnabled(true);
                newGameButton.setText("Start New Game");
                statusText.setText("Failed to create game: " + error);
            }
        });
    }
}