package systmone.automation.ui;

import java.util.concurrent.TimeUnit;

import org.sikuli.script.*;
import org.sikuli.basics.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import systmone.automation.state.WindowStateManager;
import systmone.automation.ui.PrinterConfigurationPopupHandler;

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
    private final Pattern popupPattern;
    private final WindowStateManager windowManager;
    private final PrinterConfigurationPopupHandler popupHandler;
    private Match currentDocumentMatch;

    public PrinterConfigurationHandler(
            App systmOne, 
            Region systmOneWindow, 
            SearchRegions searchRegions, 
            Pattern selectionBorderPattern,
            Pattern popupPattern) {
        this.systmOne = systmOne;
        this.systmOneWindow = systmOneWindow;
        this.searchRegions = searchRegions;
        this.selectionBorderPattern = selectionBorderPattern;
        this.popupPattern = popupPattern;
        
        // Initialize infrastructure components
        this.windowManager = new WindowStateManager(systmOne);
        this.popupHandler = new PrinterConfigurationPopupHandler(
            windowManager,
            systmOneWindow,
            popupPattern
        );
    }


    /**
     * Main entry point for printer configuration. Orchestrates the entire process
     * by calling specific methods for each major step.
     */
    public boolean configurePDFPrinter() throws FindFailed {
        try {
            logger.info("Starting PDF printer configuration");
            
            // Set initial state
            popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION);
            
            // Clear any existing popups before starting
            if (popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                windowManager.returnToMainWindow();
            }
            
            // Use retry handler for the entire configuration process
            return popupHandler.getRetryHandler().executeWithRetry(
                () -> {
                    try {
                        // Step 1: Select document
                        Match documentMatch = selectInitialDocument();
                        if (documentMatch == null) return false;
                        
                        // Step 2: Open and handle context menu
                        if (!selectNoOCROption(documentMatch)) return false;
                        
                        // Step 3: Open and handle document update window
                        App scannedDocWindow = openDocumentUpdateWindow();
                        if (scannedDocWindow == null) return false;
                        
                        // Continue with remaining steps...
                        return true;
                        
                    } catch (Exception e) {
                        logger.error("Error in configuration process: {}", e.getMessage());
                        return false;
                    }
                },
                this::attemptCleanup,
                "printer configuration"
            );
            
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
        
        popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION);
        
        if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
            return null;
        }
        
        return popupHandler.getRetryHandler().executeWithRetry(
            () -> {
                try {
                    Match documentMatch = searchRegions.getSelectionBorderRegion()
                        .wait(selectionBorderPattern, ApplicationConfig.DIALOG_TIMEOUT);
                    
                    if (documentMatch == null) {
                        logger.error("Could not find document to configure printer settings");
                        return false;
                    }
                    
                    // Move mouse to the center of the matched region and click
                    documentMatch.click();
                    
                    // Verify the click was successful by checking pattern again
                    Match verificationMatch = searchRegions.getSelectionBorderRegion()
                        .exists(selectionBorderPattern);
                        
                    if (verificationMatch == null) {
                        logger.error("Failed to verify document selection after click");
                        return false;
                    }
                    
                    this.currentDocumentMatch = verificationMatch;
                    
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                        return false;
                    }
                    
                    return true;
                    
                } catch (FindFailed e) {
                    logger.error("Error finding document: {}", e.getMessage());
                    return false;
                }
            },
            null,
            "document selection"
        ) ? currentDocumentMatch : null;
    }

    /**
     * Handles the context menu interaction to select the No OCR option using keyboard navigation.
     * This method uses keyboard inputs instead of OCR text detection for more reliable operation.
     * 
     * @param documentMatch The Match object representing the found document
     * @return boolean indicating whether the operation was successful
     * @throws FindFailed if the document interaction fails
     * @throws InterruptedException if the thread is interrupted during delays
     */
    private boolean selectNoOCROption(Match documentMatch) throws FindFailed, InterruptedException {
        logger.info("Initiating context menu interaction...");
        
        // Update state
        popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.CONTEXT_MENU);
        
        return popupHandler.getRetryHandler().executeWithRetry(
            () -> {
                try {
                    // Check for popups before right-click
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                        return false;
                    }
                    
                    // Perform right-click on document
                    documentMatch.rightClick();
                    TimeUnit.MILLISECONDS.sleep(ApplicationConfig.CONTEXT_MENU_DELAY_MS);
                    
                    // Check for popups after right-click
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.CONTEXT_MENU)) {
                        return false;
                    }
                    
                    // Create screen object for keyboard input
                    Screen screen = new Screen();
                    
                    // Navigate menu
                    screen.type(Key.DOWN);
                    TimeUnit.MILLISECONDS.sleep(100);
                    
                    // Check for popups before final selection
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.CONTEXT_MENU)) {
                        return false;
                    }
                    
                    // Make selection
                    screen.type(Key.ENTER);
                    
                    logger.info("Successfully selected No OCR option");
                    return true;
                    
                } catch (Exception e) {
                    logger.error("Failed to select No OCR option: {}", e.getMessage());
                    return false;
                }
            },
            () -> {
                try {
                    // Ensure context menu is closed on failure
                    systmOneWindow.type(Key.ESC);
                    TimeUnit.MILLISECONDS.sleep(ApplicationConfig.MENU_CLEANUP_DELAY_MS);
                } catch (Exception e) {
                    logger.warn("Error during context menu cleanup: {}", e.getMessage());
                }
            },
            "context menu interaction"
        );
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
        
        // Calculate dimensions on a 5×5 grid for precise positioning
        int columnWidth = windowRegion.w / 5;   
        int rowHeight = windowRegion.h / 5;     
        
        // Define search region for the dropdown arrow, with a small upward adjustment
        Region dropdownSearchRegion = new Region(
            windowRegion.x + (columnWidth * 3),  // Start from 4th column
            windowRegion.y + rowHeight - 20,     // Start from 2nd row, adjusted up slightly
            columnWidth * 2,                     // Cover two columns width
            rowHeight                            // Cover one row height
        );
    
        // Search for the dropdown arrow with reduced similarity threshold to account for UI variations
        Pattern dropdownArrowPattern = new Pattern("dropdown_arrow.png")
            .similar(0.6f);
    
        // Try to find and click the dropdown arrow
        try {
            Match dropdownMatch = dropdownSearchRegion.find(dropdownArrowPattern);
            logger.info("Found dropdown at coordinates: ({}, {})", dropdownMatch.x, dropdownMatch.y);
            dropdownMatch.click();
        } catch (FindFailed e) {
            logger.error("Could not find dropdown arrow: {}", e.getMessage());
            return false;
        }
        
        // Give the dropdown menu time to appear
        TimeUnit.MILLISECONDS.sleep(500);
        
        // Select the PDF printer
        windowRegion.type("Microsoft Print to PDF");
        windowRegion.type(Key.ENTER);
    
        // Allow time for UI update after printer selection
        TimeUnit.MILLISECONDS.sleep(500);
        
        logger.info("Looking for OK button...");
        
        // Define region for OK button in bottom half of window
        Region buttonRegion = new Region(
            windowRegion.x,                        
            windowRegion.y + (windowRegion.h / 2), 
            windowRegion.w,                        
            windowRegion.h / 2                     
        );
        
        // Enable OCR for text search
        Settings.OcrTextRead = true;
        Settings.OcrTextSearch = true;
        
        // Look for OK button
        try {
            Match okButton = buttonRegion.waitText("Ok", 2);
            logger.info("Found OK button, clicking...");
            okButton.click();
        } catch (FindFailed e) {
            logger.error("Could not find OK button");
            return false;
        }
    
        // Allow time for click processing
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