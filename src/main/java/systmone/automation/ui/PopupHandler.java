package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

/**
 * Manages the detection and handling of SystmOne popup dialogs.
 * This class provides comprehensive popup management including detection,
 * dismissal, and recovery strategies for different UI states.
 */
public class PopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PopupHandler.class);
    
    private final Region mainWindow;
    private final Pattern questionMarkPattern;
    private final Region iconRegion;
    
    // Track popup state for recovery
    private boolean wasPopupHandled;
    private long lastPopupTime;
    private int popupCount;
    
    /**
     * Creates a new PopupHandler for managing SystmOne popups.
     * Initializes all necessary patterns for popup interaction.
     * 
     * @param mainWindow The main application window region
     */
    public PopupHandler(Region mainWindow) {
        this.mainWindow = mainWindow;
        this.questionMarkPattern = new Pattern("popup_question_mark.png").similar(0.8); // Might need to adjust similarity
        
        // Initialize the focused region where we'll look for the icon
        this.iconRegion = new Region(
            mainWindow.x + 500,    // Left boundary
            mainWindow.y + 300,    // Top boundary
            50,                    // Width - just enough for the icon
            50                     // Height - just enough for the icon
        );
    }
    
    /**
     * Checks if a popup is currently present on screen.
     * Validates both the presence and position of the popup.
     * 
     * @return true if a popup is detected, false otherwise
     */
    public boolean isPopupPresent() {
        try {
            // Define small region just around where the question mark icon appears
            Region iconRegion = new Region(
                mainWindow.x + 500,    // Left boundary
                mainWindow.y + 300,    // Top boundary
                50,                    // Width - just enough for the icon
                50                     // Height - just enough for the icon
            );
            
            // Look for the green question mark icon in this small region
            Match iconMatch = iconRegion.exists(questionMarkPattern);
            return iconMatch != null;
            
        } catch (Exception e) {
            logger.error("Error checking for popup presence", e);
            return false;
        }
    }
    
    /**
     * Dismisses a popup using keyboard input.
     * @param accept If true, uses Enter key to accept. If false, uses ESC to cancel.
     * @return true if popup was successfully dismissed
     */
    public boolean dismissPopup(boolean accept) {
        try {
            if (!isPopupPresent()) {
                logger.warn("Attempted to dismiss non-existent popup");
                return false;
            }
            
            // Use appropriate key based on desired action
            if (accept) {
                logger.info("Sending ENTER to accept popup");
                mainWindow.type(Key.ENTER);
            } else {
                logger.info("Sending ESC to cancel popup");
                mainWindow.type(Key.ESC);
            }
            
            // Allow time for popup to dismiss
            Thread.sleep(ApplicationConfig.POPUP_DISMISS_DELAY_MS);
            
            // Verify popup is gone
            if (!isPopupPresent()) {
                wasPopupHandled = true;
                logger.info("Successfully dismissed popup #{} with {}", 
                    popupCount, accept ? "ENTER" : "ESC");
                return true;
            }
            
            logger.error("Popup still present after dismissal attempt");
            return false;
            
        } catch (Exception e) {
            logger.error("Error during popup dismissal", e);
            return false;
        }
    }
    
    /**
     * Checks if the last handled popup was recent enough to affect
     * the current operation.
     * 
     * @param withinMs Time window in milliseconds
     * @return true if a popup was handled within the specified window
     */
    public boolean wasRecentlyHandled(long withinMs) {
        if (!wasPopupHandled) return false;
        return (System.currentTimeMillis() - lastPopupTime) < withinMs;
    }
    
    /**
     * Resets the popup handling state. Should be called at the start
     * of major operations or when switching documents.
     */
    public void resetState() {
        wasPopupHandled = false;
        lastPopupTime = 0;
        popupCount = 0;
        logger.debug("Reset popup handler state");
    }
}