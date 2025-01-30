package systmone.automation.ui;

import java.util.concurrent.TimeUnit;

import org.sikuli.script.*;
import org.sikuli.basics.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import java.util.Iterator;

/**
 * Handles the configuration of the PDF printer in SystmOne.
 * This class manages the complex sequence of UI interactions required
 * to set Microsoft Print to PDF as the default printer for document processing.
 * 
 * The configuration process involves:
 * 1. Opening document processing settings
 * 2. Navigating to printer settings
 * 3. Setting Microsoft Print to PDF as default
 * 4. Closing all configuration windows
 * 
 * Dependencies:
 * - Requires access to SystmOne window and UI elements
 * - Uses Sikuli for UI automation
 * - Needs SearchRegions for optimized element detection
 */
public class PrinterConfigurationHandler {
    private static final Logger logger = LoggerFactory.getLogger(PrinterConfigurationHandler.class);

    private final App systmOne;
    private final Region systmOneWindow;
    private final SearchRegions searchRegions;
    private final Pattern selectionBorderPattern;

    public PrinterConfigurationHandler(App systmOne, Region systmOneWindow, 
            SearchRegions searchRegions, Pattern selectionBorderPattern) {
        this.systmOne = systmOne;
        this.systmOneWindow = systmOneWindow;
        this.searchRegions = searchRegions;
        this.selectionBorderPattern = selectionBorderPattern;
    }

    // TODO: IMPLEMENT POP UP HANDLING HERE
    
    /**
     * Main entry point for printer configuration. Orchestrates the entire process
     * by calling specific methods for each major step.
     */
    public boolean configurePDFPrinter() throws FindFailed {
        try {
            logger.info("Starting PDF printer configuration");
            
            // Step 1: Select document
            Match documentMatch = selectInitialDocument();
            if (documentMatch == null) return false;
            
            // Step 2: Open and handle context menu
            if (!selectNoOCROption(documentMatch)) return false;
            
            // Step 3: Open and handle document update window
            App scannedDocWindow = openDocumentUpdateWindow();
            if (scannedDocWindow == null) return false;
            
            // Step 4: Open printer settings
            App printerSettingsWindow = openPrinterSettings(scannedDocWindow);
            if (printerSettingsWindow == null) return false;
            
            // Step 5: Configure printer
            if (!configurePrinterInSettings(printerSettingsWindow)) return false;
            
            // Step 6: Clean up
            cleanupAndClose(scannedDocWindow);
            
            logger.info("Successfully configured PDF printer");
            return true;
            
        } catch (Exception e) {
            logger.error("Unexpected error in printer configuration: {}", e.getMessage(), e);
            attemptCleanup();
            return false;
        }
    }

    /**
     * Finds and selects the initial document for configuration.
     */
    private Match selectInitialDocument() throws FindFailed {
        logger.info("Looking for document to configure printer...");
        Match documentMatch = searchRegions.getSelectionBorderRegion()
            .wait(selectionBorderPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        if (documentMatch == null) {
            logger.error("Could not find document to configure printer settings");
            return null;
        }
        
        return documentMatch;
    }

    /**
     * Handles the context menu interaction to select the No OCR option.
     */
    private boolean selectNoOCROption(Match documentMatch) throws FindFailed, InterruptedException {
        logger.info("Found document, performing right-click...");
        documentMatch.rightClick();
        
        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.CONTEXT_MENU_DELAY_MS);
        
        Settings.OcrTextRead = true;
        Settings.OcrTextSearch = true;
        
        Region menuRegion = new Region(
            documentMatch.x,
            documentMatch.y,
            400,
            400
        );
        
        logger.info("Searching for 'No OCR' menu option...");
        String menuText = menuRegion.text();
        logger.debug("Menu text found: '{}'", menuText);
        
        Match menuItem = menuRegion.waitText("Process Patient Document (No OCR)", 2);
        if (menuItem == null) {
            logger.error("Could not find No OCR menu option");
            return false;
        }
        
        menuItem.click();
        return true;
    }

