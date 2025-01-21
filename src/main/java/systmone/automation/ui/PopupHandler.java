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
    private final Pattern questionPopupPattern;
    
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
        
        // Initialize patterns with appropriate similarity thresholds
        this.questionPopupPattern = new Pattern("question_popup_title.png")
            .similar(ApplicationConfig.POPUP_SIMILARITY_THRESHOLD);
            
        this.wasPopupHandled = false;
        this.lastPopupTime = 0;
        this.popupCount = 0;
    }
    
    /**
     * Checks if a popup is currently present on screen.
     * Validates both the presence and position of the popup.
     * 
     * @return true if a popup is detected, false otherwise
     */
    public boolean isPopupPresent() {
        try {
            Match popupMatch = mainWindow.exists(questionPopupPattern);
            if (popupMatch != null) {
                // Verify popup is in expected screen position
                if (isPopupPositionValid(popupMatch)) {
                    // Track popup occurrence
                    lastPopupTime = System.currentTimeMillis();
                    popupCount++;
                    logger.info("Detected popup #{} at ({}, {})", 
                        popupCount, popupMatch.x, popupMatch.y);
                    return true;
                }
                logger.warn("Found popup title but position invalid: ({}, {})",
                    popupMatch.x, popupMatch.y);
            }
            return false;
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
    
    /**
     * Validates that a popup match is in the expected screen position.
     * SystmOne popups are always centered in the main window.
     */
    private boolean isPopupPositionValid(Match popupMatch) {
        // Calculate expected center position
        int expectedX = mainWindow.x + (mainWindow.w / 2);
        int expectedY = mainWindow.y + (mainWindow.h / 2);
        
        // Allow for some position variance
        int tolerance = ApplicationConfig.POPUP_POSITION_TOLERANCE;
        
        return Math.abs(popupMatch.x - expectedX) <= tolerance &&
               Math.abs(popupMatch.y - expectedY) <= tolerance;
    }
}