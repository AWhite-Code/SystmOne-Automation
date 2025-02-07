package systmone.automation.state;

import org.slf4j.Logger;
import systmone.automation.config.LoggingConfig;
import org.slf4j.event.Level;

/**
 * Tracks UI operation logging state to prevent excessive log generation.
 * Implements intelligent filtering based on movement thresholds and time intervals.
 */
public class UILoggingState {
    private final Logger logger;
    private long lastMovementLog = 0;
    private int lastReportedY = -1;

    public UILoggingState(Logger logger) {
        this.logger = logger;
    }

    /**
     * Determines if a movement event should be logged based on configured thresholds.
     * 
     * @param newY The new Y position to potentially log
     * @param level The logging level to use if logged
     * @param message The message format string
     * @param args Message format arguments
     */
    public void logMovementIfSignificant(int newY, Level level, String message, Object... args) {
        if (!shouldLogMovement(newY)) {
            return;
        }

        switch (level) {
            case INFO:
                logger.info(message, args);
                break;
            case DEBUG:
                logger.debug(message, args);
                break;
            case WARN:
                logger.warn(message, args);
                break;
            default:
                logger.debug(message, args);
        }
    }

    private boolean shouldLogMovement(int newY) {
        long now = System.currentTimeMillis();
        if (now - lastMovementLog < LoggingConfig.MINIMUM_LOG_INTERVAL) {
            return false;
        }
        if (Math.abs(newY - lastReportedY) < LoggingConfig.MINIMUM_MOVEMENT_THRESHOLD) {
            return false;
        }
        lastMovementLog = now;
        lastReportedY = newY;
        return true;
    }
}