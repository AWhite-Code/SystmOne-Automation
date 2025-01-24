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
        this.questionMarkPattern = new Pattern("popup_question_mark.png").similar(0.8);
        
        // Calculate middle quarter of screen
        int screenWidth = mainWindow.w;
        int screenHeight = mainWindow.h;
        
        int quarterWidth = screenWidth / 4;
        int quarterHeight = screenHeight / 4;
        
        // Create region that's the middle quarter of the screen
        this.iconRegion = new Region(
            mainWindow.x + quarterWidth,     // Start 1/4 in from left
            mainWindow.y + quarterHeight,    // Start 1/4 down from top
            quarterWidth * 2,                // Middle half of width
            quarterHeight * 2                // Middle half of height
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
            long startTime = System.currentTimeMillis();
            // Use find() in the region to explicitly limit the search area
            iconRegion.find(questionMarkPattern);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Popup check took: {} ms", duration);
            return true;
            
        } catch (FindFailed e) {
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
}