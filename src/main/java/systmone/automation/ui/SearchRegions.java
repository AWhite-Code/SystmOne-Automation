// SearchRegions.java in ui package
package systmone.automation.ui;

import org.sikuli.script.Region;
import systmone.automation.config.RegionConstants;

/**
 * Creates and manages specific screen regions where the application expects
 * to find UI elements. Each region is calculated based on the main window's
 * dimensions and the proportional constants defined in RegionConstants.
 */
public class SearchRegions {
    private final Region window;
    private final Region documentCountRegion;
    private final Region selectionBorderRegion;

    public SearchRegions(Region window) {
        this.window = window;
        int windowWidth = window.w;
        int windowHeight = window.h;
        
        this.documentCountRegion = createDocumentCountRegion(window, windowWidth, windowHeight);
        this.selectionBorderRegion = createSelectionBorderRegion(window, windowWidth, windowHeight);
    }

    private Region createDocumentCountRegion(Region window, int width, int height) {
        return new Region(
            window.x,  // Start from left edge
            window.y + (int)(height * RegionConstants.DOCUMENT_COUNT_Y),
            (int)(width * RegionConstants.DOCUMENT_COUNT_WIDTH),
            (int)(height * (1.0 - RegionConstants.DOCUMENT_COUNT_Y))  // From Y position to bottom
        );
    }

    private Region createSelectionBorderRegion(Region window, int width, int height) {
        return new Region(
            window.x,  // Start from left edge
            window.y + (int)(height * RegionConstants.SELECTION_BORDER_Y),
            (int)(width * RegionConstants.SELECTION_BORDER_WIDTH),
            (int)(height * (1.0 - RegionConstants.SELECTION_BORDER_Y))  // From Y position to bottom
        );
    }

    // Getters

    public Region getDocumentCountRegion() { return documentCountRegion; }
    public Region getSelectionBorderRegion() { return selectionBorderRegion; }
    public Region getFullWindow() { return window; }
}