package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;
import systmone.automation.killswitch.GlobalKillswitch;
import systmone.automation.state.WindowStateManager;
import systmone.automation.util.RetryOperationHandler;

import java.util.concurrent.TimeUnit;

public class PrinterConfigurationPopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PrinterConfigurationPopupHandler.class);

    private final WindowStateManager windowManager;
    private final RetryOperationHandler retryHandler;
    private final Region systmOneWindow;
    private final Pattern popupPattern;
    private final Region iconRegion;
    
    public enum PrinterConfigState {
        DOCUMENT_SELECTION,
        CONTEXT_MENU,
        DOCUMENT_UPDATE,
        PRINTER_SETTINGS
    }
    
    private PrinterConfigState currentState;
    private boolean wasPopupHandled;
    private long lastPopupTime;

    public PrinterConfigurationPopupHandler(
            WindowStateManager windowManager,
            Region systmOneWindow,
            Pattern popupPattern,
            GlobalKillswitch killSwitch) {
        this.windowManager = windowManager;
        this.systmOneWindow = systmOneWindow;
        this.popupPattern = popupPattern;
        
        this.retryHandler = RetryOperationHandler.builder()
                .maxAttempts(ApplicationConfig.PRINTER_CONFIG_MAX_ATTEMPTS)
                .delayBetweenAttempts(ApplicationConfig.DEFAULT_RETRY_DELAY_MS)
                .killSwitch(killSwitch)
                .build();
                
        this.currentState = PrinterConfigState.DOCUMENT_SELECTION;

        // Define popup detection region
        int screenWidth = systmOneWindow.w;
        int screenHeight = systmOneWindow.h;
        int quarterWidth = screenWidth / 4;
        int quarterHeight = screenHeight / 4;
        
        this.iconRegion = new Region(
            systmOneWindow.x + quarterWidth,
            systmOneWindow.y + quarterHeight,
            quarterWidth * 2,
            quarterHeight * 2
        );
    }

    public boolean handlePopupIfPresent(PrinterConfigState returnToState) {
        try {
            if (!isPopupPresent()) {
                return true;
            }

            logger.info("Handling popup in state: {}", currentState);
            
            systmOneWindow.type(Key.ESC);
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
            
            wasPopupHandled = true;
            lastPopupTime = System.currentTimeMillis();
            
            cleanupCurrentState();
            return returnToState(returnToState);
            
        } catch (Exception e) {
            logger.error("Error handling popup: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPopupPresent() {
        try {
            long startTime = System.currentTimeMillis();
            iconRegion.find(popupPattern);
            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Popup check completed in {} ms", duration);
            return true;
            
        } catch (FindFailed e) {
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during popup detection", e);
            return false;
        }
    }

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

    public void updateState(PrinterConfigState newState) {
        this.currentState = newState;
    }

    public PrinterConfigState getCurrentState() {
        return currentState;
    }

    public RetryOperationHandler getRetryHandler() {
        return retryHandler;
    }

    public boolean wasRecentlyHandled(long withinMs) {
        if (!wasPopupHandled) {
            return false;
        }
        return (System.currentTimeMillis() - lastPopupTime) < withinMs;
    }

    public void resetState() {
        wasPopupHandled = false;
        lastPopupTime = 0;
        currentState = PrinterConfigState.DOCUMENT_SELECTION;
        logger.debug("Popup handler state reset completed");
    }
}