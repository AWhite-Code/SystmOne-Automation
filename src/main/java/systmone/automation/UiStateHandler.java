package systmone.automation;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles UI state verification and waiting to ensure stable automation.
 * Uses active polling and verification rather than arbitrary delays.
 */
public class UiStateHandler {
    private static final Logger logger = LoggerFactory.getLogger(UiStateHandler.class);
    
    // How many consecutive stable checks we need before considering the UI stable
    private static final int REQUIRED_STABILITY_COUNT = 3;
    // How long to wait between stability checks (milliseconds)
    private static final int POLL_INTERVAL_MS = 100;
    
    private final Region uiRegion;
    private Match lastKnownPosition;
    
    public UiStateHandler(Region uiRegion) {
        this.uiRegion = uiRegion;
    }
    
    /**
     * Waits for a UI element to appear and remain stable in position.
     * This ensures we don't interact with elements that are still moving or updating.
     *
     * @param pattern The pattern to look for
     * @param timeout Maximum time to wait in seconds
     * @return The stable Match object once found
     * @throws FindFailed if pattern isn't found or doesn't stabilize within timeout
     */
    public Match waitForStableElement(Pattern pattern, double timeout) throws FindFailed {
        long startTime = System.currentTimeMillis();
        long timeoutMs = (long)(timeout * 1000);
        Match lastMatch = null;
        int stabilityCount = 0;
        
        logger.debug("Starting to wait for stable element with timeout: {} seconds", timeout);
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Match currentMatch = uiRegion.exists(pattern);
                
                if (currentMatch == null) {
                    logger.trace("No match found, resetting stability counter");
                    stabilityCount = 0;
                    lastMatch = null;
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }
                
                if (isMatchStable(lastMatch, currentMatch)) {
                    stabilityCount++;
                    logger.trace("Position stable for {} checks", stabilityCount);
                    
                    if (stabilityCount >= REQUIRED_STABILITY_COUNT) {
                        logger.debug("Element stabilized at position ({}, {})", 
                            currentMatch.x, currentMatch.y);
                        lastKnownPosition = currentMatch;
                        return currentMatch;
                    }
                } else {
                    logger.trace("Position changed, resetting stability counter");
                    stabilityCount = 1;
                }
                
                lastMatch = currentMatch;
                Thread.sleep(POLL_INTERVAL_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FindFailed("Interrupted while waiting for stable element");
            }
        }
        
        throw new FindFailed(
            String.format("Element did not stabilize within %.1f seconds", timeout));
    }
    
    /**
     * Verifies that UI navigation has completed by ensuring the selection has moved
     * to a new position and stabilized there.
     *
     * @param pattern The pattern to look for
     * @param timeout Maximum time to wait in seconds
     * @return true if navigation succeeded, false otherwise
     */
    public boolean verifyNavigationComplete(Pattern pattern, double timeout) throws FindFailed {
        Match newPosition = waitForStableElement(pattern, timeout);
        
        if (lastKnownPosition != null) {
            boolean hasMoved = newPosition.y != lastKnownPosition.y || 
                             newPosition.x != lastKnownPosition.x;
            
            logger.debug("Navigation check - Previous pos: ({}, {}), New pos: ({}, {})", 
                lastKnownPosition.x, lastKnownPosition.y,
                newPosition.x, newPosition.y);
                
            return hasMoved;
        }
        
        return true; // First navigation always succeeds
    }
    
    private boolean isMatchStable(Match lastMatch, Match currentMatch) {
        if (lastMatch == null) {
            return false;
        }
        return currentMatch.x == lastMatch.x && currentMatch.y == lastMatch.y;
    }
}