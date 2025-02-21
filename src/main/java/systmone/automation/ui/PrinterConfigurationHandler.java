package systmone.automation.ui;

import java.util.concurrent.TimeUnit;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import systmone.automation.killswitch.GlobalKillswitch;
import systmone.automation.state.WindowStateManager;


public class PrinterConfigurationHandler {
    private static final Logger logger = LoggerFactory.getLogger(PrinterConfigurationHandler.class);

    private final App systmOne;
    private final Region systmOneWindow;
    private final SearchRegions searchRegions;
    private final Pattern selectionBorderPattern;
    private final GlobalKillswitch killSwitch;
    private final Pattern popupPattern;
    private final WindowStateManager windowManager;
    private final PrinterConfigurationPopupHandler popupHandler;
    private Match currentDocumentMatch;

    public PrinterConfigurationHandler(
            App systmOne,
            Region systmOneWindow,
            SearchRegions searchRegions,
            Pattern selectionBorderPattern,
            Pattern popupPattern,
            GlobalKillswitch killSwitch) {
        
        if (killSwitch == null) {
            throw new IllegalArgumentException("KillSwitch must be provided");
        }

        try {
            this.systmOne = systmOne;
            this.systmOneWindow = systmOneWindow;
            this.searchRegions = searchRegions;
            this.selectionBorderPattern = selectionBorderPattern;
            this.popupPattern = popupPattern;
            this.killSwitch = killSwitch;
            
            this.windowManager = new WindowStateManager(systmOne);
            
            this.popupHandler = new PrinterConfigurationPopupHandler(
                windowManager,
                systmOneWindow,
                popupPattern,
                killSwitch
            );
            
            logger.debug("Printer configuration handler initialized");
            
        } catch (Exception e) {
            logger.error("Failed to initialize printer configuration handler: ", e);
            throw e;
        }
    }


