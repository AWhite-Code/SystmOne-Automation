package systmone.automation.ui;

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
            logger.error("Scrollbar tracking not initialized");
            return false;
        }

        try {
            Rectangle newBaseline = findScrollbarThumb(fixedScrollbarRegion);
            if (newBaseline == null) {
                logger.error("Could not establish baseline position");
                return false;
            }

            baselineThumbPosition = newBaseline;
            isTrackingStarted = true;
            
            logger.info("Started new document tracking - baseline set at y={}", baselineThumbPosition.y);
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
            
            // If thumb found (minimum 3 pixels high, this may cause issues later)
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
        if (!isTrackingStarted) {
            logger.error("Document tracking not started");
            return false;
        }

        try {
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
            
            long startTime = System.currentTimeMillis();
            long timeoutMs = (long)(timeout * 1000);
            int checkCount = 0;
            int consecutiveMatchCount = 0;
            Rectangle lastPosition = null;
            int popupHandledCount = 0;
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                // Check for popup before any UI interaction
                if (popupHandler.isPopupPresent()) {
                    logger.info("Popup detected during document verification - attempt {}", 
                        popupHandledCount + 1);
                    
                    if (popupHandledCount >= ApplicationConfig.MAX_POPUP_HANDLES) {
                        logger.error("Exceeded maximum popup handling attempts");
                        return false;
                    }
                    
                    // Dismiss popup with Enter key (assuming "Yes" is the safe default)
                    popupHandler.dismissPopup(true);
                    popupHandledCount++;
                
                    consecutiveMatchCount = 0;
                    lastPosition = null;
                    
                    // Give the UI time to stabilize
                    Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    
                    // Verify our document selection is still valid
                    Match documentBorder = mainWindow.exists(selectionBorderPattern);
                    if (documentBorder == null) {
                        logger.warn("Lost document selection after popup - retrying DOWN key");
                        mainWindow.type(Key.DOWN);
                        Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    }
                    
                    // Reset timeout to give full time for verification
                    startTime = System.currentTimeMillis();
                    continue;
                }

                Rectangle newPosition = findScrollbarThumb(fixedScrollbarRegion);
                if (newPosition == null) {
                    if (popupHandler.isPopupPresent()) {
                        logger.info("Popup detected after failed thumb detection");
                        continue;  // Go back to start of loop to handle popup
                    }
                    logger.error("Could not find current thumb position");
                    return false;
                }

                int movement = newPosition.y - baselineThumbPosition.y;
                checkCount++;
                
                logger.debug("Check #{} - Comparing positions - baseline: y={}, current: y={}, movement: {} pixels", 
                    checkCount, baselineThumbPosition.y, newPosition.y, movement);

                if (movement >= ApplicationConfig.MIN_THUMB_MOVEMENT) {
                    if (lastPosition != null && lastPosition.y == newPosition.y) {
                        consecutiveMatchCount++;
                        if (consecutiveMatchCount >= 2) {
                            logger.info("Verified stable downward thumb movement - document ready");
                            isTrackingStarted = false;
                            return true;
                        }
                    } else {
                        consecutiveMatchCount = 1;
                    }
                } else {
                    consecutiveMatchCount = 0;
                    
                    if (checkCount > 5 && checkCount % 5 == 0) {
                        // Check for popup before sending additional DOWN
                        if (popupHandler.isPopupPresent()) {
                            logger.info("Popup detected before resending DOWN");
                            continue;  // Go back to start of loop to handle popup
                        }
                        
                        logger.warn("No movement detected after {} checks, resending DOWN command", 
                            checkCount);
                        mainWindow.type(Key.DOWN);
                        Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    }
                }

                lastPosition = newPosition;
                Thread.sleep(ApplicationConfig.POLL_INTERVAL_MS);
            }

            logger.debug("Timeout reached without detecting stable movement after {} checks", 
                checkCount);
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