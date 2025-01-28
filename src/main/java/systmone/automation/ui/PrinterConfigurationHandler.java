package systmone.automation.ui;

import java.util.concurrent.TimeUnit;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

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

    /**
     * Configures SystmOne to use Microsoft Print to PDF as the default printer.
     * This process involves navigating through several windows and menus.
     * 
     * @return true if configuration was successful, false otherwise
     * @throws FindFailed if required UI elements cannot be found
     */
    public boolean configurePDFPrinter() throws FindFailed {
        try {
            logger.info("Starting PDF printer configuration");
    
            // Step 1: Right-click first document and open process menu
            Match documentMatch = searchRegions.getSelectionBorderRegion()
                .wait(selectionBorderPattern, ApplicationConfig.DIALOG_TIMEOUT);
            if (documentMatch == null) {
                throw new FindFailed("Could not find document to configure printer settings");
            }
            documentMatch.rightClick();
    
            // Step 2: Click "Process Patient Document (No OCR)"
            Pattern processDocPattern = new Pattern("process_doc_button.png")
                .similar(ApplicationConfig.DEFAULT_SIMILARITY);
            Match processButton = systmOneWindow.wait(processDocPattern, 
                ApplicationConfig.MENU_TIMEOUT);
            processButton.click();
    
            // Step 3: Focus and handle the "Scanned Document Update" window
            App scannedDocWindow = focusWindow("Scanned Document Update");
            Region scannedDocRegion = scannedDocWindow.window();
            
            // Step 4: Find and click "Printer Settings" in top-left area
            Region printerSettingsRegion = new Region(
                scannedDocRegion.x,
                scannedDocRegion.y,
                scannedDocRegion.w / 3,    // leftmost third
                scannedDocRegion.h / 10    // top 10%
            );
            
            Pattern printerSettingsPattern = new Pattern("printer_settings_button.png")
                .similar(ApplicationConfig.DEFAULT_SIMILARITY);
            Match printerSettings = printerSettingsRegion.wait(printerSettingsPattern, 
                ApplicationConfig.DIALOG_TIMEOUT);
            printerSettings.click();
    
            // Step 5: Focus and handle the printer settings dialog
            App printerSettingsWindow = focusWindow("Actioned Scanned Image Printer Settings");
            Region printerSettingsWindowRegion = printerSettingsWindow.window();
    
            Pattern printerDropdownPattern = new Pattern("printer_dropdown.png")
                .similar(ApplicationConfig.DEFAULT_SIMILARITY);
            Match printerDropdown = printerSettingsWindowRegion.wait(printerDropdownPattern, 
                ApplicationConfig.DIALOG_TIMEOUT);
            printerDropdown.click();
    
            // Type out full printer name to avoid ambiguity
            printerSettingsWindowRegion.type("Microsoft Print to PDF");
            printerSettingsWindowRegion.type(Key.ENTER);
    
            // Step 6: Find and click OK button
            Pattern okButtonPattern = new Pattern("ok_button.png")
                .similar(ApplicationConfig.DEFAULT_SIMILARITY);
            Match okButton = printerSettingsWindowRegion.wait(okButtonPattern, 
                ApplicationConfig.DIALOG_TIMEOUT);
            okButton.click();
    
            // Step 7: Close the Scanned Document Update window
            scannedDocWindow.window().type(Key.ESC);
    
            // Step 8: Refocus on SystmOne window
            systmOne.focus();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
    
            logger.info("Successfully configured PDF printer");
            return true;
    
        } catch (FindFailed | InterruptedException e) {
            logger.error("Failed to configure PDF printer: " + e.getMessage());
            attemptCleanup();
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during printer configuration: " + e.getMessage());
            attemptCleanup();
            return false;
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