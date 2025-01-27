// RegionConstants.java in config package
package systmone.automation.config;

/**
 * Defines constants for calculating UI search regions.
 * These values represent proportions of the total screen dimensions
 * and are used to restrict pattern matching to specific areas.
 */
public final class RegionConstants {
    // Prevent instantiation of constants class
    private RegionConstants() {}
    
    // Vertical divisions
    public static final double BOTTOM_FIFTH = 0.8;    // Start of bottom 1/5th
    public static final double BOTTOM_HALF = 0.5;     // Start of bottom half
    public static final double LOWER_THREE_FIFTHS = 0.4; // Start of lower 3/5ths

    // Horizontal divisions
    public static final double LEFT_FIFTH = 0.2;      // Width of leftmost 1/5th
    public static final double LEFT_THIRD = 0.33;     // Width of leftmost 1/3rd
}