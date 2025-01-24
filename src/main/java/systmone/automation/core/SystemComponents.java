package systmone.automation.core;

import systmone.automation.ui.PopupHandler;
import systmone.automation.ui.SystmOneAutomator;
import systmone.automation.ui.UiStateHandler;

import org.sikuli.script.Region;
/**
 * Represents the complete set of initialized components required for the automation system.
 * This class ensures all necessary components are present and valid before the system starts.
 * It acts as a container for passing related components together through the application.
 * 
 * The components are initialized in a specific order to handle dependencies:
 * 1. SystmOneAutomator - Core automation functionality
 * 2. PopupHandler - Manages popup detection and handling
 * 3. UiStateHandler - Manages UI state and verification
 * 
 * This ordering ensures each component has access to its required dependencies while
 * avoiding circular references.
 */
public class SystemComponents {
    private final SystmOneAutomator automator;
    private final UiStateHandler uiHandler;
    private final PopupHandler popupHandler;
    private final String outputFolder;

    /**
     * Creates a new SystemComponents instance with proper component initialization order.
     * Components are created here rather than passed in to ensure correct dependency flow.
     * 
     * @param automator The initialized SystmOne automator
     * @param outputFolder The path to the output folder
     * @throws IllegalArgumentException if any parameter is null
     */
    public SystemComponents(SystmOneAutomator automator, String outputFolder) {
        if (automator == null) {
            throw new IllegalArgumentException("Automator cannot be null");
        }
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder path cannot be null or empty");
        }

        // Get the window region once - this prevents multiple calls to getWindow()
        Region mainWindow = automator.getWindow();
        if (mainWindow == null) {
            throw new IllegalArgumentException("Could not get main window region from automator");
        }

        // Store primary components
        this.automator = automator;
        this.outputFolder = outputFolder;
        
        // Create components in correct order, using the pre-fetched window
        this.popupHandler = createPopupHandler(mainWindow);
        this.uiHandler = createUiStateHandler(mainWindow, this.popupHandler);
    }

    private PopupHandler createPopupHandler(Region mainWindow) {
        return new PopupHandler(mainWindow);
    }

    private UiStateHandler createUiStateHandler(Region mainWindow, PopupHandler popupHandler) {
        return new UiStateHandler(
            mainWindow,     // Main UI region for monitoring
            popupHandler    // Handler for popup dialogs
        );
    }

    /**
     * Returns the PopupHandler component responsible for detecting and managing
     * system popups during automation.

     * @return The popup handler component
     */
    public PopupHandler getPopupHandler() {
        return popupHandler;
    }

    /**
     * Returns the SystmOne automator component that handles core automation
     * interactions with the SystmOne application.
     * 
     * @return The SystmOne automator component
     */
    public SystmOneAutomator getAutomator() {
        return automator;
    }

    /**
     * Returns the UI state handler component that manages UI state verification
     * and stability checking.
     * 
     * @return The UI state handler component
     */
    public UiStateHandler getUiHandler() {
        return uiHandler;
    }

    /**
     * Returns the configured output folder path where processed documents
     * will be saved.
     * 
     * @return The output folder path
     */
    public String getOutputFolder() {
        return outputFolder;
    }
}