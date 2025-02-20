package systmone.automation.ui;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import systmone.automation.config.ApplicationConfig;
import systmone.automation.killswitch.GlobalKillswitch;

/**
 * Provides core automation functionality for interacting with the SystmOne application.
 * This class manages window operations, UI element detection, and document processing
 * actions through Sikuli-based pattern matching.
 * 
 * Key Responsibilities:
 * - Manages SystmOne application window focus and state
 * - Handles document selection and navigation
 * - Processes document printing and saving operations
 * - Provides location-specific pattern matching
 * 
 * The automator supports both production and test operation modes, with specific
 * handling for each environment. Pattern matching is configured per location to
 * accommodate UI variations between different sites.
 * 
 * Dependencies:
 * - Requires an active SystmOne application instance
 * - Uses Sikuli for UI automation and pattern matching
 * - Relies on ApplicationConfig for environment settings
 * - Integrates with PopupHandler for dialog management
 */
public class SystmOneAutomator {
    private static final Logger logger = LoggerFactory.getLogger(SystmOneAutomator.class);

    // Core application components
    private final App systmOne;
    private final Region systmOneWindow;
    private final PopupHandler popupHandler;
    private final SearchRegions searchRegions;
    private final PrinterConfigurationHandler printerConfigHandler;
    private final UiStateHandler uiStateHandler;

    // UI pattern matchers
    private final Pattern selectionBorderPattern;
    private final Pattern saveDialogPattern;
    private final Pattern popupPattern;

    /**
     * Creates a new SystmOne automation controller with standardized patterns.
     * 
     * @param similarity The similarity threshold for pattern matching
     * @throws FindFailed if the SystmOne window or required patterns cannot be initialized
     */
    public SystmOneAutomator(double patternSimilarity, GlobalKillswitch killSwitch) throws FindFailed {
        logger.debug("SystmOneAutomator constructor called with killSwitch: {}", killSwitch);
        
        if (killSwitch == null) {
            logger.error("Null killSwitch provided to SystmOneAutomator constructor");
            throw new IllegalArgumentException("KillSwitch must be provided");
        }
        
        this.systmOne = initializeApp();
        this.systmOneWindow = systmOne.window();
        
        // Initialize search regions before patterns
        this.searchRegions = new SearchRegions(systmOneWindow);
        
        // Initialize all patterns with location-specific names
        this.selectionBorderPattern = initializePattern("selection_border", patternSimilarity);
        this.saveDialogPattern = initializePattern("save_dialog_title", patternSimilarity);
        this.popupPattern = initializePattern("question_popup_title", patternSimilarity);
        
        this.popupHandler = new PopupHandler(systmOneWindow);
    
        // Initialize UI state handler with required components
        this.uiStateHandler = new UiStateHandler(
            searchRegions.getSelectionBorderRegion(),
            systmOneWindow,
            selectionBorderPattern,
            popupHandler
        );
            
        logger.debug("About to create PrinterConfigurationHandler with killSwitch: {}", killSwitch);
        
        // Initialize printer configuration handler with killswitch and popup handling components
        this.printerConfigHandler = new PrinterConfigurationHandler(
            systmOne,
            systmOneWindow,
            searchRegions,
            selectionBorderPattern,
            popupPattern,
            killSwitch  // This seems correct
        );
        
        logger.debug("PrinterConfigurationHandler created successfully");
    
        if (systmOneWindow == null) {
            throw new FindFailed("Failed to initialize SystmOne window");
        }
    }
    
    /**
     * Initializes the SystmOne application instance and verifies its window.
     * 
     * @return The initialized App instance
     * @throws FindFailed if the application window cannot be found
     */
    private App initializeApp() throws FindFailed {
        App app = new App(ApplicationConfig.APP_TITLE);
        if (app.window() == null) {
            throw new FindFailed("SystmOne window not found");
        }
        return app;
    }

    /**
     * Creates a pattern with the given name and similarity threshold.
     * 
     * @param baseName Base name of the pattern image file
     * @param similarity Pattern matching similarity threshold
     * @return Configured Pattern instance for UI matching
     */
    private Pattern initializePattern(String baseName, double similarity) {
        return new Pattern(baseName + ".png").similar(similarity);
    }


    /**
     * Brings the SystmOne window into focus and waits for it to stabilize.
     * 
     * @throws InterruptedException if the focus operation is interrupted
     */
    public void focus() throws InterruptedException {
        systmOne.focus();
        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
    }

