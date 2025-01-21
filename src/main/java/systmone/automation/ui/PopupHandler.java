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
        this.questionPopupPattern = new Pattern("question_popup_title.png")
            .similar(ApplicationConfig.POPUP_SIMILARITY_THRESHOLD);
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
                // Since we know the popup appears at (707, 434), let's use that
                // to calculate a reasonable range for popup positions
                boolean isInValidRange = 
                    popupMatch.x >= mainWindow.x + 500 &&      // Not too far left
                    popupMatch.x <= mainWindow.x + 900 &&      // Not too far right
                    popupMatch.y >= mainWindow.y + 300 &&      // Not too high
                    popupMatch.y <= mainWindow.y + 500;        // Not too low
                
                if (isInValidRange) {
                    logger.debug("Popup detected at valid position ({}, {})", 
                        popupMatch.x, popupMatch.y);
                    return true;
                } else {
                    logger.warn("Found popup title but position invalid: ({}, {})", 
                        popupMatch.x, popupMatch.y);
                }
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
        // Get window dimensions
        int windowCenterX = mainWindow.x + (mainWindow.w / 2);
        int windowCenterY = mainWindow.y + (mainWindow.h / 2);
        
        // Calculate popup center (assuming popup title is near the top of the popup)
        int popupCenterX = popupMatch.x + (popupMatch.w / 2);
        
        // For Y position, we need to be more flexible since the title is at the top
        // The popup Y coordinate will be above the window center
        
        // Allow for more generous position tolerance
        int tolerance = ApplicationConfig.POPUP_POSITION_TOLERANCE;  // Maybe 150 pixels
        
        // Log the position calculations for debugging
        logger.debug("Window center: ({}, {})", windowCenterX, windowCenterY);
        logger.debug("Popup position: ({}, {})", popupMatch.x, popupMatch.y);
        logger.debug("Popup center X: {}", popupCenterX);
        
        // Check if popup is roughly centered horizontally and in the upper half of the window
        boolean isValid = Math.abs(popupCenterX - windowCenterX) <= tolerance &&
                         popupMatch.y > mainWindow.y &&  // Must be below top of window
                         popupMatch.y < windowCenterY;   // Must be above vertical center
        
        if (!isValid) {
            logger.debug("Position validation failed: X-offset={}, Y-position={}",
                Math.abs(popupCenterX - windowCenterX),
                popupMatch.y - mainWindow.y);
        }
        
        return isValid;
    }
}