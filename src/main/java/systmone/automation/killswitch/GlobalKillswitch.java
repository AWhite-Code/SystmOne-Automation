package systmone.automation.killswitch;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements a global keyboard killswitch for the SystmOne automation system.
 * Provides system-wide F10 key detection for graceful shutdown, regardless of window focus.
 * Uses JNativeHook for global keyboard event capture and implements the Singleton pattern
 * to ensure only one keyboard hook exists in the system.
 *
 * Key responsibilities:
 * - Global keyboard event monitoring
 * - F10 key detection across all windows
 * - Thread-safe kill signal management
 * - Proper resource cleanup on shutdown
 */
public class GlobalKillswitch implements NativeKeyListener {
    private static final Logger logger = LoggerFactory.getLogger(GlobalKillswitch.class);
    private final AtomicBoolean killSignal;
    private static GlobalKillswitch instance;

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
            logger.info("Initializing global killswitch");
            
            // Reduce JNativeHook logging noise
            java.util.logging.Logger nativeHookLogger = 
                java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
            nativeHookLogger.setLevel(java.util.logging.Level.WARNING);
            nativeHookLogger.setUseParentHandlers(false);

            // Register native hook
            GlobalScreen.registerNativeHook();
            
            instance = new GlobalKillswitch();
            GlobalScreen.addNativeKeyListener(instance);
            
            logger.info("Global killswitch initialized successfully (F10 to terminate)");
        }
        return instance;
    }

    /**
     * Handles key press events from the global keyboard hook.
     * Sets the kill signal when F10 is pressed.
     * 
     * @param event The keyboard event
     */
    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_F10) {
            logger.info("Kill signal received (F10 pressed)");
            killSignal.set(true);
        }
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
     * @return true if F10 was pressed, false otherwise
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
            GlobalScreen.removeNativeKeyListener(this);  // Add this line
            instance = null;  // Add this line to reset the singleton
            killSignal.set(false);  // Reset the signal
        } catch (NativeHookException e) {
            logger.warn("Failed to unregister native hook: {}", e.getMessage());
        }
    }
}