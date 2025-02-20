package systmone.automation.state;

import org.sikuli.script.App;
import org.sikuli.script.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

import java.util.concurrent.TimeUnit;

/**
 * Manages window state and focus operations with proper error handling.
 * Provides methods for window focus verification and recovery.
 */
public class WindowStateManager {
    private static final Logger logger = LoggerFactory.getLogger(WindowStateManager.class);

    private final App mainApp;
    private App currentFocusedWindow;
    
    public WindowStateManager(App mainApp) {
        try {
            if (mainApp == null) {
                throw new IllegalArgumentException("Main app cannot be null");
            }
            if (mainApp.window() == null) {
                throw new IllegalArgumentException("Main app window not accessible");
            }
            this.mainApp = mainApp;
            logger.debug("WindowStateManager initialized with app: {}", mainApp);
        } catch (Exception e) {
            logger.error("Failed to initialize WindowStateManager: ", e);
            throw e;
        }
    }

    /**
     * Attempts to focus a window and verify its state.
     * 
     * @param windowTitle Title of the window to focus
     * @return true if window was successfully focused
     */
    public boolean focusAndVerifyWindow(String windowTitle) {
        try {
            App window = new App(windowTitle);
            if (!verifyWindowExists(window)) {
                logger.error("Window '{}' does not exist", windowTitle);
                return false;
            }

            window.focus();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
            
            if (verifyWindowFocused(window)) {
                currentFocusedWindow = window;
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error focusing window '{}': {}", windowTitle, e.getMessage());
            return false;
        }
    }

    /**
     * Returns to the main application window.
     * 
     * @return true if main window focus was restored
     */
    public boolean returnToMainWindow() {
        try {
            mainApp.focus();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
            currentFocusedWindow = mainApp;
            return true;
        } catch (Exception e) {
            logger.error("Failed to return to main window: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies if a window exists and is accessible.
     */
    private boolean verifyWindowExists(App window) {
        try {
            return window != null && window.window() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies if a window is currently focused.
     */
    private boolean verifyWindowFocused(App window) {
        try {
            // Additional focus verification could be added here
            return window.window() != null && window.window().isValid();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the currently focused window.
     */
    public App getCurrentFocusedWindow() {
        return currentFocusedWindow;
    }

    /**
     * Cleans up window state.
     */
    public void cleanup() {
        if (currentFocusedWindow != null && currentFocusedWindow != mainApp) {
            try {
                currentFocusedWindow.window().type(Key.ESC);
                TimeUnit.MILLISECONDS.sleep(ApplicationConfig.POST_CLEANUP_DELAY_MS);
            } catch (Exception e) {
                logger.warn("Error during window cleanup: {}", e.getMessage());
            }
        }
        returnToMainWindow();
    }
}