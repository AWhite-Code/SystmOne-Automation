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
            
            // Register shutdown hook to catch crashes
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (instance != null && !instance.isKillSignalReceived()) {
                    // If we're shutting down but kill signal wasn't triggered, it's a crash
                    logger.error("Application crashed - finalizing logs");
                    LogFileNameCreator.finalizeLog(CRASHED_MARKER);
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
            killSignal.set(true);
            // Remove the stats reference here
            LogManager.finalizeLog("Killed by User", 0);  // Default to 0 if killed during initialization
        }
    }

    public void setCurrentStats(ProcessingStats stats) {
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