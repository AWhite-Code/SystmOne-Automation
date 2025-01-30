package systmone.automation.ui;

import java.util.concurrent.TimeUnit;

import org.sikuli.script.*;
import org.sikuli.basics.Settings;
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
     * Extracts and returns the total document count from the UI using OCR.
     * Searches for the text "Documents" and extracts the associated number.
     * 
     * @return The document count, or -1 if count cannot be determined
     */
    public int getDocumentCount() {
        try {
            Settings.OcrTextRead = true;
            Settings.OcrTextSearch = true;
            
            Region searchRegion = searchRegions.getDocumentCountRegion();
            Match textMatch = searchRegion.findText("Doc");  // Search for shorter text to be safer
            
            if (textMatch == null) {
                logger.error("Could not find 'Doc' text in expected region");
                return -1;
            }
            
            // Create our text capture region
            Region numberRegion = new Region(
                textMatch.x - 60,
                textMatch.y,
                80,
                textMatch.h
            );
            
            String rawText = numberRegion.text().trim();
            logger.debug("Raw OCR text found: '{}'", rawText);
            
            // More flexible pattern that handles partial matches
            // This will match numbers followed by any text starting with "Doc"
            java.util.regex.Pattern numberPattern = java.util.regex.Pattern.compile("(\\d+)\\s*Doc\\w*");
            java.util.regex.Matcher matcher = numberPattern.matcher(rawText);
            
            if (matcher.find()) {
                String numberStr = matcher.group(1);
                int detectedCount = Integer.parseInt(numberStr);
                
                // Handle border interference
                if (numberStr.startsWith("1")) {
                    String potentialRealCount = numberStr.substring(1);
                    int countWithoutBorder = Integer.parseInt(potentialRealCount);
                    
                    if (countWithoutBorder > 0 && detectedCount > 999) {
                        logger.debug("Detected border interference: {} -> {}", 
                                   detectedCount, countWithoutBorder);
                        return countWithoutBorder;
                    }
                }
                
                return detectedCount;
            }
            
            // If we didn't match our pattern, log the exact text we found to help with debugging
            logger.error("Could not extract number from text: '{}' - no matching pattern", rawText);
            return -1;
            
        } catch (Exception e) {
            logger.error("Error getting document count using OCR: " + e.getMessage(), e);
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
     * Opens the print menu through right-click context menu interaction.
     * Uses text recognition to find the Print option.
     * 
     * @param documentMatch The matched document UI element
     * @return true if print menu was successfully opened, false otherwise
     * @throws FindFailed if required UI elements cannot be found
     */
    private boolean openPrintMenu(Match documentMatch) throws FindFailed {
        try {
            if (popupHandler.isPopupPresent()) {
                logger.info("Clearing popup before print menu operation");
                popupHandler.dismissPopup(false);
                Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            }
    
            documentMatch.rightClick();
            Thread.sleep(ApplicationConfig.CONTEXT_MENU_DELAY_MS);
    
            if (popupHandler.isPopupPresent()) {
                logger.info("Popup appeared after right-click - handling");
                popupHandler.dismissPopup(false);
                return false;
            }
    
            // Enable OCR and get the menu region
            Settings.OcrTextRead = true;
            Settings.OcrTextSearch = true;
            Region menuRegion = searchRegions.getPrintMenuRegion();
    
            // Log what we're seeing for debugging purposes
            String visibleText = menuRegion.text();
            logger.info("Text detected in menu region: '{}'", visibleText);
    
            // Look for our anchor point - "Rotate Left"
            Match rotateLeftMatch = menuRegion.waitText("Rotate Right", ApplicationConfig.MENU_TIMEOUT);
            if (rotateLeftMatch == null) {
                logger.error("Could not find 'Rotate Left' anchor point");
                return false;
            }
    
            logger.info("Found 'Rotate Left' at position ({}, {})", rotateLeftMatch.x, rotateLeftMatch.y);
    
            // Create a new region just below "Rotate Left" where we know "Print" appears
            Region printRegion = new Region(
                rotateLeftMatch.x,          // Same x position as Rotate Left
                rotateLeftMatch.y + 45,     // Move down by one menu item height
                rotateLeftMatch.w,          // Same width as Rotate Left
                rotateLeftMatch.h           // Same height as Rotate Left
            );
    
            // Check for popup before clicking
            if (popupHandler.isPopupPresent()) {
                logger.info("Popup appeared before print menu selection - handling");
                popupHandler.dismissPopup(false);
                return false;
            }
    
            logger.info("Clicking calculated Print position at ({}, {})", printRegion.x, printRegion.y);
            // Click in the center of our calculated region
            printRegion.click();
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

