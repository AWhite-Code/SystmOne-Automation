package systmone.automation.ui;

// Java standard imports
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Point;

// Sikuli imports
import org.sikuli.script.Region;
import org.sikuli.script.Key;
import org.sikuli.script.Match;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Application imports
import systmone.automation.core.SystemComponents;
import systmone.automation.config.ApplicationConfig;

public class PopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PopupHandler.class);
    
    private final SystmOneAutomator automator;
    private final UiStateHandler uiStateHandler;
    private final Robot robot;
    private final Region mainWindow;
    
    // Screen center calculation
    private final int screenCenterX;
    private final int screenCenterY;
    
    // Region to monitor for popups (centered box)
    private final Region popupRegion;
    
    public PopupHandler(SystemComponents components) {
        this.automator = components.getAutomator();
        this.uiStateHandler = components.getUiHandler();
        this.mainWindow = automator.getWindow();
        
        // Calculate screen center for popup detection
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.screenCenterX = screenSize.width / 2;
        this.screenCenterY = screenSize.height / 2;
        
        // Create a region around screen center where popups appear
        // Adjust these dimensions based on actual popup size
        this.popupRegion = new Region(
            screenCenterX - 200,  // Approximate popup width/2
            screenCenterY - 100,  // Approximate popup height/2
            400,  // Popup width
            200   // Popup height
        );
        
        try {
            this.robot = new Robot();
        } catch (Exception e) {
            logger.error("Failed to initialize Robot for popup detection", e);
            throw new RuntimeException("Could not initialize popup detection", e);
        }
    }
    
    /**
     * Checks for popup presence using screen center monitoring
     */
    public boolean isPopupPresent() {
        try {
            // Take a small sample of pixels in the popup region
            // We'll look for the popup's background color
            Color centerColor = robot.getPixelColor(screenCenterX, screenCenterY);
            
            // If the color matches expected popup background
            // (you'll need to determine the actual color)
            return isPopupColor(centerColor);
            
        } catch (Exception e) {
            logger.warn("Error checking for popup", e);
            return false;
        }
    }
    
    /**
     * Handles recovery after a popup is dismissed
     */
    public boolean recoverFromPopup() {
        // First, verify popup is actually gone
        if (isPopupPresent()) {
            return false;
        }
        
        try {
            // 1. Re-verify our document selection
            Match documentMatch = uiHandler.waitForStableElement(
                automator.getSelectionBorderPattern(),
                ApplicationConfig.DIALOG_TIMEOUT
            );
            
            if (documentMatch == null) {
                logger.error("Could not recover document selection after popup");
                return false;
            }
            
            // 2. If we were tracking the scrollbar, we need to reset
            if (uiHandler.isTrackingStarted()) {  // You'll need to add this method
                if (!uiHandler.startDocumentTracking()) {
                    logger.warn("Could not restart scrollbar tracking after popup");
                    // We can continue, just with degraded verification
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error during popup recovery", e);
            return false;
        }
    }
    
    /**
     * Handles a detected popup
     */
    public boolean handlePopup() {
        try {
            // Since we know it's always in the center, we can try to focus it
            robot.mouseMove(screenCenterX, screenCenterY);
            
            // Try escape key as you mentioned it works
            mainWindow.type(Key.ESC);
            
            // Wait a moment for popup to clear
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            
            // Verify popup is gone
            if (isPopupPresent()) {
                return false;
            }
            
            // Try to recover our previous state
            return recoverFromPopup();
            
        } catch (Exception e) {
            logger.error("Error handling popup", e);
            return false;
        }
    }
}