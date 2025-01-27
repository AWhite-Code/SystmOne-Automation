package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

/**
 * Manages the detection and handling of SystmOne popup dialogs during document processing.
 * This class provides mechanisms for detecting, validating, and dismissing system popups
 * that may appear during automation tasks.
 * 
 * Key Responsibilities:
 * - Detects popup dialogs within a specified screen region
 * - Manages popup dismissal through keyboard input
 * - Tracks popup handling state for recovery scenarios
 * - Provides popup history for operation coordination
 * 
 * The handler focuses on the middle quarter of the screen for optimal
 * popup detection while avoiding false positives from other UI elements.
 * 
 * Dependencies:
 * - Requires access to the main application window
 * - Uses Sikuli for image pattern matching
 * - Relies on ApplicationConfig for timing parameters
 */
public class PopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PopupHandler.class);
    
    // UI Components and patterns
    private final Region mainWindow;
    private final Pattern questionMarkPattern;
    private final Region iconRegion;
    
    // Popup state tracking
    private boolean wasPopupHandled;
    private long lastPopupTime;
    private int popupCount;
    
    /**
     * Creates a new PopupHandler for managing SystmOne popup dialogs.
     * Initializes the detection region and pattern matching parameters.
     * The detection region is set to the middle quarter of the screen to
     * optimize popup detection accuracy.
     * 
     * @param mainWindow The main application window region to monitor
     * @throws IllegalArgumentException if mainWindow is null
     */
    public PopupHandler(Region mainWindow) {
        if (mainWindow == null) {
            throw new IllegalArgumentException("Main window region cannot be null");
        }
        
        this.mainWindow = mainWindow;
        this.questionMarkPattern = new Pattern("popup_question_mark.png").similar(0.8);
        
        // Define popup detection region as middle quarter of screen
        int screenWidth = mainWindow.w;
        int screenHeight = mainWindow.h;
        int quarterWidth = screenWidth / 4;
        int quarterHeight = screenHeight / 4;
        
        this.iconRegion = new Region(
            mainWindow.x + quarterWidth,     // Start 1/4 in from left
            mainWindow.y + quarterHeight,    // Start 1/4 down from top
            quarterWidth * 2,                // Middle half of width
            quarterHeight * 2                // Middle half of height
        );
    }
    
    /**
     * Checks for the presence of a popup dialog within the designated screen region.
     * Uses pattern matching to identify the popup question mark icon.
     * Performance metrics are logged for monitoring pattern matching efficiency.
     * 
     * @return true if a popup is detected, false otherwise
     */
    public boolean isPopupPresent() {
        try {
            long startTime = System.currentTimeMillis();
            iconRegion.find(questionMarkPattern);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Popup check completed in {} ms", duration);
            return true;
            
        } catch (FindFailed e) {
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during popup detection", e);
            return false;
        }
    }
    
    /**
     * Attempts to dismiss a detected popup using keyboard input.
     * Verifies popup presence before attempting dismissal and confirms
     * successful removal afterward.
     * 
     * @param accept true to accept the popup (ENTER key), false to cancel (ESC key)
     * @return true if popup was successfully dismissed, false if dismissal failed
     *         or no popup was present
     */
    public boolean dismissPopup(boolean accept) {
        try {
            if (!isPopupPresent()) {
                logger.warn("Dismissal attempted with no popup present");
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
            
            Thread.sleep(ApplicationConfig.POPUP_DISMISS_DELAY_MS);
            
            // Verify dismissal success
            if (!isPopupPresent()) {
                wasPopupHandled = true;
                lastPopupTime = System.currentTimeMillis();
                popupCount++;
                logger.info("Popup #{} successfully dismissed.", 
                    popupCount);
                return true;
            }
            
            logger.error("Popup persisted after dismissal attempt");
            return false;
            
        } catch (Exception e) {
            logger.error("Error during popup dismissal operation", e);
            return false;
        }
    }
    
    /**
     * Checks if a popup was handled within a specified time window.
     * Used to coordinate operations that may be affected by recent popup activity.
     * 
     * @param withinMs Maximum time window in milliseconds to consider
     * @return true if a popup was handled within the specified window
     */
    public boolean wasRecentlyHandled(long withinMs) {
        if (!wasPopupHandled) {
            return false;
        }
        return (System.currentTimeMillis() - lastPopupTime) < withinMs;
    }
    
    /**
     * Resets all popup handling state data to initial values.
     * Should be called when starting new operations or switching documents
     * to ensure clean state tracking.
     */
    public void resetState() {
        wasPopupHandled = false;
        lastPopupTime = 0;
        popupCount = 0;
        logger.debug("Popup handler state reset completed");
    }
}