package systmone.automation;


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

    public SystmOneAutomator(ApplicationConfig.Location location, double patternSimilarity) throws FindFailed {
        this.location = location;
        this.systmOne = initializeApp();
        this.systmOneWindow = systmOne.window();
        this.selectionBorderPattern = initializePattern("selection_border", patternSimilarity);
        this.printMenuItemPattern = initializePattern("print_menu_item", patternSimilarity);
        this.documentCountPattern = initializePattern("document_count", patternSimilarity);
        this.saveDialogPattern = initializePattern("save_dialog_title", patternSimilarity);
        
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
        documentMatch.rightClick();
        
        Match printMenuItem = systmOneWindow.wait(printMenuItemPattern, 
            ApplicationConfig.MENU_TIMEOUT);
        printMenuItem.click();
        
        systmOneWindow.wait(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        systmOneWindow.type("a", KeyModifier.CTRL);
        systmOneWindow.type("v", KeyModifier.CTRL);
        systmOneWindow.type(Key.ENTER);
        
        systmOneWindow.waitVanish(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        logger.info("Saved document to: {}", savePath);
    }

    public void navigateDown() {
        systmOneWindow.type(Key.DOWN);
    }

    public Match waitForStableElement(int timeout) throws FindFailed {
        return systmOneWindow.wait(selectionBorderPattern, timeout);
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