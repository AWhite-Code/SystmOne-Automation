package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.awt.Graphics2D;

import javax.imageio.ImageIO;

import systmone.automation.config.ApplicationConfig;

public class UiStateHandler {
    private static final Logger logger = LoggerFactory.getLogger(UiStateHandler.class);
    
    private final Region mainWindow;          // Added for popup handling
    private final Pattern selectionBorderPattern;  // Added for document verification
    private final Region uiRegion;
    private Robot robot;
    private PopupHandler popupHandler;
    
    // Scrollbar tracking state
    private Rectangle baselineThumbPosition;    // Position at start of document load
    private Region fixedScrollbarRegion;

    private boolean isTrackingStarted;         // Flag to indicate if we're actively tracking

    public UiStateHandler(Region uiRegion, Region mainWindow, Pattern selectionBorderPattern, PopupHandler popupHandler) {
        this.uiRegion = uiRegion;
        this.mainWindow = mainWindow;
        this.selectionBorderPattern = selectionBorderPattern;
        this.popupHandler = popupHandler;
        
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
     * Starts tracking a new document loading sequence by establishing baseline position
     */
    public boolean startDocumentTracking() {
        if (fixedScrollbarRegion == null) {
            logger.error("Cannot start tracking - scrollbar region not initialized");
            return false;
        }
    
        try {
            // First, let any ongoing UI updates settle
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            
            // Take multiple readings to ensure we have a stable starting position
            Rectangle position1 = findScrollbarThumb(fixedScrollbarRegion);
            Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
            Rectangle position2 = findScrollbarThumb(fixedScrollbarRegion);
            
            if (position1 == null || position2 == null) {
                logger.error("Failed to establish stable baseline position");
                return false;
            }
    
            // Verify positions are stable before setting baseline
            if (position1.y != position2.y) {
                logger.warn("Unstable thumb position detected: y1={}, y2={}", 
                    position1.y, position2.y);
                return false;
            }
    
            baselineThumbPosition = position1;
            isTrackingStarted = true;
            
            logger.info("Document tracking started - stable baseline position y={}", 
                baselineThumbPosition.y);
            return true;
            
        } catch (Exception e) {
            logger.error("Error starting document tracking: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Initializes scrollbar tracking using color detection
     */
    public boolean initializeScrollbarTracking(Pattern selectionPattern) {
        try {
            Match selectionMatch = uiRegion.exists(selectionPattern);
            if (selectionMatch == null) {
                logger.error("Could not find selection pattern");
                return false;
            }
    
            // Constants for scrollbar dimensions and positioning
            final int SCROLLBAR_WIDTH = 18;        // Standard Windows scrollbar width
            final int SCROLLBAR_HEIGHT = 600;      // Height for 1920x1080
            final int UPWARD_PADDING = 15;         // Look slightly above to catch the ^ arrow above scrollbar
            final int DOWNWARD_PADDING = 150;      // Extra space for scrolling down
            
            // Initial search region to find the thumb
            Region searchRegion = new Region(
                selectionMatch.x + 940 - selectionMatch.x,
                selectionMatch.y - UPWARD_PADDING, 
                SCROLLBAR_WIDTH,
                SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING 
            );
            
            logger.info("Setting up initial search region: y={} to {}", 
                searchRegion.y, searchRegion.y + searchRegion.h);
            
                Rectangle thumbBounds = findScrollbarThumb(searchRegion);
                if (thumbBounds != null) {
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
            // Take a screenshot of the full search region
            BufferedImage screenshot = robot.createScreenCapture(new Rectangle(
                searchRegion.x,
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            ));
    
            // Save the screenshot with timestamp and position info
            saveDebugScreenshot(screenshot, searchRegion, "original");
    
            // Create a marked version of the screenshot to show what we're detecting
            // BufferedImage markedScreenshot = deepCopy(screenshot);
            // Graphics2D g2d = markedScreenshot.createGraphics();
            // g2d.setColor(Color.RED);
    
            // Single pass scan for the thumb
            int centerX = screenshot.getWidth() / 2;
            int currentRun = 0;
            int longestRun = 0;
            int bestStart = -1;
            int runStart = -1;
    
            for (int y = 0; y < screenshot.getHeight(); y++) {
                Color pixelColor = new Color(screenshot.getRGB(centerX, y));
                
                if (isScrollbarColor(pixelColor)) {
                    if (currentRun == 0) {
                        runStart = y;
                    }
                    currentRun++;
                    
                    // Mark matching pixels in our debug image
                    // g2d.drawLine(0, y, screenshot.getWidth(), y);
                    
                    if (currentRun > longestRun) {
                        longestRun = currentRun;
                        bestStart = runStart;
                    }
                } else {
                    currentRun = 0;
                }
            }
    
            // g2d.dispose();
            
            // Save the marked version showing what we detected
            // saveDebugScreenshot(markedScreenshot, searchRegion, "detected");
    
            // If we found a valid thumb (using minimum height check)
            if (longestRun >= 15) {
                Rectangle thumbBounds = new Rectangle(
                    searchRegion.x,
                    searchRegion.y + bestStart,
                    searchRegion.w,
                    longestRun
                );
                
                logger.info("Found thumb at y={} with height={} in search region y={} to {}", 
                    thumbBounds.y, longestRun, searchRegion.y, searchRegion.y + searchRegion.h);
                
                return thumbBounds;
            }
    
            logger.warn("No valid thumb found in region y={} to {}", 
                searchRegion.y, searchRegion.y + searchRegion.h);
            return null;
            
        } catch (Exception e) {
            logger.error("Error finding scrollbar thumb: {}", e.getMessage());
            return null;
        }
    }
    
    // Helper method to save debug screenshots
    private void saveDebugScreenshot(BufferedImage screenshot, Region searchRegion, String type) {
        try {
            // Create a debug directory if it doesn't exist
            File debugDir = new File("debug/scrollbar");
            debugDir.mkdirs();
            
            // Create filename with timestamp and position info
            String filename = String.format("thumb_scan_%d_%s_y%d-h%d.png",
                System.currentTimeMillis(),
                type,
                searchRegion.y,
                searchRegion.h);
            
            File outputFile = new File(debugDir, filename);
            ImageIO.write(screenshot, "png", outputFile);
            
            logger.debug("Saved {} debug screenshot: {}", type, outputFile.getPath());
            
        } catch (IOException e) {
            logger.error("Failed to save debug screenshot: {}", e.getMessage());
        }
    }
    
    // Helper method for debug screenshots
    /** private void saveDebugScreenshot(BufferedImage screenshot, int x, int y) {
        try {
            File outputfile = new File(String.format("debug/thumb_scan_%d.png",
                System.currentTimeMillis()));
            outputfile.getParentFile().mkdirs();
            ImageIO.write(screenshot, "png", outputfile);
            logger.debug("Saved scan region screenshot: x={}, y={}, w={}, h={}",
                x, y, screenshot.getWidth(), screenshot.getHeight());
        } catch (IOException e) {
            logger.error("Failed to save debug screenshot", e);
        }
    }
    */

    /**
     * Verifies that the document has fully loaded by monitoring scrollbar movement
    */
    public boolean verifyDocumentLoaded(double timeout) {
        if (!isTrackingStarted) {
            logger.error("Document tracking not started");
            return false;
        }
    
        try {
            // Initial delay after navigation - this is crucial!
            // Program needs to give time for:
            // 1. Save dialog to fully close
            // 2. UI to update with new document
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS * 2);
            
            long startTime = System.currentTimeMillis();
            long timeoutMs = (long)(timeout * 1000);
            int consecutiveMatchCount = 0;
            Rectangle lastPosition = null;
            Rectangle initialBaseline = baselineThumbPosition;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // Quick popup check only when needed
                if (consecutiveMatchCount == 0 && popupHandler.isPopupPresent()) {
                    popupHandler.dismissPopup(false);
                    // After dismissing popup, give UI time to stabilize
                    Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    continue;
                }
    
                // If we can't find the thumb, wait a moment before retrying
                // This helps if the UI is still updating
                Rectangle newPosition = findScrollbarThumb(fixedScrollbarRegion);
                if (newPosition == null) {
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                    continue;
                }
    
                int movement = newPosition.y - initialBaseline.y;
                if (movement > 0) {
                    if (lastPosition != null && lastPosition.y == newPosition.y) {
                        consecutiveMatchCount++;
                        if (consecutiveMatchCount >= 2) {
                            isTrackingStarted = false;
                            return true;
                        }
                    } else {
                        consecutiveMatchCount = 1;
                    }
                    lastPosition = newPosition;
                    
                    // Small delay between position checks to let UI settle
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS / 2);
                } else {
                    // If no movement detected, give the system a bit more time
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                }
            }
    
            return false;
    
        } catch (Exception e) {
            logger.error("Verification error: {}", e.getMessage());
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
        boolean isMatch = isMatchingColor(color, ApplicationConfig.SCROLLBAR_DEFAULT) ||
                        isMatchingColor(color, ApplicationConfig.SCROLLBAR_HOVER) ||
                        isMatchingColor(color, ApplicationConfig.SCROLLBAR_SELECTED);
        
        if (isMatch) {
            logger.trace("Color match found: RGB({},{},{})", 
                color.getRed(), color.getGreen(), color.getBlue());
        }
        
        return isMatch;
    }

    private boolean isMatchingColor(Color c1, Color target) {
        boolean matches = Math.abs(c1.getRed() - target.getRed()) <= ApplicationConfig.COLOR_TOLERANCE &&
                        Math.abs(c1.getGreen() - target.getGreen()) <= ApplicationConfig.COLOR_TOLERANCE &&
                        Math.abs(c1.getBlue() - target.getBlue()) <= ApplicationConfig.COLOR_TOLERANCE;
        
        if (matches) {
            logger.trace("Color match: Source RGB({},{},{}) matches target RGB({},{},{})", 
                c1.getRed(), c1.getGreen(), c1.getBlue(),
                target.getRed(), target.getGreen(), target.getBlue());
        }
        
        return matches;
    }
}