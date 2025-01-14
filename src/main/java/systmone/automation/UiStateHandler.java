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
            BufferedImage screenshot = robot.createScreenCapture(new Rectangle(
                searchRegion.x,
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            ));
    
            int centerX = screenshot.getWidth() / 2;
            
            // Track both the raw image position and best match position
            int bestMatchStart = -1;
            int bestMatchEnd = -1;
            int longestRun = 0;
            int currentRun = 0;
            int runStart = -1;
            
            // Scan for the longest continuous run of scrollbar color
            for (int y = 0; y < screenshot.getHeight(); y++) {
                Color pixelColor = new Color(screenshot.getRGB(centerX, y));
                
                if (isScrollbarColor(pixelColor)) {
                    if (currentRun == 0) {
                        runStart = y;
                    }
                    currentRun++;
                } else {
                    if (currentRun > longestRun) {
                        longestRun = currentRun;
                        bestMatchStart = runStart;
                        bestMatchEnd = y - 1;
                    }
                    currentRun = 0;
                }
            }
            
            // Check final run
            if (currentRun > longestRun) {
                bestMatchStart = runStart;
                bestMatchEnd = screenshot.getHeight() - 1;
            }
            
            // If we found a thumb (minimum 3 pixels high)
            if (bestMatchStart != -1 && (bestMatchEnd - bestMatchStart) >= 2) {
                // Calculate absolute screen coordinates
                int screenY = searchRegion.y + bestMatchStart;
                int height = bestMatchEnd - bestMatchStart + 1;
                
                Rectangle thumbBounds = new Rectangle(
                    searchRegion.x,
                    screenY,
                    searchRegion.w,
                    height
                );
                
                logger.info("Found thumb: image coordinates y={}->{}), screen coordinates y={}->{}",
                    bestMatchStart, bestMatchEnd,
                    screenY, screenY + height);
                    
                return thumbBounds;
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Error finding scrollbar thumb: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Verifies that the document has fully loaded by monitoring scrollbar movement
     */
    public boolean verifyDocumentLoaded(double timeout) {
        try {
            if (lastKnownThumbBounds == null) {
                logger.error("Scrollbar tracking not initialized");
                return false;
            }
    
            // Get current position
            Rectangle currentPosition = findScrollbarThumb(fixedScrollbarRegion);
            if (currentPosition == null) {
                logger.error("Could not find current thumb position");
                return false;
            }
    
            // Calculate movement from initial position
            int movement = currentPosition.y - lastKnownThumbBounds.y;
            
            logger.info("Comparing positions - initial: y={}, current: y={}, movement: {} pixels", 
                lastKnownThumbBounds.y, currentPosition.y, movement);
    
            if (movement > 0) {
                // Any downward movement indicates document has moved up
                logger.info("Detected downward thumb movement - document ready");
                lastKnownThumbBounds = currentPosition;  // Update for next check
                return true;
            } else if (movement < 0) {
                // Upward movement is unexpected - log warning and fall back to standard verification
                logger.warn("Unexpected upward thumb movement detected ({} pixels) - falling back to standard verification", 
                    movement);
                return false;
            }
    
            // If we get here, no movement was detected
            logger.debug("No thumb movement detected at position y={}", currentPosition.y);
            return false;
    
        } catch (Exception e) {
            logger.error("Error in verification: {}", e.getMessage());
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