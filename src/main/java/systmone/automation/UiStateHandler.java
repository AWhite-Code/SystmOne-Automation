package systmone.automation;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

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
                logger.info("Could not find selection pattern");
                return false;
            }
            logger.info("Found selection at position: ({}, {})", selectionMatch.x, selectionMatch.y);
            
            int approximateOffset = 947;
            int scrollbarX = selectionMatch.x + approximateOffset;
            
            Region searchRegion = new Region(
                scrollbarX - 10,
                selectionMatch.y - 100,
                20,
                800
            );
            
            logger.info("Searching for scrollbar in region: ({}, {}, width: {}, height: {})",
                searchRegion.x, searchRegion.y, searchRegion.w, searchRegion.h);
            
            // Capture and save the search region
            Rectangle bounds = new Rectangle(
                searchRegion.x, 
                searchRegion.y, 
                searchRegion.w, 
                searchRegion.h
            );
            BufferedImage screenshot = robot.createScreenCapture(bounds);
            
            // Create a new image with the red border
            BufferedImage debugImage = new BufferedImage(
                screenshot.getWidth(), 
                screenshot.getHeight(), 
                BufferedImage.TYPE_INT_RGB
            );
            
            // Draw the original screenshot
            Graphics2D g2d = debugImage.createGraphics();
            g2d.drawImage(screenshot, 0, 0, null);
            
            // Draw red border
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(0, 0, debugImage.getWidth() - 1, debugImage.getHeight() - 1);
            g2d.dispose();
            
            // Save the debug image
            try {
                // Create a timestamped filename
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
                File outputFile = new File("scrollbar_search_" + timestamp + ".png");
                ImageIO.write(debugImage, "png", outputFile);
                logger.info("Saved debug screenshot to: {}", outputFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to save debug screenshot: {}", e.getMessage());
            }
            
            // Sample from the center of the captured image
            int centerX = screenshot.getWidth() / 2;
            logger.info("Starting color sampling at screen coordinates ({}, {})", 
                searchRegion.x + centerX, searchRegion.y);
            
            // Sample a larger range of Y values
            boolean foundChange = false;
            Color lastColor = null;
            
            for (int y = 0; y < screenshot.getHeight(); y += 5) {  // Sample every 5 pixels
                Color pixelColor = new Color(screenshot.getRGB(centerX, y));
                
                // Only log when the color changes significantly
                if (lastColor == null || colorDifference(lastColor, pixelColor) > 20) {
                    logger.info("At screen y={}: RGB({},{},{})", 
                        (searchRegion.y + y), 
                        pixelColor.getRed(), 
                        pixelColor.getGreen(), 
                        pixelColor.getBlue());
                    foundChange = true;
                }
                lastColor = pixelColor;
            }
            
            if (!foundChange) {
                logger.warn("No significant color changes found in search region - may be looking in wrong area");
            }
    
            Rectangle thumbBounds = findScrollbarThumb(searchRegion);
            if (thumbBounds != null) {
                logger.info("Found scrollbar thumb at: ({}, {}, width: {}, height: {})",
                    thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
                scrollbarBounds = thumbBounds;
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Failed to initialize scrollbar tracking: {}", e.getMessage(), e);
            return false;
        }
    }
    
    // Helper method to detect significant color changes
    private int colorDifference(Color c1, Color c2) {
        return Math.abs(c1.getRed() - c2.getRed()) +
               Math.abs(c1.getGreen() - c2.getGreen()) +
               Math.abs(c1.getBlue() - c2.getBlue());
    }
    
    /**
     * Finds the scrollbar thumb by color in the specified region
     */
    private Rectangle findScrollbarThumb(Region searchRegion) {
        try {
            // Log the search region we're working with
            logger.debug("Starting scrollbar thumb search in region: ({}, {}, width: {}, height: {})",
                searchRegion.x, searchRegion.y, searchRegion.w, searchRegion.h);
    
            Rectangle bounds = new Rectangle(
                searchRegion.x,
                searchRegion.y,
                searchRegion.w,
                searchRegion.h
            );
            BufferedImage screenshot = robot.createScreenCapture(bounds);
            
            // Log that we've captured the screenshot successfully
            logger.debug("Captured screenshot for color analysis, dimensions: {}x{}", 
                screenshot.getWidth(), screenshot.getHeight());
    
            int thumbTop = -1;
            int thumbBottom = -1;
            
            // Scan down the center of the scrollbar
            int centerX = searchRegion.w / 2;
            logger.debug("Scanning vertically at x-position: {}", centerX);
    
            for (int y = 0; y < screenshot.getHeight(); y++) {
                Color pixelColor = new Color(screenshot.getRGB(centerX, y));
                
                if (isScrollbarColor(pixelColor)) {
                    if (thumbTop == -1) {
                        thumbTop = y;
                        logger.info("Found thumb top edge at y={}, color state: {}, RGB({},{},{})", 
                            y, getColorState(pixelColor), 
                            pixelColor.getRed(), pixelColor.getGreen(), pixelColor.getBlue());
                    }
                    thumbBottom = y;
                } else if (thumbTop != -1) {
                    // We've found the complete thumb
                    logger.info("Found thumb bottom edge at y={}, total height: {}", 
                        thumbBottom, (thumbBottom - thumbTop + 1));
                    break;
                }
            }
            
            if (thumbTop != -1 && thumbBottom != -1) {
                Rectangle thumbBounds = new Rectangle(
                    searchRegion.x,
                    searchRegion.y + thumbTop,
                    searchRegion.w,
                    thumbBottom - thumbTop + 1
                );
                logger.info("Successfully found scrollbar thumb: ({}, {}, width: {}, height: {})",
                    thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height);
                return thumbBounds;
            } else {
                logger.warn("No scrollbar thumb found in search region");
            }
            
        } catch (Exception e) {
            logger.error("Error finding scrollbar thumb by color: {}", e.getMessage());
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