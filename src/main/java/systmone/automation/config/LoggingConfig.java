package systmone.automation.config;

import org.slf4j.event.Level;

/**
 * Configuration for system-wide logging behavior and thresholds.
 * Controls logging verbosity and filtering for UI operations.
 */
public class LoggingConfig {
    // Core logging behavior
    public static final Level DEFAULT_SCANNING_LEVEL = Level.DEBUG;
    public static final Level DEFAULT_MOVEMENT_LEVEL = Level.DEBUG;
    public static final Level DEFAULT_STABILITY_LEVEL = Level.INFO;
    
    // Thresholds and filters
    public static final boolean DETAILED_SCANNING_ENABLED = false;
    public static final boolean VERBOSE_MOVEMENT_ENABLED = false;
    public static final int MINIMUM_MOVEMENT_THRESHOLD = 5;  // pixels
    public static final long MINIMUM_LOG_INTERVAL = 500;     // milliseconds
}