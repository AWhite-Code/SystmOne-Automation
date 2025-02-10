package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import systmone.automation.state.WindowStateManager;
import systmone.automation.util.RetryOperationHandler;

import java.util.concurrent.TimeUnit;

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

    public boolean configurePDFPrinter() throws FindFailed {
        try {
            logger.info("Starting PDF printer configuration");
            int maxAttempts = ApplicationConfig.PRINTER_CONFIG_MAX_ATTEMPTS;
            int currentAttempt = 0;
            
            while (currentAttempt < maxAttempts) {
                currentAttempt++;
                logger.info("Configuration attempt {} of {}", currentAttempt, maxAttempts);
                
                try {
                    // Set initial state
                    popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION);
                    popupHandler.resetState(); // Reset popup tracking for fresh attempt
                    
                    // Clear any existing popups before starting
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                        logger.warn("Failed to handle initial popup, retrying...");
                        continue;
                    }
                    
                    // Use retry handler for the configuration process
                    boolean success = popupHandler.getRetryHandler().executeWithRetry(
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
                        
                        // Step 4: Open and configure printer settings
                        App printerSettingsWindow = openPrinterSettings(scannedDocWindow);
                        if (printerSettingsWindow == null) return false;
                        
                        // Step 5: Configure printer in settings window
                        if (!configurePrinterInSettings(printerSettingsWindow)) return false;
                        
                        return true;
                    } catch (Exception e) {
                        logger.error("Error in configuration process: {}", e.getMessage());
                        return false;
                    }
                },
                this::attemptCleanup,
                "printer configuration"
            );
                    
                    if (success) {
                        logger.info("Configuration completed successfully on attempt {}", currentAttempt);
                        return true;
                    }
                    
                    logger.warn("Configuration attempt {} failed, retrying...", currentAttempt);
                    attemptCleanup();
                    TimeUnit.MILLISECONDS.sleep(ApplicationConfig.RETRY_DELAY_MS);
                    
                } catch (Exception e) {
                    logger.error("Error during configuration attempt {}: {}", currentAttempt, e.getMessage());
                    attemptCleanup();
                    if (currentAttempt < maxAttempts) {
                        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.RETRY_DELAY_MS);
                    }
                }
            }
            
            logger.error("Configuration failed after {} attempts", maxAttempts);
            return false;
            
        } catch (Exception e) {
            logger.error("Fatal error in printer configuration: {}", e.getMessage(), e);
            attemptCleanup();
            return false;
        }
    }
    
    private Match openPrinterSettings(Region buttonRegion) throws FindFailed {
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

    private Match selectInitialDocument() throws FindFailed {
        logger.info("Looking for document to configure printer...");
        long methodStartTime = System.currentTimeMillis();
        
        popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION);
        
        long popupCheckStartTime = System.currentTimeMillis();
        if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
            return null;
        }
        long popupCheckEndTime = System.currentTimeMillis();
        logger.debug("Popup check in selectInitialDocument took {} ms", popupCheckEndTime - popupCheckStartTime);
        
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

    private boolean selectNoOCROption(Match documentMatch) throws FindFailed, InterruptedException {
        logger.info("Initiating context menu interaction...");
        long methodStartTime = System.currentTimeMillis();
        
        // Update state
        popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.CONTEXT_MENU);
        
        return popupHandler.getRetryHandler().executeWithRetry(
            () -> {
                try {
                    // Check for popups before right-click
                    long popupCheckStartTime = System.currentTimeMillis();
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                        return false;
                    }
                    long popupCheckEndTime = System.currentTimeMillis();
                    logger.debug("Popup check before right-click took {} ms", popupCheckEndTime - popupCheckStartTime);
                    
                    // Perform right-click on document
                    documentMatch.rightClick();
                    TimeUnit.MILLISECONDS.sleep(ApplicationConfig.CONTEXT_MENU_DELAY_MS);
                    
                    // Check for popups after right-click
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.CONTEXT_MENU)) {
                        return false;
                    }
                    
                    // Create screen object for keyboard input
                    Screen screen = new Screen();
                    
                    // Navigate menu using down arrow (corrected from UP)
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

    private App openDocumentUpdateWindow() throws FindFailed, InterruptedException {
        logger.info("Waiting for document update window...");
        
        popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_UPDATE);
        
        TimeUnit.MILLISECONDS.sleep(2000);
        
        App scannedDocWindow = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            logger.info("Attempt {} to find window...", attempt);
            
            try {
                if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_UPDATE)) {
                    continue;
                }
                
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

    private boolean configurePrinterInSettings(App printerSettingsWindow) throws FindFailed, InterruptedException {
        Region windowRegion = printerSettingsWindow.window();
        
        // Check for popups before starting configuration
        if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
            return false;
        }
        
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
    
        // Search for the dropdown arrow with reduced similarity threshold
        Pattern dropdownArrowPattern = new Pattern("dropdown_arrow.png")
            .similar(0.6f);
    
        try {
            // Check for popups before dropdown interaction
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                return false;
            }
            
            Match dropdownMatch = dropdownSearchRegion.find(dropdownArrowPattern);
            logger.info("Found dropdown at coordinates: ({}, {})", dropdownMatch.x, dropdownMatch.y);
            
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                return false;
            }
            
            dropdownMatch.click();
            TimeUnit.MILLISECONDS.sleep(500);
            
            // Check for popups after dropdown click
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                return false;
            }
            
            // Select the PDF printer
            windowRegion.type("Microsoft Print to PDF");
            windowRegion.type(Key.ENTER);
            TimeUnit.MILLISECONDS.sleep(500);
            
            // Check for popups after printer selection
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                return false;
            }
            
            logger.info("Looking for OK button...");
            
            // Define region for OK button in bottom half of window
            Region buttonRegion = new Region(
                windowRegion.x,                        
                windowRegion.y + (windowRegion.h / 2), 
                windowRegion.w,                        
                windowRegion.h / 2                     
            );
            
            try {
                Match okButton = buttonRegion.waitText("Ok", 2);
                logger.info("Found OK button, clicking...");
                
                // Check for popups before clicking OK
                if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                    return false;
                }
                
                okButton.click();
                TimeUnit.MILLISECONDS.sleep(500);
                
                // Final popup check after clicking OK
                return popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS);
                
            } catch (FindFailed e) {
                logger.error("Could not find OK button");
                return false;
            }
            
        } catch (FindFailed e) {
            logger.error("Could not find dropdown arrow: {}", e.getMessage());
            return false;
        }
    }
}