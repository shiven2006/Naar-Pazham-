package com.gfg.NaarPazham;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;

public class NetworkService {
    private static final String BASE_URL = "http://10.0.2.2:8080/api/games";

    private final OkHttpClient client;
    private final Gson gson;

    public NetworkService() {
        client = new OkHttpClient();
        gson = new Gson();
    }

    public interface GameCallback {
        void onSuccess(ServerGameState gameState);
        void onFailure(String errorMessage);
    }

    public void createGame(GameCallback callback) {
        //Build the request
        Request request = new Request.Builder().url(BASE_URL + "/create").
                post(RequestBody.create("", MediaType.parse("application/json"))
                ).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonResponse = response.body().string();
                    ServerGameState gameState = gson.fromJson(jsonResponse, ServerGameState.class);
                    callback.onSuccess(gameState);
                } else {
                    callback.onFailure("Server error: " + response.code());
                }
            }
        });
    }

    public void getGameState(String gameId, GameCallback callback) {
        // Add some space around operators for readability
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId)  // Could also write as: BASE_URL + "/" + gameId
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonResponse = response.body().string();
                    ServerGameState gameState = gson.fromJson(jsonResponse, ServerGameState.class);
                    callback.onSuccess(gameState);
                } else {
                    callback.onFailure("Server error: " + response.code());
                }
            }
        });
    }

    public void makeMove(String gameId, int boardX, int boardY, GameCallback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + gameId + "/moves")  // ← Fixed: "/moves" not "/move"
                .post(RequestBody.create(gson.toJson(new MoveRequest(boardX, boardY)),
                        MediaType.parse("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonResponse = response.body().string();
                    ServerGameState gameState = gson.fromJson(jsonResponse, ServerGameState.class);
                    callback.onSuccess(gameState);
                } else {
                    callback.onFailure("Server error: " + response.code());
                }
            }
        });
    }
}


