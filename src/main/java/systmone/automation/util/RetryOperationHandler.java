package systmone.automation.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import systmone.automation.killswitch.GlobalKillswitch;

import java.util.function.Supplier;

/**
 * Provides retry functionality for UI operations with proper error handling and cleanup.
 * Designed to work with popup detection and window management operations.
 */
public class RetryOperationHandler {
    private static final Logger logger = LoggerFactory.getLogger(RetryOperationHandler.class);

    private final int maxAttempts;
    private final long delayBetweenAttempts;
    private final GlobalKillswitch killSwitch;
    
    public RetryOperationHandler(int maxAttempts, long delayBetweenAttempts, GlobalKillswitch killSwitch) {
        this.maxAttempts = maxAttempts;
        this.delayBetweenAttempts = delayBetweenAttempts;
        this.killSwitch = killSwitch;
    }

    /**
     * Executes an operation with retry logic, cleanup, and state management.
     * 
     * @param operation The operation to execute
     * @param cleanup The cleanup action to perform if operation fails
     * @param operationName Name of the operation for logging
     * @return true if operation succeeds, false otherwise
     */

    public boolean executeWithRetry(
            Supplier<Boolean> operation,
            Runnable cleanup,
            String operationName) {
        
        int attempts = 0;
        boolean success = false;
        
        while (attempts < maxAttempts && !success) {
            if (killSwitch.isKillSignalReceived()) {
                logger.info("Kill switch activated during {} - aborting", operationName);
                return false;
            }

            attempts++;
            
            try {
                success = operation.get();
                if (!success) {
                    if (cleanup != null) {
                        cleanup.run();
                    }
                    if (killSwitch.isKillSignalReceived()) {
                        return false;
                    }
                    Thread.sleep(delayBetweenAttempts);
                }
            } catch (Exception e) {
                logger.error("Error during {} (attempt {}): {}", 
                        operationName, attempts, e.getMessage());
                if (cleanup != null) {
                    cleanup.run();
                }
                try {
                    if (killSwitch.isKillSignalReceived()) {
                        return false;
                    }
                    Thread.sleep(delayBetweenAttempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        if (!success) {
            logger.error("{} failed after {} attempts", operationName, maxAttempts);
        }
        
        return success;
    }

    /**
     * Creates a builder for fluent configuration of retry operations.
     * 
     * @return A new RetryOperationBuilder instance
     */
    public static RetryOperationBuilder builder() {
        return new RetryOperationBuilder();
    }

    /**
     * Builder class for configuring retry operations with a fluent API.
     */

     public static class RetryOperationBuilder {
        private int maxAttempts = ApplicationConfig.DEFAULT_RETRY_ATTEMPTS;
        private long delayBetweenAttempts = ApplicationConfig.DEFAULT_RETRY_DELAY_MS;
        private GlobalKillswitch killSwitch;
        
        public RetryOperationBuilder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }
        
        public RetryOperationBuilder delayBetweenAttempts(long delayMs) {
            this.delayBetweenAttempts = delayMs;
            return this;
        }

        public RetryOperationBuilder killSwitch(GlobalKillswitch killSwitch) {
            this.killSwitch = killSwitch;
            return this;
        }
        
        public RetryOperationHandler build() {
            if (killSwitch == null) {
                throw new IllegalStateException("KillSwitch must be provided");
            }
            return new RetryOperationHandler(maxAttempts, delayBetweenAttempts, killSwitch);
        }
    }
}