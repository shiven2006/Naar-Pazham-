package com.gfg.NaarPazham;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Step 8: Network Retry Manager
 * Handles network retry logic with exponential backoff and circuit breaker
 */
public class NetworkRetryManager {
    private static final String TAG = "NetworkRetryManager";

    // Retry configuration
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY = 1000; // 1 second
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_DELAY = 30000; // 30 seconds

    // Circuit breaker configuration
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_RESET_TIME = 60000; // 1 minute

    private Handler retryHandler;
    private CircuitBreaker circuitBreaker;

    public interface RetryOperation {
        void execute(RetryContext context);
    }

    public interface RetryCallback {
        void onSuccess();
        void onFinalFailure(String error, int totalAttempts);
        void onRetryAttempt(int attempt, long delay);
    }

    public static class RetryConfig {
        public final int maxRetries;
        public final long initialDelay;
        public final double backoffMultiplier;
        public final long maxDelay;
        public final boolean useCircuitBreaker;

        public RetryConfig(int maxRetries, long initialDelay, double backoffMultiplier,
                           long maxDelay, boolean useCircuitBreaker) {
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
            this.backoffMultiplier = backoffMultiplier;
            this.maxDelay = maxDelay;
            this.useCircuitBreaker = useCircuitBreaker;
        }

        public static RetryConfig defaultConfig() {
            return new RetryConfig(DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_DELAY,
                    DEFAULT_BACKOFF_MULTIPLIER, DEFAULT_MAX_DELAY, true);
        }

        public static RetryConfig quickRetry() {
            return new RetryConfig(2, 500, 1.5, 5000, false);
        }

        public static RetryConfig aggressiveRetry() {
            return new RetryConfig(5, 2000, 2.5, 60000, true);
        }
    }

    public static class RetryContext {
        private final int currentAttempt;
        private final int maxRetries;
        private final long currentDelay;
        private final RetryCallback callback;
        private final String operationId;

        public RetryContext(int currentAttempt, int maxRetries, long currentDelay,
                            RetryCallback callback, String operationId) {
            this.currentAttempt = currentAttempt;
            this.maxRetries = maxRetries;
            this.currentDelay = currentDelay;
            this.callback = callback;
            this.operationId = operationId;
        }

        public void success() {
            callback.onSuccess();
        }

        public void failure(String error) {
            if (currentAttempt >= maxRetries) {
                callback.onFinalFailure(error, currentAttempt);
            } else {
                // This will be handled by the retry manager
                throw new RetryableException(error);
            }
        }

        public int getCurrentAttempt() { return currentAttempt; }
        public int getMaxRetries() { return maxRetries; }
        public String getOperationId() { return operationId; }
    }

    public static class RetryableException extends RuntimeException {
        public RetryableException(String message) {
            super(message);
        }
    }

    /**
     * Simple Circuit Breaker implementation
     */
    private static class CircuitBreaker {
        private enum State { CLOSED, OPEN, HALF_OPEN }

        private State state = State.CLOSED;
        private int failureCount = 0;
        private long lastFailureTime = 0;
        private final int threshold;
        private final long resetTimeout;

        public CircuitBreaker(int threshold, long resetTimeout) {
            this.threshold = threshold;
            this.resetTimeout = resetTimeout;
        }

        public boolean isOpen() {
            if (state == State.OPEN &&
                    System.currentTimeMillis() - lastFailureTime > resetTimeout) {
                state = State.HALF_OPEN;
                Log.d(TAG, "Circuit breaker moved to HALF_OPEN");
            }

            return state == State.OPEN;
        }

        public void recordSuccess() {
            failureCount = 0;
            state = State.CLOSED;
        }

        public void recordFailure() {
            failureCount++;
            lastFailureTime = System.currentTimeMillis();

            if (state == State.HALF_OPEN) {
                state = State.OPEN;
                Log.w(TAG, "Circuit breaker opened after half-open failure");
            } else if (failureCount >= threshold) {
                state = State.OPEN;
                Log.w(TAG, "Circuit breaker opened after " + failureCount + " failures");
            }
        }

        public State getState() { return state; }
        public int getFailureCount() { return failureCount; }
    }

    public NetworkRetryManager() {
        this.retryHandler = new Handler(Looper.getMainLooper());
        this.circuitBreaker = new CircuitBreaker(CIRCUIT_BREAKER_THRESHOLD, CIRCUIT_BREAKER_RESET_TIME);
    }

    /**
     * Execute operation with default retry configuration
     */
    public void executeWithRetry(String operationId, RetryOperation operation, RetryCallback callback) {
        executeWithRetry(operationId, operation, callback, RetryConfig.defaultConfig());
    }

