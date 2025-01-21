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
            // Sample multiple points in the popup region for more reliable detection
            Point[] samplePoints = {
                new Point(screenCenterX, screenCenterY),
                new Point(screenCenterX, screenCenterY - 50),  // Top of expected popup
                new Point(screenCenterX, screenCenterY + 50)   // Bottom of expected popup
            };
    
            // Check multiple points to confirm popup presence
            for (Point point : samplePoints) {
                if (point.x >= popupRegion.x && 
                    point.x <= popupRegion.x + popupRegion.w &&
                    point.y >= popupRegion.y && 
                    point.y <= popupRegion.y + popupRegion.h) {
                    
                    Color pointColor = robot.getPixelColor(point.x, point.y);
                    if (isPopupColor(pointColor)) {
                        return true;
                    }
                }
            }
            
            return false;
    
        } catch (Exception e) {
            logger.warn("Error checking for popup", e);
            return false;
        }
    }

    /**
     * Determines if a given color matches the expected popup background color.
     * Uses the same color tolerance approach as UiStateHandler for consistency.
     */
    private boolean isPopupColor(Color color) {
        // Define expected popup background color (you'll need to adjust these values)
        final Color POPUP_BACKGROUND = new Color(240, 240, 240);  // Light gray, typical for Windows dialogs
        
        return Math.abs(color.getRed() - POPUP_BACKGROUND.getRed()) <= ApplicationConfig.COLOR_TOLERANCE &&
            Math.abs(color.getGreen() - POPUP_BACKGROUND.getGreen()) <= ApplicationConfig.COLOR_TOLERANCE &&
            Math.abs(color.getBlue() - POPUP_BACKGROUND.getBlue()) <= ApplicationConfig.COLOR_TOLERANCE;
    }
    
    /**
     * Handles recovery after a popup is dismissed
     */
    private boolean recoverFromPopup() {
        // First, verify popup is actually gone
        if (isPopupPresent()) {
            return false;
        }
    
        try {
            // 1. Re-verify our document selection using our stored uiStateHandler
            Match documentMatch = this.uiStateHandler.waitForStableElement(
                this.automator.getSelectionBorderPattern(),
                ApplicationConfig.DIALOG_TIMEOUT
            );
    
            if (documentMatch == null) {
                logger.error("Could not recover document selection after popup");
                return false;
            }
    
            // 2. Check scrollbar tracking state using our stored uiStateHandler
            if (this.uiStateHandler.isTrackingActive()) {  // Changed method name to match existing code
                if (!this.uiStateHandler.startDocumentTracking()) {
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