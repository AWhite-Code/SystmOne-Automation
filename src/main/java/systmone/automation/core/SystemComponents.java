package systmone.automation.core;

import systmone.automation.ui.PopupHandler;
import systmone.automation.ui.SystmOneAutomator;
import systmone.automation.ui.UiStateHandler;

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
        // Validate primary components
        if (automator == null) {
            throw new IllegalArgumentException("Automator cannot be null");
        }
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder path cannot be null or empty");
        }

        // Store primary components
        this.automator = automator;
        this.outputFolder = outputFolder;
        
        // Create components in correct order
        this.popupHandler = createPopupHandler();
        this.uiHandler = createUiStateHandler();
    }

    private PopupHandler createPopupHandler() {
        return new PopupHandler(this.automator.getWindow());
    }

    private UiStateHandler createUiStateHandler() {
        return new UiStateHandler(this.automator.getWindow(), this.popupHandler);
    }

    /**
     * Returns the PopupHandler component responsible for detecting and managing
     * system popups during automation.
     * 
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