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


    @Override
    public void updateStatus(String message) {
        statusText.setText(message);
    }
    public void showGameOver(String winner) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Game Over! " + winner );
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

        //Button click
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameView.resetGame();
                statusText.setText("New Game started. Player 1's Turn");
            }
        });
        gameView.setListenerMethod(this);
        };


}