package systmone.automation.core;

import systmone.automation.ui.SystmOneAutomator;
import systmone.automation.ui.UiStateHandler;

/**
 * Represents the complete set of initialized components required for the automation system.
 * This class ensures all necessary components are present and valid before the system starts.
 * It acts as a container for passing related components together through the application.
 */
public class SystemComponents {
    private final SystmOneAutomator automator;
    private final UiStateHandler uiHandler;
    private final String outputFolder;

    /**
     * Creates a new SystemComponents instance with all required components.
     * Validates that no component is null to ensure system integrity.
     * 
     * @param automator The initialized SystmOne automator
     * @param uiHandler The UI state handler
     * @param outputFolder The path to the output folder
     * @throws IllegalArgumentException if any component is null
     */
    public SystemComponents(SystmOneAutomator automator, UiStateHandler uiHandler, String outputFolder) {
        // Validate all components are present
        if (automator == null) {
            throw new IllegalArgumentException("Automator cannot be null");
        }
        if (uiHandler == null) {
            throw new IllegalArgumentException("UI Handler cannot be null");
        }
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder path cannot be null or empty");
        }

        this.automator = automator;
        this.uiHandler = uiHandler;
        this.outputFolder = outputFolder;
    }

    /**
     * @return The SystmOne automator component
     */
    public SystmOneAutomator getAutomator() {
        return automator;
    }

    /**
     * @return The UI state handler component
     */
    public UiStateHandler getUiHandler() {
        return uiHandler;
    }

    /**
     * @return The output folder path
     */
    public String getOutputFolder() {
        return outputFolder;
    }
}