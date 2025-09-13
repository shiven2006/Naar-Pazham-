package com.gfg.NaarPazham;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkService {
    private static final String TAG = "NetworkService";
    private static final String BASE_URL = "http://production-env.eba-nzvhkrn5.us-east-1.elasticbeanstalk.com";
    private RequestQueue requestQueue;
    private static NetworkService instance;
    private Context context;
    private final AtomicInteger pendingRequests = new AtomicInteger(0);
    private String lastRequestId = null;

    private static final int REQUEST_TIMEOUT_MS = 30000; // 30 seconds
    private static final int MAX_RETRIES = 2;
    private static final float BACKOFF_MULTIPLIER = 2.0f;

    private Handler matchmakingHandler;
    private Runnable matchmakingPoller;
    private boolean isPollingActive = false;
    private static final int POLLING_INTERVAL_MS = 3000; // 3 seconds
    private static final int MAX_POLLING_ATTEMPTS = 60; // 3 minutes total
    private int currentPollingAttempts = 0;


    // Singleton pattern
    public static synchronized NetworkService getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkService(context.getApplicationContext());
        }
        return instance;
    }


    public NetworkService() {
        // Default constructor for backward compatibility
    }

    private NetworkService(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
    }

    private void logNetworkDebug(String operation, String details) {
        Log.d(TAG, "NETWORK_DEBUG: " + operation + " - " + details + " [Pending: " + pendingRequests.get() + "]");
    }

    private String getRequestId() {
        return "REQ_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    // Initialize if not using singleton
    public void initialize(Context context) {
        if (this.context == null) {
            this.context = context.getApplicationContext();
            this.requestQueue = Volley.newRequestQueue(this.context);
        }
    }

    // === MAIN MATCHMAKING METHODS ===

    /**
     * Primary matchmaking method - matches GameController's /matchmaking/find endpoint
     */
    public void findMatchWithDeviceId(String playerId, String deviceId, MatchmakingCallback callback) {
        String requestId = getRequestId();

        if (playerId == null || playerId.trim().isEmpty()) {
            Log.e(TAG, "MATCHMAKING_ERROR [" + requestId + "]: Player ID is null/empty");
            callback.onFailure("Player ID is required");
            return;
        }

        if (deviceId == null || deviceId.trim().isEmpty()) {
            Log.e(TAG, "MATCHMAKING_ERROR [" + requestId + "]: Device ID is null/empty for player " + playerId);
            callback.onFailure("Device ID is required");
            return;
        }

        String cleanPlayerId = playerId.trim();
        String cleanDeviceId = deviceId.trim();

        logNetworkDebug("FIND_MATCH_START [" + requestId + "]",
                "Player: " + cleanPlayerId + ", Device: " + cleanDeviceId);

        String url = BASE_URL + "/api/games/matchmaking/find";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("playerId", cleanPlayerId);
            Log.d(TAG, "MATCHMAKING_REQUEST [" + requestId + "]: URL: " + url + ", Body: " + requestBody.toString());
        } catch (JSONException e) {
            Log.e(TAG, "MATCHMAKING_ERROR [" + requestId + "]: Failed to create request body", e);
            callback.onFailure("Failed to create request");
            return;
        }

        pendingRequests.incrementAndGet();
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                response -> {
                    pendingRequests.decrementAndGet();
                    Log.d(TAG, "MATCHMAKING_RESPONSE [" + requestId + "]: " + response.toString());
                    logNetworkDebug("FIND_MATCH_SUCCESS [" + requestId + "]", "Processing response");
                    handleMatchmakingResponse(response, callback, requestId);
                },
                error -> {
                    pendingRequests.decrementAndGet();
                    Log.e(TAG, "MATCHMAKING_FAILED [" + requestId + "]: Error details: " + error.toString());
                    if (error.networkResponse != null) {
                        Log.e(TAG, "MATCHMAKING_FAILED [" + requestId + "]: Status: " +
                                error.networkResponse.statusCode + ", Data: " +
                                new String(error.networkResponse.data));
                    }
                    logNetworkDebug("FIND_MATCH_FAILED [" + requestId + "]", "Error: " + error.getMessage());
                    handleMatchmakingError(error, callback);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("X-Device-ID", cleanDeviceId);
                headers.put("X-Request-ID", requestId);
                Log.d(TAG, "MATCHMAKING_HEADERS [" + requestId + "]: " + headers.toString());
                return headers;
            }
            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                return super.parseNetworkResponse(response);
            }
        };

        request.setRetryPolicy(new DefaultRetryPolicy(
                REQUEST_TIMEOUT_MS,
                MAX_RETRIES,
                BACKOFF_MULTIPLIER
        ));

        addToRequestQueue(request);
        logNetworkDebug("FIND_MATCH_QUEUED [" + requestId + "]", "Request queued for execution");
    }

    /**
     * Backward compatibility method
     */
    public void findMatch(String playerId, MatchmakingCallback callback) {
        String deviceId = DeviceIdGenerator.getDeviceId();
        if (deviceId == null) {
            deviceId = DeviceIdGenerator.getOrCreateDeviceId();
        }
        findMatchWithDeviceId(playerId, deviceId, callback);
    }

    /**
     * Cancel matchmaking - matches GameController's /matchmaking/cancel endpoint
     */
    public void cancelMatchmaking(String playerId, GameCallback callback) {
        if (playerId == null || playerId.trim().isEmpty()) {
            callback.onFailure("Player ID is required");
            return;
        }

        String cleanPlayerId = playerId.trim();
        String url = BASE_URL + "/api/games/matchmaking/cancel";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("playerId", cleanPlayerId);
        } catch (JSONException e) {
            callback.onFailure("Failed to create request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                response -> {
                    Log.d(TAG, "Cancel response: " + response.toString());
                    String status = response.optString("status", "unknown");
                    if ("success".equals(status)) {
                        callback.onSuccess(null);
                    } else {
                        String message = response.optString("message", "Cancel failed");
                        callback.onFailure(message);
                    }
                },
                error -> {
                    Log.w(TAG, "Cancel failed", error);
                    // 404 is expected if player wasn't in queue - treat as success
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        callback.onSuccess(null);
                    } else {
                        callback.onFailure(parseVolleyError(error));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    /**
     * Get matchmaking status - matches GameController's /matchmaking/status/{playerId} endpoint
     */
    public void getMatchmakingStatus(String playerId, MatchmakingCallback callback) {
        if (playerId == null || playerId.trim().isEmpty()) {
            callback.onFailure("Player ID is required");
            return;
        }

        String cleanPlayerId = playerId.trim();
        String url = BASE_URL + "/api/games/matchmaking/status/" + cleanPlayerId;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(TAG, "Status response: " + response.toString());
                    handleMatchmakingResponse(response, callback, "STATUS_CHECK");
                },
                error -> {
                    Log.d(TAG, "Status check failed", error);
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        // Player not in queue - check if they have an active game
                        checkForActiveGame(cleanPlayerId, callback);
                    } else {
                        callback.onFailure(parseVolleyError(error));
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    // === RESPONSE HANDLING ===

    private void handleMatchmakingResponse(JSONObject response, MatchmakingCallback callback, String requestId) {
        try {
            String status = response.optString("status", "unknown");
            String message = response.optString("message", "");
            String gameId = response.optString("gameId");
            String playerId = response.optString("playerId");

            Log.d(TAG, "RESPONSE_HANDLER [" + requestId + "]: Status: " + status +
                    ", GameId: " + gameId + ", PlayerId: " + playerId + ", Message: " + message);

            switch (status) {
                case "match_found":
                    Log.i(TAG, "MATCH_FOUND [" + requestId + "]: GameId: " + gameId + ", PlayerId: " + playerId);
                    handleMatchFound(response, callback, requestId);
                    break;

                case "waiting":
                    Log.d(TAG, "WAITING [" + requestId + "]: PlayerId: " + playerId + ", GameId: " + gameId);
                    callback.onWaitingForMatch(playerId);
                    break;

                case "not_found":
                    Log.d(TAG, "NOT_FOUND [" + requestId + "]: PlayerId: " + playerId);
                    callback.onWaitingForMatch(playerId);
                    break;

                case "error":
                    Log.e(TAG, "SERVER_ERROR [" + requestId + "]: " + message);
                    callback.onFailure(message);
                    break;

                default:
                    Log.w(TAG, "UNKNOWN_STATUS [" + requestId + "]: " + status + " with message: " + message);
                    callback.onFailure("Unknown response: " + status);
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "RESPONSE_HANDLER_ERROR [" + requestId + "]: Failed to process server response", e);
            callback.onFailure("Failed to process server response");
        }
    }

    private void handleMatchFound(JSONObject response, MatchmakingCallback callback, String requestId) {
        try {
            String gameId = response.optString("gameId");
            String playerId = response.optString("playerId");
            Integer playerNumber = response.has("playerNumber") ? response.getInt("playerNumber") : null;
            JSONObject gameStateJson = response.optJSONObject("gameState");

            Log.d(TAG, "Match found [" + requestId + "] - GameID: " + gameId + ", PlayerNum: " + playerNumber);

            if (gameId == null || gameId.isEmpty() || playerId == null || playerId.isEmpty() || playerNumber == null) {
                Log.e(TAG, "Incomplete match data [" + requestId + "]: gameId=" + gameId +
                        ", playerId=" + playerId + ", playerNumber=" + playerNumber);
                callback.onFailure("Incomplete match data");
                return;
            }

            // Try to use embedded game state first
            if (gameStateJson != null) {
                Log.d(TAG, "Using embedded game state [" + requestId + "]");
                ServerGameState gameState = parseServerGameState(gameStateJson);
                if (gameState != null && !gameState.isFinished()) {
                    boolean isPlayer1 = (playerNumber == 1);
                    callback.onMatchFound(gameState, isPlayer1, playerId);
                    return;
                }
            }

            // Fallback: fetch fresh game state
            Log.d(TAG, "Fetching fresh game state [" + requestId + "] for: " + gameId);
            getGameState(gameId, playerId, new GameCallback() {
                @Override
                public void onSuccess(ServerGameState gameState) {
                    if (gameState != null && !gameState.isFinished()) {
                        boolean isPlayer1 = (playerNumber == 1);
                        Log.d(TAG, "Fresh game state retrieved [" + requestId + "]");
                        callback.onMatchFound(gameState, isPlayer1, playerId);
                    } else {
                        Log.e(TAG, "Game not available [" + requestId + "]: gameState=" + gameState);
                        callback.onFailure("Game not available");
                    }
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "Failed to retrieve game [" + requestId + "]: " + errorMessage);
                    callback.onFailure("Could not retrieve game: " + errorMessage);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error processing match found [" + requestId + "]", e);
            callback.onFailure("Failed to process match");
        }
    }

    private void handleMatchmakingError(VolleyError error, MatchmakingCallback callback) {
        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            String responseData = new String(error.networkResponse.data);

            Log.e(TAG, "Matchmaking error - Status: " + statusCode + ", Response: " + responseData);

            switch (statusCode) {
                case 409:
                    // Conflict - player already in queue or game
                    Log.d(TAG, "409 conflict - player already in queue or game");
                    callback.onAlreadyInQueue();
                    break;

                case 404:
                    // Not found - treat as general failure
                    Log.e(TAG, "404 - Matchmaking service not found");
                    callback.onFailure("Matchmaking service not available");
                    break;

                default:
                    Log.e(TAG, "Unexpected error status: " + statusCode);
                    callback.onFailure(parseVolleyError(error));
                    break;
            }
        } else {
            Log.e(TAG, "Network connection failed - no response");
            callback.onFailure("Network connection failed");
        }
    }

    private void checkForActiveGame(String playerId, MatchmakingCallback callback) {
        Log.d(TAG, "Checking for active game for player: " + playerId);

        // First try getting game state directly
        getGameStateForPlayer(playerId, new GameCallback() {
            @Override
            public void onSuccess(ServerGameState gameState) {
                if (gameState != null && !gameState.isFinished()) {
                    boolean isPlayer1 = playerId.equals(gameState.getPlayer1Id());
                    Log.d(TAG, "Found active game: " + gameState.getGameId());
                    callback.onMatchFound(gameState, isPlayer1, playerId);
                } else {
                    Log.d(TAG, "No active game found");
                    callback.onWaitingForMatch(playerId);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.d(TAG, "Active game check failed: " + errorMessage);
                callback.onWaitingForMatch(playerId);
            }
        });
    }

    private void getGameStateForPlayer(String playerId, GameCallback callback) {
        // This would need a custom endpoint or we can iterate through known games
        // For now, just call the failure callback
        callback.onFailure("No active game endpoint available");
    }

    // === GAME OPERATIONS ===

    /**
     * Get game state - matches GameController's /{gameId} endpoint
     */
    public void getGameState(String gameId, String playerId, GameCallback callback) {
        if (gameId == null || gameId.trim().isEmpty()) {
            callback.onFailure("Game ID is required");
            return;
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            callback.onFailure("Player ID is required");
            return;
        }

        String cleanGameId = gameId.trim();
        String cleanPlayerId = playerId.trim();
        String url = BASE_URL + "/api/games/" + cleanGameId + "?playerId=" + cleanPlayerId;

        Log.d(TAG, "Fetching game state from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(TAG, "Game state response: " + response.toString());
                    handleGameResponse(response, callback);
                },
                error -> {
                    Log.e(TAG, "Get game failed", error);
                    callback.onFailure(parseVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    /**
     * Process move - matches GameController's /{gameId}/moves endpoint
     */
    public void processMove(String gameId, String playerId, int boardX, int boardY,
                            Integer fromX, Integer fromY, GameCallback callback) {
        if (gameId == null || gameId.trim().isEmpty()) {
            callback.onFailure("Game ID is required");
            return;
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            callback.onFailure("Player ID is required");
            return;
        }

        String cleanGameId = gameId.trim();
        String cleanPlayerId = playerId.trim();
        String url = BASE_URL + "/api/games/" + cleanGameId + "/moves";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("playerId", cleanPlayerId);
            requestBody.put("boardX", boardX);
            requestBody.put("boardY", boardY);

            if (fromX != null) requestBody.put("fromX", fromX);
            if (fromY != null) requestBody.put("fromY", fromY);

            Log.d(TAG, "Move request - GameID: " + cleanGameId + ", Player: " + cleanPlayerId +
                    ", Move: (" + boardX + "," + boardY + "), From: (" + fromX + "," + fromY + ")");

        } catch (JSONException e) {
            callback.onFailure("Failed to create request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                response -> handleGameResponse(response, callback),
                error -> {
                    Log.e(TAG, "Move request failed", error);
                    callback.onFailure(parseVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    /**
     * Leave game - matches GameController's /{gameId}/leave endpoint
     */
    public void leaveGame(String gameId, String playerId, GameCallback callback) {
        if (gameId == null || gameId.trim().isEmpty()) {
            callback.onFailure("Game ID is required");
            return;
        }

        if (playerId == null || playerId.trim().isEmpty()) {
            callback.onFailure("Player ID is required");
            return;
        }

        String cleanGameId = gameId.trim();
        String cleanPlayerId = playerId.trim();
        String url = BASE_URL + "/api/games/" + cleanGameId + "/leave?playerId=" + cleanPlayerId;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                null,
                response -> {
                    String status = response.optString("status", "unknown");
                    if ("success".equals(status) || "info".equals(status)) {
                        callback.onSuccess(null);
                    } else {
                        String message = response.optString("message", "Leave failed");
                        callback.onFailure(message);
                    }
                },
                error -> {
                    Log.e(TAG, "Leave game failed", error);
                    callback.onFailure(parseVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    private void handleGameResponse(JSONObject response, GameCallback callback) {
        try {
            String status = response.optString("status", "unknown");

            if ("success".equals(status)) {
                JSONObject gameStateJson = response.optJSONObject("game");
                if (gameStateJson == null) {
                    gameStateJson = response.optJSONObject("gameState");
                }

                if (gameStateJson != null) {
                    ServerGameState gameState = parseServerGameState(gameStateJson);
                    if (gameState != null) {
                        callback.onSuccess(gameState);
                    } else {
                        callback.onFailure("Invalid game state data");
                    }
                } else {
                    callback.onFailure("Missing game state in response");
                }
            } else {
                String message = response.optString("message", "Request failed");
                callback.onFailure(message);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling game response", e);
            callback.onFailure("Failed to process server response");
        }
    }

    // === PARSING METHODS ===

    private ServerGameState parseServerGameState(JSONObject gameStateJson) {
        try {
            ServerGameState gameState = new ServerGameState();

            // Basic game info
            gameState.setGameId(gameStateJson.optString("gameId", null));
            gameState.setPlayer1Id(gameStateJson.optString("player1Id", null));
            gameState.setPlayer2Id(gameStateJson.optString("player2Id", null));

            // Game status
            gameState.setGameStatus(gameStateJson.optString("gameStatus", "WAITING_FOR_PLAYERS"));
            gameState.setPlayer1Turn(gameStateJson.optBoolean("player1Turn", true));
            gameState.setPlacementPhase(gameStateJson.optBoolean("placementPhase", true));

            // Handle winner field - can be null
            if (gameStateJson.has("winner") && !gameStateJson.isNull("winner")) {
                gameState.setWinner(gameStateJson.optString("winner"));
            } else {
                gameState.setWinner(null);
            }

            // Game flags
            gameState.setGameStarted(gameStateJson.optBoolean("gameStarted", false));
            gameState.setPlayer1Assigned(gameStateJson.optBoolean("player1Assigned", false));
            gameState.setPlayer2Assigned(gameStateJson.optBoolean("player2Assigned", false));

            // Move counter
            gameState.setTotalMoves(gameStateJson.optInt("totalMoves", 0));

            // Timestamps
            if (gameStateJson.has("lastActivity")) {
                gameState.setLastActivity(gameStateJson.getLong("lastActivity"));
            }

            // Parse player moves
            parsePlayerMoves(gameState, gameStateJson);

            Log.d(TAG, "Parsed game state - ID: " + gameState.getGameId() +
                    ", Status: " + gameState.getGameStatus() +
                    ", Players: " + gameState.getPlayer1Id() + " vs " + gameState.getPlayer2Id());

            return gameState;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing server game state", e);
            return null;
        }
    }

    public void getQueueStatus(MatchmakingStatusCallback callback) {
        String url = BASE_URL + "/api/games/matchmaking/queue-status";

        Log.d(TAG, "Requesting queue status from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        String status = response.optString("status", "unknown");
                        if ("success".equals(status)) {
                            int queueSize = response.optInt("queueSize", 0);
                            Log.d(TAG, "Queue status - Size: " + queueSize);
                            callback.onStatusUpdate(queueSize);
                        } else {
                            Log.w(TAG, "Queue status failed: " + status);
                            callback.onFailure("Failed to get queue status");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing queue status", e);
                        callback.onFailure("Error parsing queue status");
                    }
                },
                error -> {
                    Log.e(TAG, "Queue status request failed", error);
                    callback.onFailure(parseVolleyError(error));
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    private void parsePlayerMoves(ServerGameState gameState, JSONObject gameStateJson) {
        try {
            List<PlayerMove> player1Moves = new ArrayList<>();
            List<PlayerMove> player2Moves = new ArrayList<>();

            // Parse player1 moves
            if (gameStateJson.has("player1Moves")) {
                JSONArray player1MovesArray = gameStateJson.getJSONArray("player1Moves");
                for (int i = 0; i < player1MovesArray.length(); i++) {
                    JSONObject moveJson = player1MovesArray.getJSONObject(i);
                    PlayerMove move = parsePlayerMove(moveJson, true);
                    if (move != null) {
                        player1Moves.add(move);
                    }
                }
            }

            // Parse player2 moves
            if (gameStateJson.has("player2Moves")) {
                JSONArray player2MovesArray = gameStateJson.getJSONArray("player2Moves");
                for (int i = 0; i < player2MovesArray.length(); i++) {
                    JSONObject moveJson = player2MovesArray.getJSONObject(i);
                    PlayerMove move = parsePlayerMove(moveJson, false);
                    if (move != null) {
                        player2Moves.add(move);
                    }
                }
            }

            gameState.setPlayer1Moves(player1Moves);
            gameState.setPlayer2Moves(player2Moves);

            Log.d(TAG, "Parsed moves - Player1: " + player1Moves.size() + ", Player2: " + player2Moves.size());

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing player moves", e);
            gameState.setPlayer1Moves(new ArrayList<>());
            gameState.setPlayer2Moves(new ArrayList<>());
        }
    }

    private PlayerMove parsePlayerMove(JSONObject moveJson, boolean isPlayer1) {
        try {
            Integer boardX = moveJson.has("boardX") && !moveJson.isNull("boardX")
                    ? moveJson.getInt("boardX") : null;
            Integer boardY = moveJson.has("boardY") && !moveJson.isNull("boardY")
                    ? moveJson.getInt("boardY") : null;

            if (boardX == null || boardY == null) {
                Log.w(TAG, "Invalid move coordinates: boardX=" + boardX + ", boardY=" + boardY);
                return null;
            }

            boolean player1Flag = moveJson.has("player1")
                    ? moveJson.getBoolean("player1") : isPlayer1;

            return new PlayerMove(boardX, boardY, player1Flag);

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing player move", e);
            return null;
        }
    }

    // === UTILITY METHODS ===

    private String parseVolleyError(VolleyError error) {
        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;

            switch (statusCode) {
                case 400: return "Invalid request";
                case 401: return "Authentication required";
                case 403: return "Access denied";
                case 404: return "Not found";
                case 409: return "Conflict - already in game or queue";
                case 500: return "Server error";
                case 503: return "Service unavailable";
                default: return "Network error (Code: " + statusCode + ")";
            }
        } else {
            return "Network connection failed";
        }
    }

    /**
     * Enhanced polling with better queue handling
     */
    public void findMatchWithEnhancedPolling(String playerId, String deviceId, MatchmakingCallback callback) {
        if (matchmakingHandler == null) {
            matchmakingHandler = new Handler(Looper.getMainLooper());
        }

        String requestId = getRequestId();
        Log.i(TAG, "ENHANCED_POLLING: Starting matchmaking for player: " + playerId);

        // Stop any existing polling
        stopPolling();

        // Reset polling state
        isPollingActive = true;
        currentPollingAttempts = 0;

        // Initial match request
        findMatchWithDeviceId(playerId, deviceId, new MatchmakingCallback() {
            @Override
            public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                Log.i(TAG, "ENHANCED_POLLING: Immediate match found!");
                stopPolling();
                callback.onMatchFound(gameState, isPlayer1, playerId);
            }

            @Override
            public void onWaitingForMatch(String playerId) {
                Log.d(TAG, "ENHANCED_POLLING: Added to queue/waiting room, starting status polling...");
                startEnhancedStatusPolling(playerId, callback);
            }

            @Override
            public void onAlreadyInQueue() {
                Log.d(TAG, "ENHANCED_POLLING: Already in queue, starting status polling...");
                startEnhancedStatusPolling(playerId, callback);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "ENHANCED_POLLING: Initial request failed: " + errorMessage);
                stopPolling();
                callback.onFailure(errorMessage);
            }
        });
    }

    /**
     * Enhanced status polling that handles queue matching
     */
    private void startEnhancedStatusPolling(String playerId, MatchmakingCallback callback) {
        if (matchmakingPoller != null) {
            matchmakingHandler.removeCallbacks(matchmakingPoller);
        }

        final long pollingStartTime = System.currentTimeMillis();

        matchmakingPoller = new Runnable() {
            @Override
            public void run() {
                if (!isPollingActive) {
                    return;
                }

                currentPollingAttempts++;
                long elapsedTime = System.currentTimeMillis() - pollingStartTime;

                Log.d(TAG, "ENHANCED_POLLING: Status check " + currentPollingAttempts +
                        " (" + (elapsedTime / 1000) + "s elapsed)");

                // Timeout after 5 minutes
                if (elapsedTime >= 5 * 60 * 1000) {
                    Log.w(TAG, "ENHANCED_POLLING: Timeout after 5 minutes");
                    stopPolling();
                    callback.onFailure("Matchmaking timeout - please try again");
                    return;
                }

                // Check matchmaking status
                getMatchmakingStatus(playerId, new MatchmakingCallback() {
                    @Override
                    public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                        Log.i(TAG, "ENHANCED_POLLING: Match found during status check after " + (elapsedTime / 1000) + "s!");
                        stopPolling();
                        callback.onMatchFound(gameState, isPlayer1, playerId);
                    }

                    @Override
                    public void onWaitingForMatch(String playerId) {
                        if (!isPollingActive) return;

                        // Continue polling with adaptive interval
                        int interval = calculatePollingInterval(elapsedTime);
                        Log.d(TAG, "ENHANCED_POLLING: Still waiting, next check in " + (interval / 1000) + "s");

                        if (matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(matchmakingPoller, interval);
                        }
                    }

                    @Override
                    public void onAlreadyInQueue() {
                        if (!isPollingActive) return;

                        // Continue polling
                        int interval = calculatePollingInterval(elapsedTime);
                        Log.d(TAG, "ENHANCED_POLLING: In queue, next check in " + (interval / 1000) + "s");

                        if (matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(matchmakingPoller, interval);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // For status polling, some failures are expected (like 404)
                        // Continue polling unless it's a serious error
                        if (errorMessage.contains("Network connection") ||
                                errorMessage.contains("Server error")) {
                            Log.e(TAG, "ENHANCED_POLLING: Serious error during status check: " + errorMessage);
                            stopPolling();
                            callback.onFailure("Network error during matchmaking");
                        } else {
                            Log.d(TAG, "ENHANCED_POLLING: Minor status error (continuing): " + errorMessage);
                            if (isPollingActive && matchmakingHandler != null) {
                                int interval = calculatePollingInterval(elapsedTime);
                                matchmakingHandler.postDelayed(matchmakingPoller, interval);
                            }
                        }
                    }
                });
            }
        };

        // Start polling with initial delay
        matchmakingHandler.postDelayed(matchmakingPoller, 2000); // 2 seconds initial delay
    }
    /**
     * Calculate adaptive polling interval based on elapsed time
     */
    private int calculatePollingInterval(long elapsedTimeMs) {
        if (elapsedTimeMs < 30_000) {
            return 3_000; // 3 seconds for first 30 seconds
        } else if (elapsedTimeMs < 60_000) {
            return 5_000; // 5 seconds for next 30 seconds
        } else if (elapsedTimeMs < 2 * 60_000) {
            return 8_000; // 8 seconds for next minute
        } else {
            return 10_000; // 10 seconds for remaining time
        }
    }

    /**
     * Enhanced callback interface with queue progress updates
     */
    public interface EnhancedMatchmakingCallback {
        void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId);
        void onWaitingForMatch(String playerId, long waitTimeMs);
        void onQueueProgress(String playerId, int estimatedWaitSeconds);
        void onAlreadyInQueue();
        void onFailure(String errorMessage);
    }

    /**
     * Find match with progress callbacks
     */
    public void findMatchWithProgress(String playerId, String deviceId, EnhancedMatchmakingCallback callback) {
        findMatchWithEnhancedPolling(playerId, deviceId, new MatchmakingCallback() {
            private long matchmakingStartTime = System.currentTimeMillis();

            @Override
            public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                callback.onMatchFound(gameState, isPlayer1, playerId);
            }

            @Override
            public void onWaitingForMatch(String playerId) {
                long waitTime = System.currentTimeMillis() - matchmakingStartTime;
                callback.onWaitingForMatch(playerId, waitTime);

                // Estimate remaining wait time based on elapsed time
                int estimatedWait = Math.max(30, Math.min(120, 60 - (int)(waitTime / 1000)));
                callback.onQueueProgress(playerId, estimatedWait);
            }

            @Override
            public void onAlreadyInQueue() {
                callback.onAlreadyInQueue();
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    /**
     * Send periodic heartbeat while in queue (optional enhancement)
     */
    private void sendQueueHeartbeat(String playerId) {
        if (!isPollingActive) return;

        String url = BASE_URL + "/api/games/matchmaking/heartbeat";

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("playerId", playerId);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create heartbeat request", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                response -> Log.d(TAG, "HEARTBEAT: Sent for player " + playerId),
                error -> Log.d(TAG, "HEARTBEAT: Failed for player " + playerId)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        // Short timeout for heartbeat
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1.0f));
        addToRequestQueue(request);
    }

    /**
     * Get detailed queue status (optional)
     */
    public void getDetailedQueueStatus(String playerId, DetailedQueueCallback callback) {
        String url = BASE_URL + "/api/games/matchmaking/player-status/" + playerId;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        String status = response.optString("status", "unknown");
                        if ("in_queue".equals(status)) {
                            long queueTime = response.optLong("queueTimeMs", 0);
                            int estimatedWait = response.optInt("estimatedWaitSeconds", 0);
                            int position = response.optInt("queuePosition", 0);
                            callback.onQueueStatus(status, queueTime, estimatedWait, position);
                        } else {
                            callback.onQueueStatus(status, 0, 0, 0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing detailed queue status", e);
                        callback.onFailure("Failed to parse queue status");
                    }
                },
                error -> callback.onFailure(parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String deviceId = DeviceIdGenerator.getDeviceId();
                if (deviceId != null) {
                    headers.put("X-Device-ID", deviceId);
                }
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    public interface DetailedQueueCallback {
        void onQueueStatus(String status, long queueTimeMs, int estimatedWaitSeconds, int position);
        void onFailure(String errorMessage);
    }

    /**
     * Force refresh queue status (for testing)
     */
    public void forceQueueProcessing(String adminKey, SimpleCallback callback) {
        String url = BASE_URL + "/api/games/admin/process-queue";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                null,
                response -> {
                    String status = response.optString("status", "unknown");
                    String message = response.optString("message", "");
                    callback.onResult("success".equals(status), message);
                },
                error -> callback.onResult(false, parseVolleyError(error))
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("X-Admin-Key", adminKey);
                return headers;
            }
        };

        addToRequestQueue(request);
    }

    public interface SimpleCallback {
        void onResult(boolean success, String message);
    }








    private void addToRequestQueue(JsonObjectRequest request) {
        if (requestQueue == null) {
            Log.e(TAG, "Request queue not initialized");
            return;
        }
        request.setTag(TAG);
        requestQueue.add(request);
    }


    private void startPolling(String playerId, String deviceId, MatchmakingCallback callback) {
        if (matchmakingPoller != null) {
            matchmakingHandler.removeCallbacks(matchmakingPoller);
        }

        matchmakingPoller = new Runnable() {
            @Override
            public void run() {
                if (!isPollingActive) {
                    return;
                }

                currentPollingAttempts++;
                Log.d(TAG, "Polling attempt " + currentPollingAttempts + "/" + MAX_POLLING_ATTEMPTS);

                if (currentPollingAttempts >= MAX_POLLING_ATTEMPTS) {
                    Log.w(TAG, "Matchmaking polling timed out after " + currentPollingAttempts + " attempts");
                    stopPolling();
                    callback.onFailure("Matchmaking timeout - no opponent found");
                    return;
                }

                // Check matchmaking status
                getMatchmakingStatus(playerId, new MatchmakingCallback() {
                    @Override
                    public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                        Log.i(TAG, "Match found during polling!");
                        stopPolling();
                        callback.onMatchFound(gameState, isPlayer1, playerId);
                    }

                    @Override
                    public void onWaitingForMatch(String playerId) {
                        Log.d(TAG, "Still waiting... polling continues");
                        if (isPollingActive && matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    onWaitingForMatch(playerId); // re-invoke after delay
                                }
                            }, POLLING_INTERVAL_MS);
                        }
                    }

                    @Override
                    public void onAlreadyInQueue() {
                        Log.d(TAG, "Player in queue, continue polling");
                        // Continue polling
                        if (isPollingActive && matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(matchmakingPoller, POLLING_INTERVAL_MS);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Polling failed: " + errorMessage);
                        stopPolling();
                        callback.onFailure("Matchmaking failed: " + errorMessage);
                    }
                });
            }
        };

        // Start polling
        matchmakingHandler.postDelayed(matchmakingPoller, POLLING_INTERVAL_MS);
    }

    private void startStatusPolling(String playerId, MatchmakingCallback callback) {
        if (matchmakingPoller != null) {
            matchmakingHandler.removeCallbacks(matchmakingPoller);
        }

        matchmakingPoller = new Runnable() {
            @Override
            public void run() {
                if (!isPollingActive) {
                    return;
                }

                currentPollingAttempts++;
                Log.d(TAG, "Status polling attempt " + currentPollingAttempts + "/" + MAX_POLLING_ATTEMPTS);

                if (currentPollingAttempts >= MAX_POLLING_ATTEMPTS) {
                    Log.w(TAG, "Status polling timed out");
                    stopPolling();
                    callback.onFailure("Matchmaking timeout");
                    return;
                }

                getMatchmakingStatus(playerId, new MatchmakingCallback() {
                    @Override
                    public void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId) {
                        Log.i(TAG, "Match found during status polling!");
                        stopPolling();
                        callback.onMatchFound(gameState, isPlayer1, playerId);
                    }

                    @Override
                    public void onWaitingForMatch(String playerId) {
                        Log.d(TAG, "Still waiting in status polling");
                        if (isPollingActive && matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    onWaitingForMatch(playerId); // call again after delay
                                }
                            }, POLLING_INTERVAL_MS);
                        }
                    }

                    @Override
                    public void onAlreadyInQueue() {
                        Log.d(TAG, "Still in queue during status polling");
                        if (isPollingActive && matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(matchmakingPoller, POLLING_INTERVAL_MS);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.d(TAG, "Status polling failed (might be normal): " + errorMessage);
                        // For status polling failures, continue polling as player might not be in queue yet
                        if (isPollingActive && matchmakingHandler != null) {
                            matchmakingHandler.postDelayed(matchmakingPoller, POLLING_INTERVAL_MS);
                        }
                    }
                });
            }
        };

        matchmakingHandler.postDelayed(matchmakingPoller, POLLING_INTERVAL_MS);
    }

    public void stopPolling() {
        Log.d(TAG, "Stopping matchmaking polling");
        isPollingActive = false;
        currentPollingAttempts = 0;

        if (matchmakingHandler != null && matchmakingPoller != null) {
            matchmakingHandler.removeCallbacks(matchmakingPoller);
            matchmakingPoller = null;
        }
    }

    // Enhanced cancel with polling stop
    public void cancelMatchmakingWithPolling(String playerId, GameCallback callback) {
        Log.i(TAG, "Cancelling matchmaking with polling for player: " + playerId);

        // Stop polling first
        stopPolling();

        // Then cancel on server
        cancelMatchmaking(playerId, new GameCallback() {
            @Override
            public void onSuccess(ServerGameState gameState) {
                Log.i(TAG, "Matchmaking successfully cancelled");
                callback.onSuccess(gameState);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w(TAG, "Matchmaking cancel failed (might be normal): " + errorMessage);
                // Even if cancel fails, we stopped polling, so call success
                callback.onSuccess(null);
            }
        });
    }


    public void cleanup() {
        stopPolling();
        if (requestQueue != null) {
            requestQueue.cancelAll(TAG);
        }
    }

    // === CALLBACK INTERFACES ===

    public interface MatchmakingCallback {
        void onMatchFound(ServerGameState gameState, boolean isPlayer1, String playerId);
        void onWaitingForMatch(String playerId);
        void onAlreadyInQueue();
        void onFailure(String errorMessage);
    }

    public interface GameCallback {
        void onSuccess(ServerGameState gameState);
        void onFailure(String errorMessage);
    }

    public interface MatchmakingStatusCallback {
        void onStatusUpdate(int queueSize);
        void onFailure(String errorMessage);
    }

    public interface JoinGameCallback {
        void onSuccess(ServerGameState gameState, boolean isPlayer1, String playerId);
        void onFailure(String errorMessage);
    }

    public interface SimpleResponse {
        // For basic success/failure responses
    }
}