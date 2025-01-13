package systmone.automation;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import systmone.automation.config.ApplicationConfig;

public class UiStateHandler {
    private static final Logger logger = LoggerFactory.getLogger(UiStateHandler.class);
    
    private final Region uiRegion;
    private Match lastKnownPosition;
    private Rectangle scrollbarBounds;
    private Robot robot;
    public UiStateHandler(Region uiRegion) {
        this.uiRegion = uiRegion;
        try {
            this.robot = new Robot();
        } catch (Exception e) {
            logger.error("Failed to initialize Robot for color detection", e);
        }
    }
    
    /**
     * Waits for a UI element to appear and remain stable in position.
     */
    public Match waitForStableElement(Pattern pattern, double timeout) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = (long)(timeout * 1000);
        Match lastMatch = null;
        int stabilityCount = 0;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Match currentMatch = uiRegion.exists(pattern);
                
                if (currentMatch == null) {
                    stabilityCount = 0;
                    lastMatch = null;
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                    continue;
                }
                
                if (isMatchStable(lastMatch, currentMatch)) {
                    stabilityCount++;
                    if (stabilityCount >= ApplicationConfig.REQUIRED_STABILITY_COUNT) {
                        lastKnownPosition = currentMatch;
                        return currentMatch;
                    }
                } else {
                    stabilityCount = 1;
                }
                
                lastMatch = currentMatch;
                Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Initializes scrollbar tracking using color detection
     */
    public boolean initializeScrollbarTracking(Pattern selectionPattern) {
        try {
            Match selectionMatch = uiRegion.exists(selectionPattern);
            if (selectionMatch == null) {
                return false;
            }
            
            // Define initial search region for scrollbar
            Region searchRegion = new Region(
                selectionMatch.x + 250,  // Adjust offset based on your UI
                selectionMatch.y,
                20,  // Typical scrollbar width
                uiRegion.h - selectionMatch.y
            );
            
            // Find initial thumb position
            Rectangle thumbBounds = findScrollbarThumb(searchRegion);
            if (thumbBounds != null) {
                // Store the full scrollbar region for future searches
                scrollbarBounds = new Rectangle(
                    thumbBounds.x,
                    searchRegion.y,
                    thumbBounds.width,
                    searchRegion.h
                );
                logger.info("Scrollbar tracking initialized successfully");
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize scrollbar tracking", e);
        }
        
        return false;
    }
    
    /**
     * Finds the scrollbar thumb by color in the specified region
     */
    private Rectangle findScrollbarThumb(Region searchRegion) {
        try {
            Rectangle bounds = new Rectangle(
                searchRegion.x,
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            );
            BufferedImage screenshot = robot.createScreenCapture(bounds);
            
            int thumbTop = -1;
            int thumbBottom = -1;
            
            // Scan down the center of the scrollbar
            int centerX = searchRegion.w / 2;
            for (int y = 0; y < screenshot.getHeight(); y++) {
                Color pixelColor = new Color(screenshot.getRGB(centerX, y));
                
                if (isScrollbarColor(pixelColor)) {
                    if (thumbTop == -1) {
                        thumbTop = y;
                        logger.trace("Found thumb top at y={}, color state: {}", 
                            y, getColorState(pixelColor));
                    }
                    thumbBottom = y;
                } else if (thumbTop != -1) {
                    // We've found the complete thumb
                    break;
                }
            }
            
            if (thumbTop != -1 && thumbBottom != -1) {
                return new Rectangle(
                    searchRegion.x,
                    searchRegion.y + thumbTop,
                    searchRegion.w,
                    thumbBottom - thumbTop + 1
                );
            }
            
        } catch (Exception e) {
            logger.error("Error finding scrollbar thumb by color", e);
        }
        
        return null;
    }
    
    /**
     * Verifies that the document has fully loaded by monitoring scrollbar movement
     */
    public boolean verifyDocumentLoaded(double timeout) {
        if (scrollbarBounds == null) {
            logger.warn("Scrollbar tracking not initialized");
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = (long)(timeout * 1000);
        Rectangle lastPosition = null;
        int stabilityCount = 0;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Region searchRegion = new Region(
                    scrollbarBounds.x,
                    scrollbarBounds.y,
                    scrollbarBounds.width,
                    scrollbarBounds.height
                );
                
                Rectangle currentPosition = findScrollbarThumb(searchRegion);
                
                if (currentPosition != null) {
                    if (lastPosition == null) {
                        lastPosition = currentPosition;
                    } else if (currentPosition.y != lastPosition.y) {
                        logger.debug("Thumb movement detected: {} -> {}", 
                            lastPosition.y, currentPosition.y);
                        
                        if (++stabilityCount >= ApplicationConfig.REQUIRED_STABILITY_COUNT) {
                            logger.info("Document load confirmed via scrollbar movement");
                            return true;
                        }
                    } else {
                        stabilityCount = 0;
                    }
                    
                    lastPosition = currentPosition;
                }
                
                Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        logger.warn("Timeout waiting for document load confirmation");
        return false;
    }
    
    private boolean isMatchStable(Match lastMatch, Match currentMatch) {
        if (lastMatch == null) {
            return false;
        }
        return currentMatch.x == lastMatch.x && currentMatch.y == lastMatch.y;
    }
    
    private boolean isScrollbarColor(Color color) {
        return isMatchingColor(color, ApplicationConfig.SCROLLBAR_DEFAULT) ||
               isMatchingColor(color, ApplicationConfig.SCROLLBAR_HOVER) ||
               isMatchingColor(color, ApplicationConfig.SCROLLBAR_SELECTED);
    }
    
    private boolean isMatchingColor(Color c1, Color target) {
        return Math.abs(c1.getRed() - target.getRed()) <= ApplicationConfig.COLOR_TOLERANCE &&
               Math.abs(c1.getGreen() - target.getGreen()) <= ApplicationConfig.COLOR_TOLERANCE &&
               Math.abs(c1.getBlue() - target.getBlue()) <= ApplicationConfig.COLOR_TOLERANCE;
    }
    
    private String getColorState(Color color) {
        if (isMatchingColor(color, ApplicationConfig.SCROLLBAR_DEFAULT)) return "Default";
        if (isMatchingColor(color, ApplicationConfig.SCROLLBAR_HOVER)) return "Hover";
        if (isMatchingColor(color, ApplicationConfig.SCROLLBAR_SELECTED)) return "Selected";
        return "Unknown";
    }
}