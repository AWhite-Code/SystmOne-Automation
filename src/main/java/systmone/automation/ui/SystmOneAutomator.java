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
        PopupHandler popupHandler = new PopupHandler(systmOneWindow);
        
        // Right-click to open context menu
        documentMatch.rightClick();
        
        // Wait for print menu item, handling any popups
        Match printMenuItem = null;
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < ApplicationConfig.DIALOG_TIMEOUT * 1000) {
            // Check for and handle popups before looking for menu
            if (popupHandler.isPopupPresent()) {
                logger.info("Handling popup during print menu operation");
                popupHandler.dismissPopup(false);  // Use ESC to dismiss
                // Re-attempt right-click as menu may have closed
                documentMatch.rightClick();
            }
    
            printMenuItem = systmOneWindow.exists(printMenuItemPattern);
            if (printMenuItem != null) break;
            
            try {
                Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FindFailed("Print operation interrupted");
            }
        }
        
        if (printMenuItem == null) {
            throw new FindFailed("Print menu item not found after popup handling");
        }
        
        // Click print menu item
        printMenuItem.click();
        
        // Wait for and handle save dialog
        Match saveDialog = systmOneWindow.wait(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        // Handle path copying and save operation
        systmOneWindow.type("a", KeyModifier.CTRL);
        systmOneWindow.type("v", KeyModifier.CTRL);
        systmOneWindow.type(Key.ENTER);
        
        // Wait for save dialog to close, handling any popups
        long saveStartTime = System.currentTimeMillis();
        while (!systmOneWindow.waitVanish(saveDialogPattern, 1)) {  // Short timeout for quick checks
            if (System.currentTimeMillis() - saveStartTime > ApplicationConfig.DIALOG_TIMEOUT * 1000) {
                throw new FindFailed("Save dialog did not close within timeout");
            }
            
            if (popupHandler.isPopupPresent()) {
                logger.info("Handling popup during save operation");
                popupHandler.dismissPopup(false);
            }
            
            try {
                Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FindFailed("Save operation interrupted");
            }
        }
        
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