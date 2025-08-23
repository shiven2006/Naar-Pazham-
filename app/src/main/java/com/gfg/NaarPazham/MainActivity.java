package com.gfg.NaarPazham;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements UIUpdateListener {
    private GameView gameView;
    private TextView statusText;
    private TextView gameIdText;
    private TextView playerRoleText;
    private Button resetButton;
    private Button newGameButton;
    private Button joinGameButton;
    private Button shareGameIdButton;

    // Game state
    private String currentGameId = null;
    private boolean isHost = false;

    @Override
    public void updateStatus(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    @Override
    public void showGameOver(String winner) {
        runOnUiThread(() -> {
            statusText.setText("Game Over! " + winner);
            // Re-enable game creation/joining when game is over
            newGameButton.setEnabled(true);
            joinGameButton.setEnabled(true);
            newGameButton.setText("Start New Game");

            // Show game over dialog
            new AlertDialog.Builder(this)
                    .setTitle("Game Over!")
                    .setMessage(winner)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Play Again", (dialog, which) -> {
                        gameView.resetGame();
                        updateUIForNoGame();
                    })
                    .show();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupEventListeners();

        // Initial state
        updateUIForNoGame();
    }

    private void initializeViews() {
        gameView = findViewById(R.id.game_view);
        statusText = findViewById(R.id.status_text);
        gameIdText = findViewById(R.id.game_id_text);
        playerRoleText = findViewById(R.id.player_role_text);
        resetButton = findViewById(R.id.reset_button);
        newGameButton = findViewById(R.id.new_game_button);
        joinGameButton = findViewById(R.id.join_game_button);
        shareGameIdButton = findViewById(R.id.share_game_id_button);

        gameView.setListenerMethod(this);
    }

    private void setupEventListeners() {
        resetButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Reset Game")
                    .setMessage("Are you sure you want to reset the current game? This will disconnect from the server.")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        gameView.resetGame();
                        updateUIForNoGame();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        newGameButton.setOnClickListener(v -> {
            newGameButton.setEnabled(false);
            joinGameButton.setEnabled(false);
            newGameButton.setText("Creating Game...");
            statusText.setText("Creating new game on server...");

            gameView.createNewGame();
        });

        joinGameButton.setOnClickListener(v -> showJoinGameDialog());

        shareGameIdButton.setOnClickListener(v -> shareGameId());
    }

    private void showJoinGameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join Game");

        final EditText input = new EditText(this);
        input.setHint("Enter Game ID");

        // Limit input to reasonable game ID length
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});

        builder.setView(input);

        builder.setPositiveButton("Join", (dialog, which) -> {
            String gameId = input.getText().toString().trim().toUpperCase();
            if (!gameId.isEmpty()) {
                joinGame(gameId);
            } else {
                Toast.makeText(this, "Please enter a valid Game ID", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        // Auto-focus and show keyboard
        AlertDialog dialog = builder.create();
        dialog.show();
        input.requestFocus();
    }

    private void joinGame(String gameId) {
        // Disable buttons during join attempt
        newGameButton.setEnabled(false);
        joinGameButton.setEnabled(false);
        joinGameButton.setText("Joining...");
        statusText.setText("Joining game: " + gameId + "...");

        gameView.joinGame(gameId);
    }

    private void shareGameId() {
        if (currentGameId != null) {
            // Copy to clipboard
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Game ID", currentGameId);
            clipboard.setPrimaryClip(clip);

            // Show confirmation
            Toast.makeText(this, "Game ID copied to clipboard!", Toast.LENGTH_SHORT).show();

            // Show sharing dialog with better formatting
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Share Game ID")
                    .setMessage("Game ID: " + currentGameId +
                            "\n\nShare this ID with your opponent so they can join your game." +
                            "\n\nThe ID has been copied to your clipboard.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            Toast.makeText(this, "No active game to share", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIForNoGame() {
        currentGameId = null;
        isHost = false;
        gameIdText.setText("No active game");
        gameIdText.setVisibility(View.GONE);
        playerRoleText.setVisibility(View.GONE);
        shareGameIdButton.setVisibility(View.GONE);
        newGameButton.setEnabled(true);
        joinGameButton.setEnabled(true);
        newGameButton.setText("Start New Game");
        joinGameButton.setText("Join Game");
        statusText.setText("Start a new game or join an existing one");
    }

    private void updateUIForActiveGame(String gameId, boolean isHost, boolean isPlayer1) {
        this.currentGameId = gameId;
        this.isHost = isHost;

        gameIdText.setText("Game ID: " + gameId);
        gameIdText.setVisibility(View.VISIBLE);

        String playerRole = isPlayer1 ? "Player 1 (Red)" : "Player 2 (Blue)";
        playerRoleText.setText("You are: " + playerRole);
        playerRoleText.setVisibility(View.VISIBLE);

        if (isHost) {
            shareGameIdButton.setVisibility(View.VISIBLE);
        } else {
            shareGameIdButton.setVisibility(View.GONE);
        }

        newGameButton.setText("Game Active");
        newGameButton.setEnabled(false);
        joinGameButton.setText("In Game");
        joinGameButton.setEnabled(false);
    }

    // Callbacks from GameView
    public void onGameCreated(String gameId) {
        runOnUiThread(() -> {
            updateUIForActiveGame(gameId, true, true); // Creator is always Player 1
            statusText.setText("Game created! Waiting for opponent to join...");
        });
    }

    public void onGameCreationFailed(String error) {
        runOnUiThread(() -> {
            newGameButton.setEnabled(true);
            joinGameButton.setEnabled(true);
            newGameButton.setText("Start New Game");
            statusText.setText("Failed to create game: " + error);

            Toast.makeText(this, "Game creation failed: " + error, Toast.LENGTH_LONG).show();
        });
    }

    public void onGameJoined(String gameId, boolean isPlayer1) {
        runOnUiThread(() -> {
            updateUIForActiveGame(gameId, false, isPlayer1);
            String playerRole = isPlayer1 ? "Player 1 (Red)" : "Player 2 (Blue)";
            statusText.setText("Joined game as " + playerRole + "!");

            Toast.makeText(this, "Successfully joined game!", Toast.LENGTH_SHORT).show();
        });
    }

    public void onGameJoinFailed(String error) {
        runOnUiThread(() -> {
            newGameButton.setEnabled(true);
            joinGameButton.setEnabled(true);
            joinGameButton.setText("Join Game");
            statusText.setText("Failed to join game: " + error);

            Toast.makeText(this, "Failed to join game: " + error, Toast.LENGTH_LONG).show();
        });
    }
}