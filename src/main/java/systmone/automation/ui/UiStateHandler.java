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
    private int scrollbarX;  // Stores the center X coordinate of the scrollbar
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
            final int SCROLLBAR_OFFSET = 940;    // Distance from selection to scrollbar
            final int SCROLLBAR_WIDTH = 18;      // Standard Windows scrollbar width
            final int SCROLLBAR_HEIGHT = 600;    // Height for 1920x1080
            final int UPWARD_PADDING = 15;       // Look slightly above to catch the ^ arrow
            final int DOWNWARD_PADDING = 150;    // Extra space for scrolling down
            
            // Calculate the exact center of where we expect the scrollbar
            int scrollbarCenterX = selectionMatch.x + SCROLLBAR_OFFSET + (SCROLLBAR_WIDTH / 2);
            
            // Initial search region to find the thumb
            Region searchRegion = new Region(
                scrollbarCenterX - (SCROLLBAR_WIDTH / 2),  // Position region around our center point
                selectionMatch.y - UPWARD_PADDING, 
                SCROLLBAR_WIDTH,
                SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING 
            );
            
            logger.info("Setting up initial search region: x={}, y={} to {}", 
                scrollbarCenterX, searchRegion.y, searchRegion.y + searchRegion.h);
            
            Rectangle thumbBounds = findScrollbarThumb(searchRegion);
            if (thumbBounds != null) {
                // Create our ongoing tracking region centered on our known good X coordinate
                fixedScrollbarRegion = new Region(
                    scrollbarCenterX - (SCROLLBAR_WIDTH / 2),  // Keep same center point
                    thumbBounds.y - UPWARD_PADDING,
                    SCROLLBAR_WIDTH,
                    SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING
                );
                
                // Store the center X coordinate for future scans
                scrollbarX = scrollbarCenterX;
                
                logger.info("Initialized scrollbar tracking: centerX={}, thumb at y={}, search region y={} to {}, height={}", 
                    scrollbarX,
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
        long startTime = System.currentTimeMillis();
        try {
            // Use stored scrollbarX if available, otherwise calculate from region
            int scanX = scrollbarX > 0 ? scrollbarX : searchRegion.x + (searchRegion.w / 2);
            logger.info("Scanning at x={} from y={} to {}", 
                scanX, searchRegion.y, searchRegion.y + searchRegion.h);
     
            // Capture single pixel strip for scanning
            BufferedImage singlePixel = robot.createScreenCapture(new Rectangle(
                scanX,
                searchRegion.y,
                1,
                searchRegion.h
            ));
            long captureTime = System.currentTimeMillis() - startTime;
            logger.info("Screenshot capture took: {} ms", captureTime);
            logger.info("Actual captured image dimensions: {}x{}", 
                singlePixel.getWidth(), singlePixel.getHeight());
     
            // Debug image processing start
            long debugStart = System.currentTimeMillis();
            BufferedImage fullScreenshot = robot.createScreenCapture(new Rectangle(
                scanX - (searchRegion.w / 2),
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            ));
            saveDebugScreenshot(fullScreenshot, searchRegion, "original");
     
            BufferedImage markedScreenshot = new BufferedImage(
                searchRegion.w,
                singlePixel.getHeight(),
                BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g2d = markedScreenshot.createGraphics();
            g2d.drawImage(fullScreenshot, 0, 0, null);
            g2d.setColor(Color.RED);
     
            // Pixel processing start
            long processStart = System.currentTimeMillis();
            int currentRun = 0;
            int longestRun = 0;
            int bestStart = -1;
            int runStart = -1;
     
            for (int y = 0; y < singlePixel.getHeight(); y++) {
                Color pixelColor = new Color(singlePixel.getRGB(0, y));
                
                if (isScrollbarColor(pixelColor)) {
                    if (currentRun == 0) {
                        runStart = y;
                    }
                    currentRun++;
                    g2d.drawLine(0, y, markedScreenshot.getWidth(), y);
                    
                    if (currentRun > longestRun) {
                        longestRun = currentRun;
                        bestStart = runStart;
                    }
                } else {
                    currentRun = 0;
                }
            }
            long processTime = System.currentTimeMillis() - processStart;
            logger.info("Pixel processing took: {} ms", processTime);
     
            // Finish debug image
            g2d.dispose();
            saveDebugScreenshot(markedScreenshot, searchRegion, "detected");
            long debugTime = System.currentTimeMillis() - debugStart;
            logger.info("Debug image processing took: {} ms", debugTime);
     
            logger.info("Longest run found: {} pixels starting at y={}", longestRun, bestStart);
     
            if (longestRun >= 3) {
                Rectangle thumbBounds = new Rectangle(
                    scanX,
                    searchRegion.y + bestStart,
                    1,
                    longestRun
                );
                
                logger.info("Found thumb at x={}, y={}, height={}", 
                    scanX, thumbBounds.y, longestRun);
                
                return thumbBounds;
            }
     
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