    public boolean configurePDFPrinter() throws FindFailed {
        try {
            logger.info("Starting PDF printer configuration");
            int maxAttempts = ApplicationConfig.PRINTER_CONFIG_MAX_ATTEMPTS;
            int currentAttempt = 0;
    
            if (killSwitch.isKillSignalReceived()) {
                logger.info("Kill switch activated - aborting printer configuration");
                return false;
            }
            
            while (currentAttempt < maxAttempts) {
                currentAttempt++;
                
                try {
                    popupHandler.updateState(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION);
                    popupHandler.resetState();
                    
                    if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                        logger.warn("Failed to handle initial popup, retrying...");
                        continue;
                    }
                    
                    boolean success = popupHandler.getRetryHandler().executeWithRetry(
                        () -> {
                            try {
                                if (killSwitch.isKillSignalReceived()) {
                                    logger.info("Kill switch activated during configuration");
                                    return false;
                                }
    
                                Match documentMatch = selectInitialDocument();
                                if (documentMatch == null || killSwitch.isKillSignalReceived()) return false;
                                
                                if (!selectNoOCROption(documentMatch) || killSwitch.isKillSignalReceived()) return false;
                                
                                App scannedDocWindow = openDocumentUpdateWindow();
                                if (scannedDocWindow == null || killSwitch.isKillSignalReceived()) return false;
                                
                                App printerSettingsWindow = openPrinterSettings(scannedDocWindow);
                                if (printerSettingsWindow == null || killSwitch.isKillSignalReceived()) return false;
                                
                                if (!configurePrinterInSettings(printerSettingsWindow) || killSwitch.isKillSignalReceived()) return false;
    
                                if (!cleanupAndClose(scannedDocWindow)) return false;
                                
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
                        logger.info("Printer configuration completed successfully");
                        return true;
                    }
                    
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

    /**
     * Performs cleanup operations by closing windows and returning focus
     * to the main application window.
     */
    private boolean cleanupAndClose(App scannedDocWindow) throws InterruptedException {
        logger.info("Cleaning up and closing windows...");
        
        try {
            // Check for popups before closing
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                return false;
            }
            
            // Close the Scanned Document Update window
            if (scannedDocWindow != null && scannedDocWindow.window() != null) {
                scannedDocWindow.window().type(Key.ESC);
            }
            
            // Give windows time to close
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
            
            // Check for any popups that might have appeared during closing
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.DOCUMENT_SELECTION)) {
                return false;
            }
            
            // Refocus on SystmOne window
            systmOne.focus();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
            
            return true;
            
        } catch (Exception e) {
            logger.warn("Error during cleanup: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Opens the printer settings window using text-based button detection.
     */
    private App openPrinterSettings(App scannedDocWindow) throws FindFailed, InterruptedException {
        logger.info("Opening printer settings using text recognition...");
        
        Region scannedDocRegion = scannedDocWindow.window();
        
        // Define search region in top third of window where button typically appears
        Region buttonRegion = new Region(
            scannedDocRegion.x,
            scannedDocRegion.y,
            scannedDocRegion.w / 3,  // First third of window width
            scannedDocRegion.h / 4   // First quarter of window height
        );
    
        // Check for popups before searching for button
        if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
            return null;
        }
    
        // Progressive retry approach for first-time loading
        Match buttonMatch = null;
        int maxAttempts = 3;
        int currentAttempt = 0;
        double baseTimeout = ApplicationConfig.DIALOG_TIMEOUT;
        
        while (buttonMatch == null && currentAttempt < maxAttempts) {
            try {
                currentAttempt++;
                // Increase timeout for each retry
                double adjustedTimeout = baseTimeout * currentAttempt;
                logger.debug("Attempt {} looking for printer settings button with {}ms timeout", 
                    currentAttempt, adjustedTimeout);
                
                // Wait for text with progressively longer timeouts
                buttonMatch = buttonRegion.waitText("Printer Settings", adjustedTimeout);
                
                if (buttonMatch != null) {
                    break;
                }
                
                // Short pause between attempts
                TimeUnit.MILLISECONDS.sleep(1000);
                
            } catch (FindFailed e) {
                logger.debug("Attempt {} failed to find button", currentAttempt);
                if (currentAttempt >= maxAttempts) {
                    logger.error("Could not find 'Printer Settings' button after {} attempts", maxAttempts);
                    return null;
                }
            }
        }
    
        if (buttonMatch == null) {
            logger.error("Could not find 'Printer Settings' button using text recognition");
            return null;
        }
    
        // Rest of the method remains the same
        if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
            return null;
        }
    
        buttonMatch.click();
        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.BUTTON_CLICK_DELAY_MS);
    
        if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
            return null;
        }
    
        App printerSettingsWindow = new App("Actioned Scanned Image Printer Settings");
        if (printerSettingsWindow.window() == null) {
            logger.error("Printer settings window did not open after clicking text button");
            return null;
        }
    
        printerSettingsWindow.focus();
        TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
    
        return printerSettingsWindow;
    }
    
    private Match selectInitialDocument() throws FindFailed {
        logger.info("Looking for document to configure printer...");
        
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
            if (killSwitch.isKilled()) {
                logger.info("Kill switch activated during window search");
                return null;
            }
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
        if (killSwitch.isKilled()) return false;
    
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
            windowRegion.x + (columnWidth * 3),  
            windowRegion.y + rowHeight - 20,     
            columnWidth * 2,                     
            rowHeight                            
        );
    
        if (killSwitch.isKilled()) {
            attemptCleanup();
            return false;
        }
        
        // Dropdown arrow pattern matching and interaction code remains the same...
        Pattern dropdownArrowPattern = new Pattern("dropdown_arrow.png")
            .similar(0.6f);
    
        Screen screen = new Screen();  // Moved up for broader scope
        
        try {
            // Previous dropdown interaction code remains the same...
            Match dropdownMatch = dropdownSearchRegion.find(dropdownArrowPattern);
            logger.info("Found dropdown at coordinates: ({}, {})", dropdownMatch.x, dropdownMatch.y);
            
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                return false;
            }
            
            dropdownMatch.click();
            TimeUnit.MILLISECONDS.sleep(500);
            
            // Select the PDF printer
            windowRegion.type("Microsoft Print to PDF");
            windowRegion.type(Key.ENTER);
            TimeUnit.MILLISECONDS.sleep(500);
            
            // Check for popups after printer selection
            if (!popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS)) {
                return false;
            }
            
            logger.info("Looking for OK button - preparing Alt+O sequence...");
            
            // First attempt with short pauses
            screen.keyDown(Key.ALT);
            TimeUnit.MILLISECONDS.sleep(10);  // Brief pause while holding Alt
            
            screen.type("o");  // Send the 'o' key
            TimeUnit.MILLISECONDS.sleep(10);  // Brief pause before releasing Alt
            
            screen.keyUp(Key.ALT);
            TimeUnit.MILLISECONDS.sleep(100); // Longer pause after the full sequence
            
            // Verify window state and try fallbacks if needed
            if (printerSettingsWindow.window() != null) {
                logger.warn("Window still open after Alt+O sequence, attempting fallback...");
                
                // Second attempt with longer pauses
                TimeUnit.MILLISECONDS.sleep(200);  // Additional pause before retry
                
                screen.keyDown(Key.ALT);
                TimeUnit.MILLISECONDS.sleep(20);   // Longer pause on retry
                screen.type("o");
                TimeUnit.MILLISECONDS.sleep(20);
                screen.keyUp(Key.ALT);
                TimeUnit.MILLISECONDS.sleep(500);
            }
            
            // Final popup check
            return popupHandler.handlePopupIfPresent(PrinterConfigurationPopupHandler.PrinterConfigState.PRINTER_SETTINGS);
            
        } catch (FindFailed e) {
            logger.error("Failed to find UI element: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error during printer configuration: {}", e.getMessage());
            return false;
        } finally {
            // Always ensure ALT key is released
            try {
                screen.keyUp(Key.ALT);
            } catch (Exception releaseError) {
                logger.warn("Error ensuring Alt key release: {}", releaseError.getMessage());
            }
        }
    }
}