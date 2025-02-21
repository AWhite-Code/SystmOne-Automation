package systmone.automation.killswitch;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import systmone.automation.state.ProcessingStats;
import systmone.automation.util.LogFileNameCreator;
import systmone.automation.util.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements a global keyboard killswitch for the SystmOne automation system.
 * Provides system-wide F5 key detection for graceful shutdown, regardless of window focus.
 * Uses JNativeHook for global keyboard event capture and implements the Singleton pattern
 * to ensure only one keyboard hook exists in the system.
 *
 * Key responsibilities:
 * - Global keyboard event monitoring
 * - F5 key detection across all windows
 * - Thread-safe kill signal management
 * - Proper resource cleanup on shutdown
 */
public class GlobalKillswitch implements NativeKeyListener {
    private static final Logger logger = LoggerFactory.getLogger(GlobalKillswitch.class);
    private final AtomicBoolean killSignal;
    private static GlobalKillswitch instance;

    private ProcessingStats currentStats;
    
    private static final String CRASHED_MARKER = "Crashed";
    private static final String KILLED_MARKER = "Killed by User";

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the kill signal state.
     */
    private GlobalKillswitch() {
        this.killSignal = new AtomicBoolean(false);
    }

    public AtomicBoolean getKillSignal() {
        return killSignal;
    }

    /**
     * Initializes the global keyboard listener and sets up the killswitch.
     * Should be called during application startup.
     * 
     * @return The GlobalKillswitch instance
     * @throws NativeHookException if keyboard hook registration fails
     */
    public static GlobalKillswitch initialize() throws NativeHookException {
        if (instance == null) {
            // Reduce JNativeHook logging noise
            java.util.logging.Logger nativeHookLogger = 
                java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
            nativeHookLogger.setLevel(java.util.logging.Level.WARNING);
            nativeHookLogger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            
            instance = new GlobalKillswitch();
            GlobalScreen.addNativeKeyListener(instance);
            
            // Register shutdown hook for crash handling
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (instance != null && !instance.isKillSignalReceived()) {
                    int processedCount = (instance.currentStats != null) ? 
                        instance.currentStats.getProcessedDocuments() : 0;
                    logger.error("Application crashed - finalizing logs");
                    LogManager.finalizeLog(CRASHED_MARKER, processedCount);
                }
            }));
            
            logger.info("Global killswitch initialized successfully (F5 to terminate)");
        }
        return instance;
    }

    /**
     * Handles key press events from the global keyboard hook.
     * Sets the kill signal when F5 is pressed.
     * 
     * @param event The keyboard event
     */
    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_F5) {
            logger.info("Kill signal received (F5 pressed)");
            
            // First set the kill signal
            killSignal.set(true);
            
            // Small delay to allow any in-progress document to complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore interruption
            }
            
            // Now get the final count
            int processedCount = 0;
            if (currentStats != null) {
                processedCount = currentStats.getProcessedDocuments();
                logger.info("Retrieved final document count for kill signal: {}", processedCount);
            }
            
            // Finalize log with actual document count and killed marker
            LogManager.finalizeLog(KILLED_MARKER, processedCount);
        }
    }

    public void setCurrentStats(ProcessingStats stats) {
        logger.info("Updating killswitch stats - Current processed count: {}", 
            (stats != null ? stats.getProcessedDocuments() : "null"));
        this.currentStats = stats;
    }

    /**
     * Required by NativeKeyListener interface but not used.
     */
    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        // Not used
    }

    /**
     * Required by NativeKeyListener interface but not used.
     */
    @Override
    public void nativeKeyTyped(NativeKeyEvent event) {
        // Not used
    }

    /**
     * Checks if the killswitch has been activated.
     * Thread-safe due to AtomicBoolean usage.
     * 
     * @return true if F5 was pressed, false otherwise
     */
    public boolean isKillSignalReceived() {
        return killSignal.get();
    }

    /**
     * Convenience method to check if kill signal is active.
     * Provides a cleaner API than accessing the AtomicBoolean directly.
     * 
     * @return true if kill signal has been received, false otherwise
     */
    public boolean isKilled() {
        return killSignal.get();
    }

    /**
     * Cleans up resources and unregisters the native hook.
     * Should be called during application shutdown.
     */
    public void cleanup() {
        try {
            logger.info("Cleaning up global killswitch");
            GlobalScreen.unregisterNativeHook();
            GlobalScreen.removeNativeKeyListener(this);
            instance = null;
            killSignal.set(false);
        } catch (NativeHookException e) {
            logger.warn("Failed to unregister native hook: {}", e.getMessage());
        }
    }
}