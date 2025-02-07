package systmone.automation.ui;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;

import systmone.automation.config.ApplicationConfig;
import systmone.automation.config.LoggingConfig;
import systmone.automation.state.UILoggingState;

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
    private final Region mainWindow;
    private final Pattern selectionBorderPattern;
    private final UILoggingState loggingState;

    /**
     * Initializes a new UI state handler with the specified region and popup handler.
     * 
     * @param uiRegion The region of the UI to monitor for state changes
     * @param popupHandler Handler for managing popup dialogs
     * @throws RuntimeException if Robot initialization fails
     */
    public UiStateHandler(Region uiRegion, Region mainWindow, Pattern selectionBorderPattern, 
    PopupHandler popupHandler) {
    this.uiRegion = uiRegion;
    this.mainWindow = mainWindow;
    this.selectionBorderPattern = selectionBorderPattern;
    this.popupHandler = popupHandler;
    this.loggingState = new UILoggingState(logger);

    try {
    this.robot = new Robot();
    } catch (Exception e) {
    logger.error("Failed to initialize Robot for color detection", e);
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
            if (position1 == null) {
                logger.error("Could not find initial thumb position");
                return false;
            }

            // Wait between readings to ensure stability
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            
            Rectangle position2 = findScrollbarThumb(fixedScrollbarRegion);
            if (position2 == null) {
                logger.error("Could not find confirmation thumb position");
                return false;
            }

            // Take a third reading for extra confidence
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            
            Rectangle position3 = findScrollbarThumb(fixedScrollbarRegion);
            if (position3 == null) {
                logger.error("Could not find final thumb position");
                return false;
            }

            // Verify all positions are stable
            if (position1.y != position2.y || position2.y != position3.y) {
                logger.warn("Unstable thumb positions detected: y1={}, y2={}, y3={}", 
                    position1.y, position2.y, position3.y);
                return false;
            }

            // If we got here, position is stable
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
     * Counts total documents by tracking UI element movement.
     * Uses scrollbar position or selection border movement to validate navigation.
     * 
     * @return Total number of documents found, or -1 if counting fails
     */
    public int determineDocumentCount() {
        boolean hasScrollbar = initializeScrollbarTracking(selectionBorderPattern);
        int documentCount = 1; // Start at 1 for current document
        int failedMoveAttempts = 0;
        Rectangle lastPosition = null;
        
        try {
            // Get initial selection border position
            Match initialMatch = quickMatchCheck(selectionBorderPattern);
            if (initialMatch == null) {
                logger.error("Could not find initial selection border");
                return -1;
            }
            lastPosition = new Rectangle(initialMatch.x, initialMatch.y, initialMatch.w, initialMatch.h);
            
            while (failedMoveAttempts < 3) { // Stop after 3 failed attempts
                // Attempt navigation
                mainWindow.type(Key.DOWN);
                Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                
                // Check for movement
                boolean moved = false;
                
                if (hasScrollbar) {
                    // Check scrollbar movement
                    Rectangle newThumbPos = findScrollbarThumb(fixedScrollbarRegion);
                    if (newThumbPos != null && (lastPosition == null || newThumbPos.y != lastPosition.y)) {
                        moved = true;
                        lastPosition = newThumbPos;
                    }
                }
                
                // Always verify with selection border
                Match currentMatch = quickMatchCheck(selectionBorderPattern);
                if (currentMatch != null && 
                    (currentMatch.y != lastPosition.y || currentMatch.x != lastPosition.x)) {
                    moved = true;
                    lastPosition = new Rectangle(currentMatch.x, currentMatch.y, currentMatch.w, currentMatch.h);
                }
                
                if (moved) {
                    documentCount++;
                    failedMoveAttempts = 0;
                } else {
                    failedMoveAttempts++;
                }
            }
            
            logger.info("Document count determined: {}", documentCount);
            return documentCount;
            
        } catch (Exception e) {
            logger.error("Error determining document count: {}", e.getMessage());
            return -1;
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
            final int SCROLLBAR_WIDTH = 18;
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
     * Locates the scrollbar thumb using color detection in a specified region.
     * Uses pixel-by-pixel color analysis to find the longest continuous run of
     * scrollbar-colored pixels, which indicates the thumb position.
     * 
     * @param searchRegion Region to search for the scrollbar thumb
     * @return Rectangle representing thumb bounds if found, null otherwise
     */
    private Rectangle findScrollbarThumb(Region searchRegion) {
        try {
            // Determine scan coordinates
            int scanX = scrollbarX > 0 ? scrollbarX : searchRegion.x + (searchRegion.w / 2);
            logger.debug("Starting scrollbar scan at x={}", scanX);
    
            // Capture vertical strip for analysis
            BufferedImage singlePixel = robot.createScreenCapture(new Rectangle(
                scanX,
                searchRegion.y,
                1,
                searchRegion.h
            ));
    
            // Only log dimension issues if they don't match expectations
            if (singlePixel.getHeight() != searchRegion.h || singlePixel.getWidth() != 1) {
                logger.warn("Unexpected image dimensions: {}x{}", 
                    singlePixel.getWidth(), singlePixel.getHeight());
            }
    
            // Create debug visualization
            BufferedImage fullScreenshot = robot.createScreenCapture(new Rectangle(
                scanX - (searchRegion.w / 2),
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            ));
    
            BufferedImage markedScreenshot = new BufferedImage(
                searchRegion.w,
                singlePixel.getHeight(),
                BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g2d = markedScreenshot.createGraphics();
            g2d.drawImage(fullScreenshot, 0, 0, null);
            g2d.setColor(Color.RED);
    
            // Process pixels to find thumb position
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
    
            // Only log run details if no valid thumb found or at debug level
            if (longestRun < ApplicationConfig.MIN_THUMB_MOVEMENT) {
                logger.debug("No valid thumb found - longest run {} pixels at y={}", 
                    longestRun, bestStart);
                return null;
            }
    
            Rectangle thumbBounds = new Rectangle(
                scanX,
                searchRegion.y + bestStart,
                1,
                longestRun
            );
            
            // Single informative log about the found position
            logger.info("Thumb detected at y={} (height={})", thumbBounds.y, longestRun);
            
            return thumbBounds;
            
        } catch (Exception e) {
            logger.error("Scrollbar scan failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verifies that a document has fully loaded by monitoring scrollbar movement.
     * Considers the document loaded when the scrollbar shows consistent downward movement.
     * 
     * @param timeout Maximum time in seconds to wait for document loading
     * @return true if document loading is confirmed, false if timeout or verification fails
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
            int noMovementCount = 0;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // Handle any popup dialogs
                if (popupHandler.isPopupPresent()) {
                    popupHandler.dismissPopup(false);
                    continue;
                }
    
                Rectangle newPosition = findScrollbarThumb(fixedScrollbarRegion);
                if (newPosition == null) {
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                    continue;
                }
                
                // Calculate and log significant movements only
                int movement = newPosition.y - initialBaseline.y;
                loggingState.logMovementIfSignificant(newPosition.y, Level.DEBUG, 
                    "Scrollbar movement: {}px from baseline", movement);
    
                if (movement == 0) {
                    noMovementCount++;
                    if (noMovementCount >= 3) {
                        logger.info("Document verification complete - no movement detected");
                        return false;
                    }
                } else if (movement > 0) {
                    noMovementCount = 0;
                    // Confirm movement is stable
                    Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
                    Rectangle confirmPosition = findScrollbarThumb(fixedScrollbarRegion);
                    
                    if (confirmPosition != null && confirmPosition.y > initialBaseline.y) {
                        long totalTime = System.currentTimeMillis() - startTime;
                        logger.info("Document loaded successfully after {}ms", totalTime);
                        isTrackingStarted = false;
                        return true;
                    }
                }
            }
            
            logger.warn("Document load verification timed out after {}ms", 
                System.currentTimeMillis() - startTime);
            return false;
    
        } catch (Exception e) {
            logger.error("Document verification failed: {}", e.getMessage());
            return false;
        }
    }

    // Helper Methods

    /**
     * Checks if two matches represent the same stable UI element position.
     */
    private boolean isMatchStable(Match lastMatch, Match currentMatch) {
        if (lastMatch == null) {
            return false;
        }
        return currentMatch.x == lastMatch.x && currentMatch.y == lastMatch.y;
    }
    
    /**
     * Determines if a color matches any of the standard scrollbar colors.
     */
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

    /**
     * Compares two colors accounting for configured tolerance.
     */
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

    /**
     * Performs a quick check for the presence of a UI element without stability verification.
     */
    public Match quickMatchCheck(Pattern pattern) {
        return uiRegion.exists(pattern);
    }

    /**
     * Saves debug screenshots for scrollbar detection analysis.
     */
    /* private void saveDebugScreenshot(BufferedImage screenshot, Region searchRegion, String type) {
        try {
            File debugDir = new File("debug/scrollbar");
            debugDir.mkdirs();
            
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
    }*/
}