    /**
     * Opens and verifies the document update window.
     */
    private App openDocumentUpdateWindow() throws FindFailed, InterruptedException {
        logger.info("Waiting for document update window...");
        TimeUnit.MILLISECONDS.sleep(2000);
        
        App scannedDocWindow = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            logger.info("Attempt {} to find window...", attempt);
            
            try {
                scannedDocWindow = new App("Scanned Document Update");
                if (scannedDocWindow.window() != null) {
                    TimeUnit.MILLISECONDS.sleep(500);
                    scannedDocWindow.focus();
                    logger.info("Window focused successfully");
                    break;
                }
            } catch (Exception e) {
                logger.debug("Attempt {} failed: {}", attempt, e.getMessage());
            }
            
            TimeUnit.MILLISECONDS.sleep(1000);
        }
        
        return scannedDocWindow;
    }

    /**
     * Opens the printer settings window using text-based button detection.
     */
    private App openPrinterSettings(App scannedDocWindow) throws FindFailed, InterruptedException {
        Region scannedDocRegion = scannedDocWindow.window();
        Region buttonRegion = new Region(
            scannedDocRegion.x,
            scannedDocRegion.y,
            scannedDocRegion.w / 3,
            scannedDocRegion.h / 4
        );
        
        Match buttonMatch = findPrinterSettingsButton(buttonRegion);
        if (buttonMatch == null) return null;
        
        buttonMatch.click();
        TimeUnit.MILLISECONDS.sleep(1000);
        
        return focusWindow("Actioned Scanned Image Printer Settings");
    }

    /**
     * Handles the printer configuration in the settings window using text-based detection.
     */
    private boolean configurePrinterInSettings(App printerSettingsWindow) throws FindFailed, InterruptedException {
        Region windowRegion = printerSettingsWindow.window();
        
        // Give the window time to fully render
        TimeUnit.MILLISECONDS.sleep(1000);
        
        logger.info("Looking for printer dropdown in settings window...");
        
        // Create a region that focuses on the top portion where the dropdown is
        // We know from the screenshot it's near the top of the window
        Region dropdownSearchRegion = new Region(
            windowRegion.x,                    // Start from left edge
            windowRegion.y + 30,              // Start a bit below the title bar
            windowRegion.w,                    // Full width
            100                               // Focus on just the top portion
        );
    
        // Now look for the dropdown arrow pattern with a lower similarity threshold
        // This accounts for different Windows themes and scaling
        Pattern dropdownArrowPattern = new Pattern("dropdown_arrow.png")
            .similar(0.6f);  // Lower similarity threshold
    
        Match dropdownMatch = null;
        try {
            // Find all matches and get the leftmost one (closest to "Printer" label)
            Iterator<Match> matches = dropdownSearchRegion.findAll(dropdownArrowPattern);
            Match leftmostMatch = null;
            int leftmostX = Integer.MAX_VALUE;
    
            while(matches.hasNext()) {
                Match match = matches.next();
                if (match.x < leftmostX) {
                    leftmostX = match.x;
                    leftmostMatch = match;
                }
            }
            
            dropdownMatch = leftmostMatch;
        } catch (FindFailed e) {
            logger.error("Could not find dropdown arrow: {}", e.getMessage());
            return false;
        }
    
        if (dropdownMatch == null) {
            logger.error("No dropdown arrow found in the expected region");
            return false;
        }
    
        logger.info("Found dropdown, clicking...");
        dropdownMatch.click();
        
        // Give the dropdown menu time to appear
        TimeUnit.MILLISECONDS.sleep(500);
        
        // Type the printer name and press Enter
        windowRegion.type("Microsoft Print to PDF");
        windowRegion.type(Key.ENTER);
    
        // After selecting the printer and pressing Enter, wait a moment for any UI updates
        TimeUnit.MILLISECONDS.sleep(500);
        
        logger.info("Looking for OK button...");
        
        // Create a region focusing on the bottom portion of the window where OK buttons typically appear
        Region buttonRegion = new Region(
            windowRegion.x,                        // Start from left edge
            windowRegion.y + (windowRegion.h / 2), // Start from middle of window
            windowRegion.w,                        // Full width
            windowRegion.h / 2                     // Bottom half of window
        );
        
        // Enable OCR for text search
        Settings.OcrTextRead = true;
        Settings.OcrTextSearch = true;
        
        // Try variations of OK text that might appear
        String[] okVariations = {"OK", "Ok", "ok"};
        Match okButton = null;
        
        for (String okText : okVariations) {
            try {
                okButton = buttonRegion.waitText(okText, 2);
                if (okButton != null) {
                    logger.info("Found OK button with text: '{}'", okText);
                    break;
                }
            } catch (FindFailed e) {
                logger.debug("Did not find OK button with text: '{}'", okText);
            }
        }
        
        if (okButton == null) {
            logger.error("Could not find OK button");
            return false;
        }
        
        logger.info("Clicking OK button...");
        okButton.click();
        
        // Wait a moment to ensure the click is processed
        TimeUnit.MILLISECONDS.sleep(500);
        
        return true;
    }

    /**
     * Finds the Printer Settings button using text recognition.
     * Tries various text patterns that might match the button.
     */
    private Match findPrinterSettingsButton(Region buttonRegion) throws FindFailed {
        String[] buttonTextVariations = {
            "Printer Settings",
            "PrinterSettings",
            "Printer",
            "Settings"
        };
        
        for (String textVariation : buttonTextVariations) {
            try {
                logger.debug("Trying to find text: '{}'", textVariation);
                Match buttonMatch = buttonRegion.waitText(textVariation, 2);
                if (buttonMatch != null) {
                    logger.info("Found button using text: '{}'", textVariation);
                    return buttonMatch;
                }
            } catch (FindFailed e) {
                logger.debug("Did not find text: '{}'", textVariation);
            }
        }
        
        logger.error("Could not find Printer Settings button");
        return null;
    }

    /**
     * Performs cleanup operations by closing windows and returning focus
     * to the main application window.
     */
    private void cleanupAndClose(App scannedDocWindow) throws InterruptedException {
        logger.info("Cleaning up and closing windows...");
        
        try {
            // Close the Scanned Document Update window
            if (scannedDocWindow != null && scannedDocWindow.window() != null) {
                scannedDocWindow.window().type(Key.ESC);
            }
            
            // Give windows time to close
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
            
            // Refocus on SystmOne window
            systmOne.focus();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
            
        } catch (Exception e) {
            logger.warn("Error during cleanup: {}", e.getMessage());
            // Continue anyway since this is cleanup code
        }
    }

    // Taken from SystmOneAutomator Class. Should refactor so I dont have 2 duplicates of the same function
    /**
     * Focuses and handles operations on the Scanned Document Update window.
     * Returns the App reference for the focused window.
     * 
     * @param windowTitle The title of the window to focus
     * @return The focused App instance
     * @throws FindFailed if the window cannot be found
     * @throws InterruptedException if the focus operation is interrupted
     */
    private App focusWindow(String windowTitle) throws FindFailed, InterruptedException {
        App window = new App(windowTitle);
        window.focus();
        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
        return window;
    }

    /**
     * Attempts to clean up any open dialogs or windows after a failure.
     */
    private void attemptCleanup() {
        try {
            systmOneWindow.type(Key.ESC);
            Thread.sleep(500);
            systmOneWindow.type(Key.ESC);
            systmOne.focus();
        } catch (Exception cleanupError) {
            logger.error("Error during cleanup: " + cleanupError.getMessage());
        }
    }
}