    /**
     * Determines the total number of documents by tracking UI movement.
     * 
     * @return The document count, or -1 if count cannot be determined
     */
    public int getDocumentCount() {
        try {
            return uiStateHandler.determineDocumentCount();  // Use the instance
        } catch (Exception e) {
            logger.error("Error getting document count: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Initiates the document print operation based on current operation mode.
     * 
     * @param documentMatch The matched document UI element
     * @param savePath Target path for saving the document
     * @throws FindFailed if required UI elements cannot be found
     */
    public void printDocument(Match documentMatch, String savePath) throws FindFailed {
            handleProductionPrintOperation(documentMatch, savePath);
        }

    /**
     * Handles print operations in production mode with actual save operations.
     * 
     * @param documentMatch The matched document UI element
     * @param savePath Target path for saving the document
     * @throws FindFailed if required UI elements cannot be found
     */
    private void handleProductionPrintOperation(Match documentMatch, String savePath) throws FindFailed {
        int attempts = 0;
    
        while (attempts < ApplicationConfig.MAX_PRINT_MENU_ATTEMPTS) {
            attempts++;
            logger.info("Print menu attempt {} of {}", attempts, ApplicationConfig.MAX_PRINT_MENU_ATTEMPTS);
    
            try {
                // Use selection border region to verify document selection
                Match currentDocument = searchRegions.getSelectionBorderRegion().exists(selectionBorderPattern);
                if (currentDocument == null) {
                    throw new FindFailed("Lost document selection during print operation");
                }
    
                // Initialize print operation using our region-aware openPrintMenu
                if (!openPrintMenu(currentDocument)) {
                    logger.warn("Failed to open print menu - retrying");
                    continue;
                }
    
                // For save dialog, we still use full window since it can appear anywhere
                systmOneWindow.wait(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
                
                // Perform save operations (unchanged as these are keyboard operations)
                systmOneWindow.type("a", KeyModifier.CTRL);
                systmOneWindow.type("v", KeyModifier.CTRL);
                systmOneWindow.type(Key.ENTER);
                
                // Wait for save dialog to close (still using full window)
                systmOneWindow.waitVanish(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
                
                logger.info("Saved document to: {}", savePath);
                return;  // Success!
    
            } catch (FindFailed e) {
                // Popup handling remains unchanged as it's managed by PopupHandler
                if (popupHandler.isPopupPresent()) {
                    logger.info("Popup detected during print operation - handling full cleanup sequence");
                    
                    try {
                        popupHandler.dismissPopup(false);
                        Thread.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
                        
                        logger.info("Closing stuck print menu");
                        systmOneWindow.type(Key.ESC);
                        Thread.sleep(ApplicationConfig.MENU_CLEANUP_DELAY_MS);
                        
                        logger.info("Dismissing print failed notification");
                        systmOneWindow.type(Key.ESC);
                        Thread.sleep(ApplicationConfig.POST_CLEANUP_DELAY_MS);
                        
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new FindFailed("Print operation interrupted during cleanup");
                    }
                    
                    continue;
                }
                
                if (attempts >= ApplicationConfig.MAX_PRINT_MENU_ATTEMPTS) {
                    throw new FindFailed("Print menu operation failed after " + 
                        ApplicationConfig.MAX_PRINT_MENU_ATTEMPTS + " attempts: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Opens the print menu using right-click and keyboard navigation.
     * Checks for popups before proceeding and uses keyboard shortcuts instead of OCR.
     * 
     * @param documentMatch The Match object representing the document location
     * @return boolean indicating if the print menu was successfully opened
     * @throws FindFailed if the document interaction fails
     */
    private boolean openPrintMenu(Match documentMatch) throws FindFailed {
        try {
            // Check for and dismiss any existing popups
            if (popupHandler.isPopupPresent()) {
                logger.info("Clearing popup before print menu operation");
                popupHandler.dismissPopup(false);
                TimeUnit.MILLISECONDS.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            }

            // Perform right-click on document
            logger.info("Right-clicking at location: ({}, {})", documentMatch.x, documentMatch.y);
            documentMatch.rightClick();
            
            // Wait for context menu to appear
            TimeUnit.MILLISECONDS.sleep(100);
            
            // Create screen object for keyboard input
            Screen screen = new Screen();
            
            // Send up arrow to highlight Print option
            screen.type(Key.UP);
            
            // Short delay to ensure menu item is highlighted
            TimeUnit.MILLISECONDS.sleep(100);
            
            // Press enter to select Print
            screen.type(Key.ENTER);
            
            logger.info("Successfully opened print menu using keyboard navigation");
            return true;

        } catch (Exception e) {
            logger.error("Failed to open print menu: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Moves the document selection down one position.
     */
    public void navigateDown() {
        systmOneWindow.type(Key.DOWN);
    }

    /**
     * Waits for a UI element to appear and stabilize.
     * 
     * @param timeout Maximum time to wait in seconds
     * @return Match object for the stable element
     * @throws FindFailed if the element is not found within timeout
     */
    public Match waitForStableElement(int timeout) throws FindFailed {
        return searchRegions.getSelectionBorderRegion().wait(selectionBorderPattern, timeout);
    }

    public boolean configurePDFPrinter() throws FindFailed {
        return printerConfigHandler.configurePDFPrinter();
    }

    // Getter methods
    public PopupHandler getPopupHandler() {
        return popupHandler;
    }

    public Region getWindow() {
        return systmOneWindow;
    }

    public Pattern getSelectionBorderPattern() {
        return selectionBorderPattern;
    }

    public Pattern getSaveDialogPattern() {
        return saveDialogPattern;
    }
}

