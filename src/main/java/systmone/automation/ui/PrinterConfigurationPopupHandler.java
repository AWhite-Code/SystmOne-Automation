package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import systmone.automation.state.WindowStateManager;
import systmone.automation.util.RetryOperationHandler;

import java.util.concurrent.TimeUnit;

/**
 * Specialized popup handler for printer configuration operations.
 * Integrates with WindowStateManager and RetryOperationHandler for robust
 * popup detection and recovery during printer setup.
 */
public class PrinterConfigurationPopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PrinterConfigurationPopupHandler.class);

    private final WindowStateManager windowManager;
    private final RetryOperationHandler retryHandler;
    private final Region systmOneWindow;
    private final Pattern popupPattern;
    
    // States for printer configuration
    public enum PrinterConfigState {
        DOCUMENT_SELECTION,
        CONTEXT_MENU,
        DOCUMENT_UPDATE,
        PRINTER_SETTINGS
    }
    
    private PrinterConfigState currentState;

    public PrinterConfigurationPopupHandler(
            WindowStateManager windowManager,
            Region systmOneWindow,
            Pattern popupPattern) {
        this.windowManager = windowManager;
        this.systmOneWindow = systmOneWindow;
        this.popupPattern = popupPattern;
        
        // Initialize retry handler with printer-specific configurations
        this.retryHandler = RetryOperationHandler.builder()
                .maxAttempts(ApplicationConfig.PRINTER_CONFIG_MAX_ATTEMPTS)
                .delayBetweenAttempts(ApplicationConfig.DEFAULT_RETRY_DELAY_MS)
                .build();
                
        this.currentState = PrinterConfigState.DOCUMENT_SELECTION;
    }

    /**
     * Checks for and handles any popups, performing appropriate cleanup based on current state.
     * 
     * @param returnToState State to return to after popup handling
     * @return true if popup was handled successfully
     */
    public boolean handlePopupIfPresent(PrinterConfigState returnToState) {
        try {
            Match popup = systmOneWindow.exists(popupPattern);
            if (popup == null) {
                return true; // No popup present
            }

            logger.info("Popup detected during printer configuration in state: {}", currentState);
            
            // Close popup
            popup.click();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
            
            // Perform state-specific cleanup
            cleanupCurrentState();
            
            // Return to specified state
            return returnToState(returnToState);
            
        } catch (Exception e) {
            logger.error("Error handling popup: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Performs cleanup actions based on current state.
     */
    private void cleanupCurrentState() throws InterruptedException {
        switch (currentState) {
            case DOCUMENT_SELECTION:
                // Just ensure we're on main window
                systmOneWindow.type(Key.ESC);
                TimeUnit.MILLISECONDS.sleep(ApplicationConfig.MENU_CLEANUP_DELAY_MS);
                break;
                
            case CONTEXT_MENU:
                // Close context menu if open
                systmOneWindow.type(Key.ESC);
                TimeUnit.MILLISECONDS.sleep(ApplicationConfig.MENU_CLEANUP_DELAY_MS);
                break;
                
            case DOCUMENT_UPDATE:
                // Close document update window if open
                if (windowManager.getCurrentFocusedWindow() != null) {
                    windowManager.getCurrentFocusedWindow().window().type(Key.ESC);
                    TimeUnit.MILLISECONDS.sleep(ApplicationConfig.MENU_CLEANUP_DELAY_MS);
                }
                break;
                
            case PRINTER_SETTINGS:
                // Close printer settings window if open
                if (windowManager.getCurrentFocusedWindow() != null) {
                    windowManager.getCurrentFocusedWindow().window().type(Key.ESC);
                    TimeUnit.MILLISECONDS.sleep(ApplicationConfig.MENU_CLEANUP_DELAY_MS);
                }
                break;
        }
        
        // Always return to main window after cleanup
        windowManager.returnToMainWindow();
    }

    /**
     * Returns to a specified state after popup handling.
     */
    private boolean returnToState(PrinterConfigState targetState) {
        try {
            currentState = targetState;
            switch (targetState) {
                case DOCUMENT_SELECTION:
                    // Just ensure main window focus
                    return windowManager.returnToMainWindow();
                    
                case DOCUMENT_UPDATE:
                    // Attempt to refocus document update window
                    return windowManager.focusAndVerifyWindow("Scanned Document Update");
                    
                case PRINTER_SETTINGS:
                    // Attempt to refocus printer settings window
                    return windowManager.focusAndVerifyWindow("Actioned Scanned Image Printer Settings");
                    
                default:
                    return windowManager.returnToMainWindow();
            }
        } catch (Exception e) {
            logger.error("Error returning to state {}: {}", targetState, e.getMessage());
            return false;
        }
    }

    /**
     * Updates current state of the configuration process.
     */
    public void updateState(PrinterConfigState newState) {
        this.currentState = newState;
    }

    /**
     * Gets current state of the configuration process.
     */
    public PrinterConfigState getCurrentState() {
        return currentState;
    }

    /**
     * Provides access to the retry handler for operations.
     */
    public RetryOperationHandler getRetryHandler() {
        return retryHandler;
    }
}