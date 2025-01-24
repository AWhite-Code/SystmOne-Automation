package systmone.automation.core;

import systmone.automation.ui.PopupHandler;
import systmone.automation.ui.SystmOneAutomator;
import systmone.automation.ui.UiStateHandler;

import org.sikuli.script.Region;

/**
 * Represents the complete set of initialized components required for the automation system.
 * This class acts as a dependency container and ensures all components are properly initialized
 * before system operation begins. It maintains strict validation of component dependencies and
 * manages their lifecycle.
 * 
 * Component Initialization Order:
 * 1. SystmOneAutomator - Provides core window automation capabilities
 * 2. PopupHandler - Manages system dialog detection and interaction
 * 3. UiStateHandler - Handles UI state verification and stability checking
 * 
 * Key Responsibilities:
 * - Validates all component dependencies during initialization
 * - Maintains references to core system components
 * - Provides controlled access to system components
 * - Ensures proper component initialization order
 * 
 * Dependencies:
 * - Requires an active SystmOne application window
 * - Requires a valid output folder path for document storage
 */
public class SystemComponents {
    // Core automation components
    private final SystmOneAutomator automator;
    private final UiStateHandler uiHandler;
    private final PopupHandler popupHandler;
    
    // System configuration
    private final String outputFolder;

    /**
     * Creates a new SystemComponents instance with the required components.
     * Components are initialized in a specific order to maintain proper dependency flow
     * and ensure system stability.
     * 
     * @param automator The initialized SystmOne automator instance
     * @param outputFolder The absolute path to the document output folder
     * @throws IllegalArgumentException if automator is null, output folder is null/empty,
     *         or if the main application window cannot be accessed
     */
    public SystemComponents(SystmOneAutomator automator, String outputFolder) {
        if (automator == null) {
            throw new IllegalArgumentException("Automator cannot be null");
        }
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder path cannot be null or empty");
        }

        // Cache the window region to prevent multiple lookups
        Region mainWindow = automator.getWindow();
        if (mainWindow == null) {
            throw new IllegalArgumentException("Could not get main window region from automator");
        }

        // Initialize core components
        this.automator = automator;
        this.outputFolder = outputFolder;
        
        // Create dependent components in order
        this.popupHandler = createPopupHandler(mainWindow);
        this.uiHandler = createUiStateHandler(mainWindow, this.popupHandler);
    }

    /**
     * Creates and initializes the popup handler component.
     * 
     * @param mainWindow The main application window region
     * @return Initialized PopupHandler instance
     */
    private PopupHandler createPopupHandler(Region mainWindow) {
        return new PopupHandler(mainWindow);
    }

    /**
     * Creates and initializes the UI state handler component with its dependencies.
     * 
     * @param mainWindow The main application window region
     * @param popupHandler The popup handler dependency
     * @return Initialized UiStateHandler instance
     */
    private UiStateHandler createUiStateHandler(Region mainWindow, PopupHandler popupHandler) {
        return new UiStateHandler(mainWindow, popupHandler);
    }

    /**
     * Provides access to the popup handling component.
     * 
     * @return The initialized PopupHandler instance
     */
    public PopupHandler getPopupHandler() {
        return popupHandler;
    }

    /**
     * Provides access to the core automation component.
     * 
     * @return The initialized SystmOneAutomator instance
     */
    public SystmOneAutomator getAutomator() {
        return automator;
    }

    /**
     * Provides access to the UI state management component.
     * 
     * @return The initialized UiStateHandler instance
     */
    public UiStateHandler getUiHandler() {
        return uiHandler;
    }

    /**
     * Provides access to the configured output folder path.
     * 
     * @return The absolute path to the document output folder
     */
    public String getOutputFolder() {
        return outputFolder;
    }
}