    /**
     * Execute operation with custom retry configuration
     */
    public void executeWithRetry(String operationId, RetryOperation operation,
                                 RetryCallback callback, RetryConfig config) {
        executeAttempt(operationId, operation, callback, config, 1, config.initialDelay);
    }

    /**
     * Execute a single attempt of the operation
     */
    private void executeAttempt(String operationId, RetryOperation operation,
                                RetryCallback callback, RetryConfig config,
                                int attempt, long currentDelay) {

        // Check circuit breaker
        if (config.useCircuitBreaker && circuitBreaker.isOpen()) {
            Log.w(TAG, "Circuit breaker is open, failing fast for: " + operationId);
            callback.onFinalFailure("Service temporarily unavailable (circuit breaker open)", attempt);
            return;
        }

        Log.d(TAG, "Executing " + operationId + " - attempt " + attempt + "/" + config.maxRetries);

        try {
            RetryContext context = new RetryContext(attempt, config.maxRetries, currentDelay,
                    callback, operationId);
            operation.execute(context);

            // If we get here without exception, operation initiated successfully
            // Success/failure will be reported via context.success()/context.failure()

        } catch (RetryableException e) {
            handleRetryableFailure(operationId, operation, callback, config, attempt,
                    currentDelay, e.getMessage());
        } catch (Exception e) {
            // Non-retryable exception
            Log.e(TAG, "Non-retryable error in " + operationId, e);
            circuitBreaker.recordFailure();
            callback.onFinalFailure("Non-retryable error: " + e.getMessage(), attempt);
        }
    }

    /**
     * Handle a retryable failure
     */
    private void handleRetryableFailure(String operationId, RetryOperation operation,
                                        RetryCallback callback, RetryConfig config,
                                        int attempt, long currentDelay, String error) {

        circuitBreaker.recordFailure();

        if (attempt >= config.maxRetries) {
            Log.e(TAG, "Max retries exceeded for " + operationId + ": " + error);
            callback.onFinalFailure(error, attempt);
            return;
        }

        // Calculate next delay with exponential backoff
        long nextDelay = Math.min((long)(currentDelay * config.backoffMultiplier), config.maxDelay);

        Log.w(TAG, "Retrying " + operationId + " in " + nextDelay + "ms (attempt " +
                (attempt + 1) + "/" + config.maxRetries + ") - Error: " + error);

        callback.onRetryAttempt(attempt + 1, nextDelay);

        // Schedule retry
        retryHandler.postDelayed(() -> {
            executeAttempt(operationId, operation, callback, config, attempt + 1, nextDelay);
        }, nextDelay);
    }

    /**
     * Record a successful operation (for circuit breaker)
     */
    public void recordSuccess() {
        circuitBreaker.recordSuccess();
    }

    /**
     * Check if circuit breaker is open
     */
    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.isOpen();
    }

    /**
     * Get circuit breaker statistics
     */
    public String getCircuitBreakerStatus() {
        return "State: " + circuitBreaker.getState() +
                ", Failures: " + circuitBreaker.getFailureCount();
    }

    /**
     * Cancel all pending retry operations
     */
    public void cancelAllRetries() {
        retryHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "All retry operations cancelled");
    }

    /**
     * Convenience method for simple retry with exponential backoff
     */
    public static void retryWithBackoff(String operationId, Runnable operation,
                                        int maxRetries, Runnable onFinalFailure) {
        NetworkRetryManager manager = new NetworkRetryManager();

        manager.executeWithRetry(operationId,
                context -> {
                    try {
                        operation.run();
                        context.success();
                    } catch (Exception e) {
                        context.failure(e.getMessage());
                    }
                },
                new RetryCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, operationId + " succeeded");
                    }

                    @Override
                    public void onFinalFailure(String error, int totalAttempts) {
                        Log.e(TAG, operationId + " failed after " + totalAttempts + " attempts: " + error);
                        onFinalFailure.run();
                    }

                    @Override
                    public void onRetryAttempt(int attempt, long delay) {
                        Log.d(TAG, operationId + " retrying attempt " + attempt + " in " + delay + "ms");
                    }
                },
                new RetryConfig(maxRetries, 2000, 2.0, 30000, false)
        );
    }

    /**
     * Create a pre-configured retry manager for matchmaking operations
     */
    public static NetworkRetryManager forMatchmaking() {
        NetworkRetryManager manager = new NetworkRetryManager();
        // Matchmaking operations can be more patient with retries
        return manager;
    }

    /**
     * Create a pre-configured retry manager for game operations
     */
    public static NetworkRetryManager forGameOperations() {
        NetworkRetryManager manager = new NetworkRetryManager();
        // Game operations need faster response times
        return manager;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        cancelAllRetries();
        Log.d(TAG, "NetworkRetryManager cleaned up");
    }
}