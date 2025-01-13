package systmone.automation;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.awt.Graphics2D;

import javax.imageio.ImageIO;

import systmone.automation.config.ApplicationConfig;

public class UiStateHandler {
    private static final Logger logger = LoggerFactory.getLogger(UiStateHandler.class);
    
    private final Region uiRegion;
    private Match lastKnownPosition;
    private Rectangle scrollbarBounds;
    private Robot robot;
    private Pattern selectionPattern;
    private Rectangle lastKnownThumbBounds;  // Store the last known position of the thumb
    private Region fixedScrollbarRegion;
    private Integer initialThumbY;

    private Rectangle scrollbarRegion;
    private Integer lastThumbY;  // Track the last known Y position of the thumb

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
        this.selectionPattern = selectionPattern;
        
        try {
            Match selectionMatch = uiRegion.exists(selectionPattern);
            if (selectionMatch == null) {
                logger.error("Could not find selection pattern");
                return false;
            }
    
            // Constants for scrollbar dimensions and positioning
            final int SCROLLBAR_WIDTH = 18;        // Standard Windows scrollbar width
            final int SCROLLBAR_HEIGHT = 400;      // Height for 1920x1080
            final int UPWARD_PADDING = 20;         // Look slightly above for better detection
            final int DOWNWARD_PADDING = 100;      // Extra space for scrolling down
            
            // Initial search region to find the thumb
            Region searchRegion = new Region(
                selectionMatch.x + 940 - selectionMatch.x,
                selectionMatch.y - UPWARD_PADDING,  // Small upward padding
                SCROLLBAR_WIDTH,
                SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING  // Total height plus padding
            );
            
            logger.info("Setting up initial search region: y={} to {}", 
                searchRegion.y, searchRegion.y + searchRegion.h);
            
            Rectangle thumbBounds = findScrollbarThumb(searchRegion);
            if (thumbBounds != null) {
                // Store both the initial position and the search region
                initialThumbY = thumbBounds.y;
                lastKnownThumbBounds = thumbBounds;
                
                // Create our ongoing tracking region
                fixedScrollbarRegion = new Region(
                    thumbBounds.x,
                    thumbBounds.y - UPWARD_PADDING,    // Maintain upward visibility
                    SCROLLBAR_WIDTH,
                    SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING
                );
                
                logger.info("Initialized scrollbar tracking: thumb at y={}, search region y={} to {}, height={}", 
                    thumbBounds.y,
                    fixedScrollbarRegion.y,
                    fixedScrollbarRegion.y + fixedScrollbarRegion.h,
                    fixedScrollbarRegion.h);
                    
                return true;
            }
    
            logger.error("Could not locate scrollbar thumb in expected region");
            return false;
    
        } catch (Exception e) {
            logger.error("Failed to initialize scrollbar tracking: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Finds the scrollbar thumb by color in the specified region
     */
    private Rectangle findScrollbarThumb(Region searchRegion) {
        try {
            // Get the screenshot
            Rectangle bounds = new Rectangle(
                searchRegion.x,
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            );
            
            BufferedImage screenshot = robot.createScreenCapture(bounds);
            
            // Calculate sampling line position
            int imageWidth = screenshot.getWidth();
            int centerX = imageWidth / 2;
            
            // Log exact sampling coordinates
            logger.debug("Sampling colors along vertical line at x={} in {}x{} image", 
                centerX, imageWidth, screenshot.getHeight());
            
            // Sample along the height of the image
            for (int imageY = 0; imageY < screenshot.getHeight(); imageY++) {
                Color pixelColor = new Color(screenshot.getRGB(centerX, imageY));
                
                if (isScrollbarColor(pixelColor)) {
                    // Convert image coordinates back to screen coordinates
                    int screenY = searchRegion.y + imageY;
                    
                    logger.debug("Found scrollbar color at image y={}, screen y={}", 
                        imageY, screenY);
                    
                    // Find edges in image coordinates
                    int thumbTopImage = imageY;
                    int thumbBottomImage = imageY;
                    
                    // Scan up (in image coordinates)
                    for (int scanY = imageY - 1; scanY >= 0; scanY--) {
                        Color scanColor = new Color(screenshot.getRGB(centerX, scanY));
                        if (!isScrollbarColor(scanColor)) {
                            break;
                        }
                        thumbTopImage = scanY;
                    }
                    
                    // Scan down (in image coordinates)
                    for (int scanY = imageY + 1; scanY < screenshot.getHeight(); scanY++) {
                        Color scanColor = new Color(screenshot.getRGB(centerX, scanY));
                        if (!isScrollbarColor(scanColor)) {
                            break;
                        }
                        thumbBottomImage = scanY;
                    }
                    
                    // Convert back to screen coordinates for the return value
                    Rectangle thumbBounds = new Rectangle(
                        searchRegion.x,
                        searchRegion.y + thumbTopImage,    // Convert to screen coordinates
                        searchRegion.w,
                        thumbBottomImage - thumbTopImage + 1
                    );
                    
                    logger.info("Found thumb: image coordinates y={}->{}), screen coordinates y={}->{}",
                        thumbTopImage, thumbBottomImage,
                        thumbBounds.y, thumbBounds.y + thumbBounds.height);
                        
                    return thumbBounds;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error finding scrollbar thumb: {}", e.getMessage());
        }
        return null;
    }
    
    // Helper method to find thumb edges
    private int findThumbEdge(BufferedImage img, int x, int y, boolean searchUp) {
        int edge = y;
        int step = searchUp ? -1 : 1;
        int limit = searchUp ? 0 : img.getHeight() - 1;
        
        while ((searchUp ? edge > limit : edge < limit)) {
            Color color = new Color(img.getRGB(x, edge + step));
            if (!isScrollbarColor(color)) {
                break;
            }
            edge += step;
        }
        return edge;
    }
    
    /**
     * Verifies that the document has fully loaded by monitoring scrollbar movement
     */
    public boolean verifyDocumentLoaded(double timeout) {
        try {
            if (lastKnownThumbBounds == null || fixedScrollbarRegion == null) {
                logger.error("Scrollbar tracking not properly initialized");
                return false;
            }
    
            long startTime = System.currentTimeMillis();
            long timeoutMs = (long)(timeout * 1000);
            int retryCount = 0;
            int maxRetries = 10;
            
            // Add initial delay to let UI update
            Thread.sleep(100);  // Let the UI start moving
            
            Rectangle initialPosition = findScrollbarThumb(fixedScrollbarRegion);
            if (initialPosition == null) {
                logger.error("Could not find initial thumb position");
                return false;
            }
            
            logger.info("Starting position check - thumb at y={}", initialPosition.y);
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                Thread.sleep(200);  // Wait longer between checks
                
                Rectangle currentThumb = findScrollbarThumb(fixedScrollbarRegion);
                if (currentThumb != null) {
                    int movement = currentThumb.y - initialPosition.y;
                    
                    // Log every position check
                    logger.debug("Current position y={}, movement={} pixels", 
                        currentThumb.y, movement);
                    
                    if (Math.abs(movement) >= 3) {  // Require at least 3 pixels of movement
                        logger.info("Detected movement of {} pixels from {} to {}", 
                            movement, initialPosition.y, currentThumb.y);
                        lastKnownThumbBounds = currentThumb;  // Update for next check
                        return true;
                    }
                }
                
                if (++retryCount >= maxRetries) {
                    logger.warn("No movement detected after {} attempts", maxRetries);
                    return false;
                }
            }
            
            return false;
    
        } catch (Exception e) {
            logger.error("Error verifying document load: {}", e.getMessage());
            return false;
        }
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
}