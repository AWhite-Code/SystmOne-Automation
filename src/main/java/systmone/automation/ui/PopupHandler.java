package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the detection and management of SystmOne popup dialogs.
 * Currently focused solely on detecting popups and pausing program execution.
 * Future enhancements will include popup dismissal and recovery strategies.
 */
public class PopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PopupHandler.class);
    
    // The main application window region
    private final Region mainWindow;
    
    // Pattern for detecting the "Question" popup title
    private final Pattern questionPopupPattern;
    
    /**
     * Creates a new PopupHandler for detecting SystmOne popups.
     * 
     * @param components The system components containing necessary references
     */
    public PopupHandler(Region mainWindow) {
        this.mainWindow = mainWindow;
        this.questionPopupPattern = new Pattern("question_popup_title.png")
            .similar(0.9f);
    }
    
    /**
     * Checks if a popup is currently present on screen.
     * This is a fast check that can be called frequently during operations.
     * 
     * @return true if a popup is detected, false otherwise
     */
    public boolean isPopupPresent() {
        try {
            // Look for the Question title bar in the window
            Match popupMatch = mainWindow.exists(questionPopupPattern);
            
            // If we found a match, verify it's in the center of the screen
            if (popupMatch != null) {
                logger.info("Detected popup at coordinates ({}, {})", 
                    popupMatch.x, popupMatch.y);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error checking for popup presence", e);
            return false;
        }
    }
}