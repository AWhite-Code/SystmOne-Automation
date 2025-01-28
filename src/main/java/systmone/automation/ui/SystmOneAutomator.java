package systmone.automation.ui;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

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
    private final PrinterConfigurationHandler printerConfigHandler;  // Add this field

    // UI pattern matchers
    private final Pattern selectionBorderPattern;
    private final Pattern printMenuItemPattern;
    private final Pattern documentCountPattern;
    private final Pattern saveDialogPattern;

    /**
     * Creates a new SystmOne automation controller with standardized patterns.
     * 
     * @param similarity The similarity threshold for pattern matching
     * @throws FindFailed if the SystmOne window or required patterns cannot be initialized
     */
    public SystmOneAutomator(double patternSimilarity) throws FindFailed {
        this.systmOne = initializeApp();
        this.systmOneWindow = systmOne.window();
        
        // Initialize search regions before patterns
        this.searchRegions = new SearchRegions(systmOneWindow);
        
        // Initialize patterns with location-specific names
        this.selectionBorderPattern = initializePattern("selection_border", patternSimilarity);
        this.printMenuItemPattern = initializePattern("print_menu_item", patternSimilarity);
        this.documentCountPattern = initializePattern("document_count", patternSimilarity);
        this.saveDialogPattern = initializePattern("save_dialog_title", patternSimilarity);
        
        this.popupHandler = new PopupHandler(systmOneWindow);
        
        // Initialize printer configuration handler
        this.printerConfigHandler = new PrinterConfigurationHandler(
            systmOne,
            systmOneWindow,
            searchRegions,
            selectionBorderPattern
        );
        
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
     * Extracts and returns the total document count from the UI.
     * Searches for the document count pattern and parses the surrounding text.
     * 
     * @return The document count, or -1 if count cannot be determined
     */
    public int getDocumentCount() {
        try {
            // Search for document count only in the designated bottom-left region
            Match countMatch = searchRegions.getDocumentCountRegion().exists(documentCountPattern);
            if (countMatch == null) {
                logger.error("Document count pattern not found in expected region");
                return -1;
            }
    
            // Create a text capture region around the match
            // Note: We keep the expanded region for text capture to ensure we get the full number
            Region textRegion = new Region(
                countMatch.x - 50,
                countMatch.y,
                150,
                countMatch.h
            );
    
            String countText = textRegion.text();
            return extractNumberFromText(countText);
            
        } catch (Exception e) {
            logger.error("Error getting document count: " + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Extracts the first numeric value from a text string.
     * 
     * @param text Text containing numeric values
     * @return First number found in text, or -1 if no number is found
     */
    private int extractNumberFromText(String text) {
        return Arrays.stream(text.split("\\s+"))
            .filter(part -> part.matches("\\d+"))
            .findFirst()
            .map(Integer::parseInt)
            .orElse(-1);
    }

    /**
     * Initiates the document print operation based on current operation mode.
     * 
     * @param documentMatch The matched document UI element
     * @param savePath Target path for saving the document
     * @throws FindFailed if required UI elements cannot be found
     */
    public void printDocument(Match documentMatch, String savePath) throws FindFailed {
        if (ApplicationConfig.TEST_MODE) {
            handleTestModePrintOperation(documentMatch);
        } else {
            handleProductionPrintOperation(documentMatch, savePath);
        }
    }

    /**
     * Handles print operations in test mode with simulated popup interactions.
     * 
     * @param documentMatch The matched document UI element
     * @throws FindFailed if required UI elements cannot be found
     */
    private void handleTestModePrintOperation(Match documentMatch) throws FindFailed {
        logger.info("TEST MODE: Starting simulated print operation");
        
        // Open print dialog
        documentMatch.rightClick();
        Match printMenuItem = systmOneWindow.wait(printMenuItemPattern, 
            ApplicationConfig.MENU_TIMEOUT);
        printMenuItem.click();
        
        // Wait for and verify save dialog
        systmOneWindow.wait(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        // Provide test instructions
        logger.info("TEST MODE: Save dialog open - you can now:");
        logger.info("1. Manually close the save dialog");
        logger.info("2. Wait for 'retry' popup");
        logger.info("3. Watch the automation handle the retry");
        
        try {
            // Wait for manual interaction with reasonable timeout
            long startTime = System.currentTimeMillis();
            long timeout = 30000; // 30 seconds for testing
            boolean dialogClosed = false;
            
            // Monitor save dialog until it's closed or times out
            while (System.currentTimeMillis() - startTime < timeout) {
                if (systmOneWindow.exists(saveDialogPattern) == null) {
                    dialogClosed = true;
                    break;
                }
                Thread.sleep(500);
            }
            
            // Handle timeout case
            if (!dialogClosed) {
                logger.warn("TEST MODE: Timed out waiting for manual dialog close");
                throw new FindFailed("Test mode timeout - save dialog not closed");
            }
            
            // Allow time for retry popup to appear
            Thread.sleep(1000);
            
            // Trigger popup handling
            throw new FindFailed("TEST MODE: Triggering retry popup handler");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FindFailed("Test mode interrupted");
        }
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
     * Opens the print menu through right-click context menu interaction.
     * Handles the complete sequence of right-click and print option selection.
     * 
     * @param documentMatch The matched document UI element
     * @return true if print menu was successfully opened, false otherwise
     * @throws FindFailed if required UI elements cannot be found
     */
    private boolean openPrintMenu(Match documentMatch) throws FindFailed {
        try {
            // Check for and clear any existing popups
            if (popupHandler.isPopupPresent()) {
                logger.info("Clearing popup before print menu operation");
                popupHandler.dismissPopup(false);
                Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            }
    
            // Right-click the document to open context menu
            documentMatch.rightClick();
            Thread.sleep(ApplicationConfig.CONTEXT_MENU_DELAY_MS);
    
            // Check for popup after right-click
            if (popupHandler.isPopupPresent()) {
                logger.info("Popup appeared after right-click - handling");
                popupHandler.dismissPopup(false);
                return false;
            }
    
            // Look for print menu item only in the designated region
            Match printMenuItem = searchRegions.getPrintMenuRegion().wait(
                printMenuItemPattern, 
                ApplicationConfig.MENU_TIMEOUT
            );
            
            // Final popup check before clicking
            if (popupHandler.isPopupPresent()) {
                logger.info("Popup appeared before print menu selection - handling");
                popupHandler.dismissPopup(false);
                return false;
            }
    
            printMenuItem.click();
            return true;
    
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FindFailed("Print menu operation interrupted");
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

