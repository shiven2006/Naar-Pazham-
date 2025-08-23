package com.gfg.NaarPazham;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class NetworkService {
    private static final String TAG = "NetworkService";
    private static final String BASE_URL = "http://10.0.2.2:8080/api/games"; // Use your actual server URL

    private OkHttpClient client;
    private Gson gson;
    private Handler mainHandler;

    public NetworkService() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    // Callback interfaces
    public interface GameCallback {
        void onSuccess(ServerGameState gameState);
        void onFailure(String errorMessage);
    }

    public interface JoinGameCallback {
        void onSuccess(ServerGameState gameState, boolean isPlayer1, String playerId);
        void onFailure(String errorMessage);
    }

    public interface StatusCallback {
        void onStatusUpdate(String gameStatus, int currentPlayer, String winner);
        void onFailure(String errorMessage);
    }

    // 1. Create Game
    public void createGame(GameCallback callback) {
        Log.d(TAG, "Creating new game...");

        RequestBody body = RequestBody.create("", MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/create")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to create game", e);
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Create game response: " + jsonResponse);

                    if (response.isSuccessful()) {
                        GameResponse gameResponse = gson.fromJson(jsonResponse, GameResponse.class);
                        if ("success".equals(gameResponse.getStatus())) {
                            mainHandler.post(() -> callback.onSuccess(gameResponse.getGame()));
                        } else {
                            mainHandler.post(() -> callback.onFailure(gameResponse.getMessage()));
                        }
                    } else {
                        mainHandler.post(() -> callback.onFailure("Server error: " + response.code()));
                    }
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    mainHandler.post(() -> callback.onFailure("Invalid response format"));
                }
            }
        });
    }

    // 2. Join Game
    public void joinGame(String gameId, JoinGameCallback callback) {
        Log.d(TAG, "Joining game: " + gameId);

        // Generate a unique player ID for the joiner
        String joinerId = "ANDROID_PLAYER_" + System.currentTimeMillis();

        // Create join request body
        String jsonBody = "{\"playerId\":\"" + joinerId + "\"}";
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId + "/join")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to join game", e);
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Join game response: " + jsonResponse);

                    if (response.isSuccessful()) {
                        JoinGameResponse joinResponse = gson.fromJson(jsonResponse, JoinGameResponse.class);
                        if ("success".equals(joinResponse.getStatus())) {
                            boolean isPlayer1 = joinResponse.getPlayerNumber() == 1;
                            // Use the player ID we sent
                            String assignedPlayerId = joinResponse.getPlayerId() != null ? joinResponse.getPlayerId() : joinerId;
                            mainHandler.post(() -> callback.onSuccess(joinResponse.getGame(), isPlayer1, assignedPlayerId));
                        } else {
                            mainHandler.post(() -> callback.onFailure(joinResponse.getMessage()));
                        }
                    } else {
                        try {
                            JoinGameResponse errorResponse = gson.fromJson(jsonResponse, JoinGameResponse.class);
                            String errorMsg = (errorResponse != null && errorResponse.getMessage() != null)
                                    ? errorResponse.getMessage()
                                    : "Server error: " + response.code();
                            mainHandler.post(() -> callback.onFailure(errorMsg));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onFailure("Server error: " + response.code()));
                        }
                    }
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    mainHandler.post(() -> callback.onFailure("Invalid response format"));
                }
            }
        });
    }

    // 3. Get Game State
    public void getGameState(String gameId, GameCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to get game state", e);
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String jsonResponse = responseBody.string();

                    if (response.isSuccessful()) {
                        GameResponse gameResponse = gson.fromJson(jsonResponse, GameResponse.class);
                        if ("success".equals(gameResponse.getStatus())) {
                            mainHandler.post(() -> callback.onSuccess(gameResponse.getGame()));
                        } else {
                            mainHandler.post(() -> callback.onFailure(gameResponse.getMessage()));
                        }
                    } else {
                        mainHandler.post(() -> callback.onFailure("Server error: " + response.code()));
                    }
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    mainHandler.post(() -> callback.onFailure("Invalid response format"));
                }
            }
        });
    }

    // 4. Get Game Status (lighter endpoint for polling)
    public void getGameStatus(String gameId, StatusCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId + "/status")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "Status polling error: " + e.getMessage());
                mainHandler.post(() -> callback.onFailure(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful()) {
                        String jsonResponse = responseBody.string();
                        GameStatusResponse statusResponse = gson.fromJson(jsonResponse, GameStatusResponse.class);

                        mainHandler.post(() -> {
                            if ("success".equals(statusResponse.getStatus())) {
                                callback.onStatusUpdate(
                                        statusResponse.getGameStatus(),
                                        statusResponse.getCurrentPlayer() != null ? statusResponse.getCurrentPlayer() : 1,
                                        statusResponse.getWinner()
                                );
                            } else {
                                callback.onFailure("Status check failed");
                            }
                        });
                    } else {
                        mainHandler.post(() -> callback.onFailure("Server error: " + response.code()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Status parse error: " + e.getMessage());
                    mainHandler.post(() -> callback.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    // 5. Make Placement Move
    public void makeMove(String gameId, int boardX, int boardY, String playerId, GameCallback callback) {
        Log.d(TAG, "Making placement move: (" + boardX + "," + boardY + ") for player: " + playerId);

        MoveRequest moveRequest = new MoveRequest(boardX, boardY);
        moveRequest.setPlayerId(playerId);
        String jsonRequest = gson.toJson(moveRequest);

        Log.d(TAG, "Placement request JSON: " + jsonRequest);

        RequestBody body = RequestBody.create(jsonRequest, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId + "/moves")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Placement move failed", e);
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Placement response: " + jsonResponse);

                    if (response.isSuccessful()) {
                        GameResponse gameResponse = gson.fromJson(jsonResponse, GameResponse.class);
                        if ("success".equals(gameResponse.getStatus())) {
                            mainHandler.post(() -> callback.onSuccess(gameResponse.getGame()));
                        } else {
                            mainHandler.post(() -> callback.onFailure(gameResponse.getMessage()));
                        }
                    } else {
                        try {
                            GameResponse errorResponse = gson.fromJson(jsonResponse, GameResponse.class);
                            String errorMsg = (errorResponse != null && errorResponse.getMessage() != null)
                                    ? errorResponse.getMessage()
                                    : "Server error: " + response.code();
                            mainHandler.post(() -> callback.onFailure(errorMsg));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onFailure("Server error: " + response.code() + " - " + jsonResponse));
                        }
                    }
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    mainHandler.post(() -> callback.onFailure("Invalid response format"));
                }
            }
        });
    }

    // 6. Make Movement Move
    public void makeMoveMovement(String gameId, int fromX, int fromY, int toX, int toY, String playerId, GameCallback callback) {
        Log.d(TAG, "Making movement move: (" + fromX + "," + fromY + ") -> (" + toX + "," + toY + ") for player: " + playerId);

        MoveRequest movementRequest = new MoveRequest(fromX, fromY, toX, toY);
        movementRequest.setPlayerId(playerId);
        String jsonRequest = gson.toJson(movementRequest);

        Log.d(TAG, "Movement request JSON: " + jsonRequest);

        RequestBody body = RequestBody.create(jsonRequest, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId + "/moves")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Movement move failed", e);
                mainHandler.post(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Movement response: " + jsonResponse);

                    if (response.isSuccessful()) {
                        GameResponse gameResponse = gson.fromJson(jsonResponse, GameResponse.class);
                        if ("success".equals(gameResponse.getStatus())) {
                            mainHandler.post(() -> callback.onSuccess(gameResponse.getGame()));
                        } else {
                            mainHandler.post(() -> callback.onFailure(gameResponse.getMessage()));
                        }
                    } else {
                        try {
                            GameResponse errorResponse = gson.fromJson(jsonResponse, GameResponse.class);
                            String errorMsg = (errorResponse != null && errorResponse.getMessage() != null)
                                    ? errorResponse.getMessage()
                                    : "Server error: " + response.code();
                            mainHandler.post(() -> callback.onFailure(errorMsg));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onFailure("Server error: " + response.code() + " - " + jsonResponse));
                        }
                    }
                } catch (JsonSyntaxException e) {
                    Log.e(TAG, "JSON parsing error", e);
                    mainHandler.post(() -> callback.onFailure("Invalid response format"));
                }
            }
        });
    }
}