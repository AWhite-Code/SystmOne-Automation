// RegionConstants.java in config package
package systmone.automation.config;

/**
 * Defines proportional constants for UI element search regions.
 * These values represent where specific UI elements appear in the application window
 * as proportions of the total screen dimensions (0.0 to 1.0).
 * 
 * For vertical positions (Y), values indicate distance from top of window.
 * For horizontal positions (X), values indicate distance from left of window.
 * For widths/heights, values indicate proportion of total dimension.
 */
public final class RegionConstants {
    // Prevent instantiation of constants class
    private RegionConstants() {}
    
    // Document Count region constants
    public static final double DOCUMENT_COUNT_Y = 0.8;      // Starts 80% down from top
    public static final double DOCUMENT_COUNT_WIDTH = 0.2;  // Uses leftmost 20% of width
    
    // Print Menu region constants
    public static final double PRINT_MENU_Y = 0.25;         // Starts quarterway down screen
    public static final double PRINT_MENU_WIDTH = 0.33;    // Uses leftmost third of width
    
    // Selection Border region constants
    public static final double SELECTION_BORDER_Y = 0.15;    // Starts 15% down from top
    public static final double SELECTION_BORDER_WIDTH = 0.33; // Uses leftmost third of width
}