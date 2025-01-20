package systmone.automation.ui;

import java.awt.*;
import java.awt.event.InputEvent;
import systmone.automation.state.AutomationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PopupHandler {
    private static final Logger logger = LoggerFactory.getLogger(PopupHandler.class);
    private static final String POPUP_TITLE = "Question";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    private final Robot robot;
    private final UiStateHandler uiStateHandler;
    private AutomationState currentState;
    
    public PopupHandler(UiStateHandler uiStateHandler) throws AWTException {
        this.robot = new Robot();
        this.uiStateHandler = uiStateHandler;
    }
    
    // Main method to handle popup interruption
    public boolean handlePopupInterrupt(AutomationState state) {
        this.currentState = state;
        Dialog popup = findQuestionPopup();
        
        if (popup != null) {
            logger.info("Question popup detected during {}. Handling interrupt...", 
                state.getCurrentStage());
            
            // Create state snapshot before handling popup
            state.setInterrupted(true);
            
            try {
                // Handle the popup
                if (handlePopup(popup)) {
                    // Wait for main window to regain focus
                    robot.delay(1000);
                    
                    // Attempt to restore previous state
                    return restoreState();
                }
            } catch (Exception e) {
                logger.error("Error handling popup: {}", e.getMessage());
            }
        }
        return false;
    }
    
    private Dialog findQuestionPopup() {
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
            if (window instanceof Dialog && 
                POPUP_TITLE.equals(((Dialog) window).getTitle())) {
                return (Dialog) window;
            }
        }
        return null;
    }
    
    private boolean handlePopup(Dialog popup) {
        // Get popup bounds for clicking
        Rectangle bounds = popup.getBounds();
        Point buttonLocation = findOKButton(bounds);
        
        if (buttonLocation != null) {
            // Click the OK button
            robot.mouseMove(buttonLocation.x, buttonLocation.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            return true;
        }
        return false;
    }
    
    private Point findOKButton(Rectangle popupBounds) {
        // This would use pattern matching to find the OK button
        // For now, we'll use a simplified approach of looking at the bottom right
        return new Point(
            popupBounds.x + popupBounds.width - 70,
            popupBounds.y + popupBounds.height - 30
        );
    }
    
    private boolean restoreState() {
        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                switch (currentState.getCurrentStage()) {
                    case DOCUMENT_SELECTION:
                        return restoreDocumentSelection();
                    case CONTEXT_MENU_OPEN:
                        return restoreContextMenu();
                    case PRINT_DIALOG_OPEN:
                        return restorePrintDialog();
                    case SAVE_DIALOG_OPEN:
                        return restoreSaveDialog();
                    case DOCUMENT_SAVING:
                        return monitorSaveCompletion();
                    case NAVIGATION_PENDING:
                        return prepareForNavigation();
                }
            } catch (Exception e) {
                logger.warn("Restore attempt {} failed: {}", 
                    attempts + 1, e.getMessage());
                attempts++;
                robot.delay(1000);
            }
        }
        return false;
    }
    
    // State restoration methods for each stage
    private boolean restoreDocumentSelection() {
        // Re-select the current document
        // Implementation details would depend on your UI interaction methods
        return true;
    }
    
    private boolean restoreContextMenu() {
        // Re-open context menu
        return true;
    }
    
    private boolean restorePrintDialog() {
        // Re-open print dialog
        return true;
    }
    
    private boolean restoreSaveDialog() {
        // Re-open save dialog and set path
        return true;
    }
    
    private boolean monitorSaveCompletion() {
        // Check if save completed and handle accordingly
        return true;
    }
    
    private boolean prepareForNavigation() {
        // Prepare for navigation to next document
        return true;
    }
}