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

/**
 * Handles UI state verification and stability checking for the SystmOne document processing system.
 * This class is responsible for monitoring and validating UI elements, particularly focusing on
 * scrollbar tracking and document loading states. It provides mechanisms to ensure UI elements
 * are stable and properly loaded before processing continues.
 * 
 * The handler uses color detection and pattern matching to track scrollbar movement,
 * which serves as an indicator of document loading completion. It includes comprehensive
 * debugging capabilities through screenshot capture and analysis.
 */
public class UiStateHandler {
    private static final Logger logger = LoggerFactory.getLogger(UiStateHandler.class);
    
    private final Region uiRegion;
    private int scrollbarX;  // Stores the center X coordinate of the scrollbar
    private Robot robot;
    private PopupHandler popupHandler;
    
    // Scrollbar tracking state
    private Rectangle baselineThumbPosition;    // Position at start of document load
    private Region fixedScrollbarRegion;        // Region where scrollbar movement is tracked
    private boolean isTrackingStarted;          // Flag to indicate if tracking is active

    /**
     * Initializes a new UI state handler with the specified region and popup handler.
     * 
     * @param uiRegion The region of the UI to monitor for state changes
     * @param popupHandler Handler for managing popup dialogs
     * @throws RuntimeException if Robot initialization fails
     */
    public UiStateHandler(Region uiRegion, PopupHandler popupHandler) {
        this.uiRegion = uiRegion;
        this.popupHandler = popupHandler;
        
        try {
            this.robot = new Robot();
        } catch (Exception e) {
            logger.error("Failed to initialize Robot for color detection", e);
            throw new RuntimeException("Failed to initialize Robot", e);
        }
    }

    
    /**
     * Waits for a UI element to appear and remain stable in its position.
     * An element is considered stable when it maintains the same position
     * for a specified number of consecutive checks.
     * 
     * @param pattern The Sikuli pattern to search for in the UI region
     * @param timeout Maximum time in seconds to wait for element stability
     * @return Match object if element is found and stable, null otherwise
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
                if (stabilityCount < ApplicationConfig.REQUIRED_STABILITY_COUNT) {
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        
        return null;
    }

    /**
     * Starts tracking a new document loading sequence by establishing a baseline
     * position for the scrollbar thumb. This baseline is used to detect document
     * loading completion through scrollbar movement.
     * 
     * @return true if tracking started successfully, false if initialization failed
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
     * Initializes scrollbar tracking using color detection within a specified region.
     * Establishes the scrollbar location based on a provided selection pattern and
     * sets up the tracking region for monitoring document loading.
     * 
     * @param selectionPattern Pattern used to locate the initial position
     * @return true if scrollbar tracking was successfully initialized, false otherwise
     */
    public boolean initializeScrollbarTracking(Pattern selectionPattern) {
        try {
            // Locate initial selection position
            Match selectionMatch = uiRegion.exists(selectionPattern);
            if (selectionMatch == null) {
                logger.error("Could not find selection pattern");
                return false;
            }

            // Calculate scrollbar dimensions and position
            final int SCROLLBAR_OFFSET = ApplicationConfig.SCROLLBAR_OFFSET_X;
            final int SCROLLBAR_WIDTH = ApplicationConfig.SCROLLBAR_WIDTH;
            final int SCROLLBAR_HEIGHT = 600;    // Standard height for 1920x1080
            final int UPWARD_PADDING = ApplicationConfig.SEARCH_RANGE_ABOVE;
            final int DOWNWARD_PADDING = ApplicationConfig.SEARCH_RANGE_BELOW;
            
            // Calculate scrollbar center position
            int scrollbarCenterX = selectionMatch.x + SCROLLBAR_OFFSET + (SCROLLBAR_WIDTH / 2);
            
            // Define initial search region for thumb
            Region searchRegion = new Region(
                scrollbarCenterX - (SCROLLBAR_WIDTH / 2),
                selectionMatch.y - UPWARD_PADDING, 
                SCROLLBAR_WIDTH,
                SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING 
            );
            
            logger.info("Setting up initial search region: x={}, y={} to {}", 
                scrollbarCenterX, searchRegion.y, searchRegion.y + searchRegion.h);
            
            // Find and validate scrollbar thumb position
            Rectangle thumbBounds = findScrollbarThumb(searchRegion);
            if (thumbBounds != null) {
                // Establish ongoing tracking region
                fixedScrollbarRegion = new Region(
                    scrollbarCenterX - (SCROLLBAR_WIDTH / 2),
                    thumbBounds.y - UPWARD_PADDING,
                    SCROLLBAR_WIDTH,
                    SCROLLBAR_HEIGHT + UPWARD_PADDING + DOWNWARD_PADDING
                );
                
                scrollbarX = scrollbarCenterX;
                
                logger.info("Initialized scrollbar tracking: centerX={}, thumb at y={}, region y={} to {}, height={}", 
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
            long startTime = System.currentTimeMillis();
            long timeoutMs = (long)(timeout * 1000);
            Rectangle initialBaseline = baselineThumbPosition;
            int loopCount = 0;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                loopCount++;
                long loopStartTime = System.currentTimeMillis();
                
                // Quick popup check
                if (popupHandler.isPopupPresent()) {
                    logger.debug("Loop {}: Popup check at {}", loopCount, loopStartTime);
                    popupHandler.dismissPopup(false);
                    continue;
                }
    
                // Find thumb position
                Rectangle newPosition = findScrollbarThumb(fixedScrollbarRegion);
                if (newPosition == null) {
                    logger.debug("Loop {}: No thumb found at {}", loopCount, System.currentTimeMillis());
                    Thread.sleep(5);
                    continue;
                }
    
                int movement = newPosition.y - initialBaseline.y;
                logger.debug("Loop {}: Movement {} pixels at {}", loopCount, movement, System.currentTimeMillis());
                
                if (movement > 0) {
                    // Make one additional check to verify movement
                    Thread.sleep(5);
                    Rectangle confirmPosition = findScrollbarThumb(fixedScrollbarRegion);
                    if (confirmPosition != null && confirmPosition.y > initialBaseline.y) {
                        long totalTime = System.currentTimeMillis() - startTime;
                        logger.info("Document load verification took: {} ms after {} loops", totalTime, loopCount);
                        isTrackingStarted = false;
                        return true;
                    }
                    logger.debug("Loop {}: Movement verification failed at {}", loopCount, System.currentTimeMillis());
                }
    
                long loopTime = System.currentTimeMillis() - loopStartTime;
                logger.debug("Loop {}: Took {} ms", loopCount, loopTime);
            }
    
            logger.warn("Document load verification timed out after {} ms and {} loops", 
                System.currentTimeMillis() - startTime, loopCount);
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

    public Match quickMatchCheck(Pattern pattern) {
        return uiRegion.exists(pattern);
    }
}