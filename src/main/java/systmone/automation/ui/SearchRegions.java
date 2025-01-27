// SearchRegions.java in ui package
package systmone.automation.ui;

import org.sikuli.script.Region;
import systmone.automation.config.RegionConstants;

/**
 * Manages screen regions for UI element detection.
 * Calculates and provides access to specific regions where
 * the application should look for different UI elements,
 * improving performance and reliability of pattern matching.
 */
public class SearchRegions {
    private final Region window;
    private final Region documentCountRegion;
    private final Region printMenuRegion;
    private final Region selectionBorderRegion;

    public SearchRegions(Region window) {
        this.window = window;
        
        int windowWidth = window.w;
        int windowHeight = window.h;
        
        this.documentCountRegion = createDocumentCountRegion(window, windowWidth, windowHeight);
        this.printMenuRegion = createPrintMenuRegion(window, windowWidth, windowHeight);
        this.selectionBorderRegion = createSelectionBorderRegion(window, windowWidth, windowHeight);
    }

    private Region createDocumentCountRegion(Region window, int width, int height) {
        return new Region(
            window.x,
            window.y + (int)(height * RegionConstants.BOTTOM_FIFTH),
            (int)(width * RegionConstants.LEFT_FIFTH),
            (int)(height * (1.0 - RegionConstants.BOTTOM_FIFTH))
        );
    }

    private Region createPrintMenuRegion(Region window, int width, int height) {
        return new Region(
            window.x,
            window.y + (int)(height * RegionConstants.BOTTOM_HALF),
            (int)(width * RegionConstants.LEFT_THIRD),
            (int)(height * (1.0 - RegionConstants.BOTTOM_HALF))
        );
    }

    private Region createSelectionBorderRegion(Region window, int width, int height) {
        return new Region(
            window.x,
            window.y + (int)(height * RegionConstants.LOWER_THREE_FIFTHS),
            (int)(width * RegionConstants.LEFT_THIRD),
            (int)(height * (1.0 - RegionConstants.LOWER_THREE_FIFTHS))
        );
    }

    public Region getDocumentCountRegion() { return documentCountRegion; }
    public Region getPrintMenuRegion() { return printMenuRegion; }
    public Region getSelectionBorderRegion() { return selectionBorderRegion; }
    public Region getFullWindow() { return window; }
}