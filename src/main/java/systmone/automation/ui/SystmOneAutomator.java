package systmone.automation.ui;


import java.util.Arrays;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

import java.util.concurrent.TimeUnit;

/**
 * Handles core automation interactions with the SystmOne application.
 * Responsible for window management, pattern matching, and basic UI operations.
 */
public class SystmOneAutomator {
    private static final Logger logger = LoggerFactory.getLogger(SystmOneAutomator.class);
    private final App systmOne;
    private final Region systmOneWindow;
    private final ApplicationConfig.Location location;
    private final Pattern selectionBorderPattern;
    private final Pattern printMenuItemPattern;
    private final Pattern documentCountPattern;
    private final Pattern saveDialogPattern;
    private final PopupHandler popupHandler;

    public SystmOneAutomator(ApplicationConfig.Location location, double patternSimilarity) throws FindFailed {
        this.location = location;
        this.systmOne = initializeApp();
        this.systmOneWindow = systmOne.window();
        this.selectionBorderPattern = initializePattern("selection_border", patternSimilarity);
        this.printMenuItemPattern = initializePattern("print_menu_item", patternSimilarity);
        this.documentCountPattern = initializePattern("document_count", patternSimilarity);
        this.saveDialogPattern = initializePattern("save_dialog_title", patternSimilarity);
        this.popupHandler = new PopupHandler(systmOneWindow);
        
        if (systmOneWindow == null) {
            throw new FindFailed("Failed to initialize SystmOne window");
        }
    }

    private App initializeApp() throws FindFailed {
        App app = new App(ApplicationConfig.APP_TITLE);
        if (app.window() == null) {
            throw new FindFailed("SystmOne window not found");
        }
        return app;
    }

    private Pattern initializePattern(String baseName, double similarity) {
        String locationSuffix = "_" + location.name().toLowerCase();
        return new Pattern(baseName + locationSuffix + ".png").similar(similarity);
    }

    public void focus() throws InterruptedException {
        systmOne.focus();
        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
    }

    public int getDocumentCount() {
        try {
            Match countMatch = systmOneWindow.exists(documentCountPattern);
            if (countMatch == null) {
                logger.error("Document count pattern not found");
                return -1;
            }

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

    private int extractNumberFromText(String text) {
        return Arrays.stream(text.split("\\s+"))
            .filter(part -> part.matches("\\d+"))
            .findFirst()
            .map(Integer::parseInt)
            .orElse(-1);
    }

    public void printDocument(Match documentMatch, String savePath) throws FindFailed {
        if (ApplicationConfig.TEST_MODE) {
            handleTestModePrintOperation(documentMatch);
        } else {
            handleProductionPrintOperation(documentMatch, savePath);
        }
    }

    /**
     * Handles the print operation in test mode. This method simulates a save dialog
     * that can be manually closed to trigger popup handling testing.
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
     * Handles the print operation in production mode. This method performs the actual
     * save operation with clipboard operations and keyboard input.
     */
    private void handleProductionPrintOperation(Match documentMatch, String savePath) throws FindFailed {
        // Start print operation
        documentMatch.rightClick();
        
        // Wait for and click print menu item
        Match printMenuItem = systmOneWindow.wait(printMenuItemPattern, 
            ApplicationConfig.MENU_TIMEOUT);
        printMenuItem.click();
        
        // Wait for save dialog
        systmOneWindow.wait(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        // Perform save operations with proper keyboard modifiers
        systmOneWindow.type("a", KeyModifier.CTRL);  // Select all existing text
        systmOneWindow.type("v", KeyModifier.CTRL);  // Paste new path
        systmOneWindow.type(Key.ENTER);              // Confirm save
        
        // Wait for save dialog to close
        systmOneWindow.waitVanish(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        logger.info("Saved document to: {}", savePath);
    }

    public void navigateDown() {
        systmOneWindow.type(Key.DOWN);
    }

    public Match waitForStableElement(int timeout) throws FindFailed {
        return systmOneWindow.wait(selectionBorderPattern, timeout);
    }
    
    // Getters


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

    public ApplicationConfig.Location getLocation() {
        return location;
    }
}