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
            final int SCROLLBAR_HEIGHT = 400;      // Height for 1920x1080
            final int UPWARD_PADDING = 20;         // Look slightly above to catch the ^ arrow above scrollbar
            final int DOWNWARD_PADDING = 100;      // Extra space for scrolling down
            
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
            final int MIN_THUMB_HEIGHT = 15;
            final int ARROW_SKIP = 25;
            final int SCAN_WINDOW = 200;
            final int LOOK_ABOVE = 20;
            
        // Calculate capture region
        int captureStartY = searchRegion.y + ARROW_SKIP;
        if (baselineThumbPosition != null) {
            // Look relative to last known position
            captureStartY = Math.max(
                searchRegion.y + ARROW_SKIP,
                baselineThumbPosition.y - LOOK_ABOVE
            );
        }
    
            // Add logging for capture dimensions
            logger.debug("Capturing region: x={}, y={}, width={}, height={}", 
                searchRegion.x, captureStartY, searchRegion.w, SCAN_WINDOW);
            
            BufferedImage screenshot = robot.createScreenCapture(new Rectangle(
                searchRegion.x,
                captureStartY,
                searchRegion.w,
                SCAN_WINDOW
            ));
    
            // Color detection logging
            int centerX = screenshot.getWidth() / 2;
            int currentRun = 0;
            int longestRun = 0;
            int longestStart = -1;
            int runStart = -1;
            int colorMatches = 0;  // Track how many pixels match scrollbar color
            
            for (int y = 0; y < screenshot.getHeight(); y++) {
                Color pixelColor = new Color(screenshot.getRGB(centerX, y));
                
                if (isScrollbarColor(pixelColor)) {
                    colorMatches++;
                    if (currentRun == 0) {
                        runStart = y;
                        logger.trace("Started new color run at y={}", y);
                    }
                    currentRun++;
                    
                    if (currentRun > longestRun) {
                        longestRun = currentRun;
                        longestStart = runStart;
                        logger.trace("New longest run: length={}, start_y={}", longestRun, longestStart);
                    }
                } else {
                    if (currentRun > 0) {
                        logger.trace("Ended color run: length={}, at y={}", currentRun, y);
                    }
                    currentRun = 0;
                    runStart = -1;
                }
            }
            
            logger.debug("Color analysis complete - found {} matching pixels, longest run={} starting at y={}", 
                colorMatches, longestRun, longestStart);
    
                if (longestRun >= MIN_THUMB_HEIGHT) {
                    // Here's the key change - we need to return absolute screen coordinates
                    Rectangle thumbBounds = new Rectangle(
                        searchRegion.x,
                        captureStartY + longestStart,  // This gives us the actual screen Y coordinate
                        searchRegion.w,
                        longestRun
                    );
                    
                    logger.debug("Found thumb - relative_start={}, absolute_y={}, height={}", 
                        longestStart, thumbBounds.y, longestRun);
                        
                    return thumbBounds;
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Error finding scrollbar thumb: {}", e.getMessage());
                return null;
            }
        }
    
    // Helper method for debug screenshots
    private void saveDebugScreenshot(BufferedImage screenshot, int x, int y) {
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

    /**
     * Verifies that the document has fully loaded by monitoring scrollbar movement
    */
    public boolean verifyDocumentLoaded(double timeout) {
        if (!isTrackingStarted) {
            logger.error("Document tracking not started - missing initialization");
            return false;
        }
    
        try {
            // Initial delay to let the UI catch up after navigation command
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS*2);
            
            // Initialize our tracking variables
            long startTime = System.currentTimeMillis();
            long timeoutMs = (long)(timeout * 1000);
            int checkCount = 0;
            int consecutiveMatchCount = 0;
            Rectangle lastPosition = null;
            int popupHandledCount = 0;
            
            // Store our starting position for comparison
            Rectangle initialBaseline = baselineThumbPosition;
            logger.info("Starting verification with initial baseline at y={}", initialBaseline.y);
            
            // Main verification loop
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                logger.debug("Verification check #{} - elapsed={}ms, timeout={}ms", 
                    checkCount + 1, elapsedMs, timeoutMs);
    
                // Handle any popups before proceeding
                if (popupHandler.isPopupPresent()) {
                    logger.info("Popup detected during verification - attempt {}", 
                        popupHandledCount + 1);
                    
                    if (popupHandledCount >= ApplicationConfig.MAX_POPUP_HANDLES) {
                        logger.error("Exceeded maximum popup handling attempts");
                        return false;
                    }
                    
                    popupHandler.dismissPopup(true);
                    popupHandledCount++;
                    consecutiveMatchCount = 0;
                    lastPosition = null;
                    
                    Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    
                    // Recheck document selection
                    if (mainWindow.exists(selectionBorderPattern) == null) {
                        logger.warn("Lost document selection after popup - retrying DOWN key");
                        mainWindow.type(Key.DOWN);
                        Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    }
                    
                    startTime = System.currentTimeMillis();  // Reset timeout
                    continue;
                }
    
                // Try to find current thumb position
                Rectangle newPosition = findScrollbarThumb(fixedScrollbarRegion);
                if (newPosition == null) {
                    logger.warn("Failed to find thumb on check #{}", checkCount + 1);
                    if (popupHandler.isPopupPresent()) {
                        continue;
                    }
                    return false;
                }
    
                // Calculate and log movement
                int movement = newPosition.y - initialBaseline.y;
                checkCount++;
                
                logger.info("Position check #{} - baseline={}, current={}, movement={}, consecutive_matches={}", 
                    checkCount, initialBaseline.y, newPosition.y, movement, consecutiveMatchCount);
    
                if (movement > 0) {
                    logger.debug("Detected downward movement of {} pixels", movement);
                    if (lastPosition != null && lastPosition.y == newPosition.y) {
                        consecutiveMatchCount++;
                        logger.debug("Position matched previous - consecutive_matches={}", 
                            consecutiveMatchCount);
                        
                        if (consecutiveMatchCount >= 2) {
                            logger.info("Verification successful - movement={}, final_y={}", 
                                movement, newPosition.y);
                            isTrackingStarted = false;
                            return true;
                        }
                    } else {
                        logger.debug("New position differs from last - resetting consecutive matches");
                        consecutiveMatchCount = 1;
                    }
                    
                    lastPosition = newPosition;
                } else {
                    if (lastPosition == null) {
                        logger.debug("No movement detected (movement={}, needed >0)", movement);
                        consecutiveMatchCount = 0;
                        
                        if (checkCount > 5 && checkCount % 5 == 0) {
                            if (!popupHandler.isPopupPresent()) {
                                logger.warn("Retrying navigation after {} failed checks", checkCount);
                                mainWindow.type(Key.DOWN);
                                Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                            }
                        }
                    }
                }
    
                Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
            }
    
            logger.error("Verification timed out after {} checks - last consecutive matches: {}", 
                checkCount, consecutiveMatchCount);
            return false;
    
        } catch (Exception e) {
            logger.error("Verification error: {} at {}", 
                e.getMessage(), e.getStackTrace()[0]);
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