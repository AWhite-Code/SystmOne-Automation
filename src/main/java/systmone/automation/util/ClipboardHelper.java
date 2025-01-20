package systmone.automation.util;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling clipboard operations.
 * Provides methods for copying text content to the system clipboard.
 */
public class ClipboardHelper {
    private static final Logger logger = LoggerFactory.getLogger(ClipboardHelper.class);
    
    // Private constructor to prevent instantiation of utility class
    private ClipboardHelper() {
        throw new AssertionError("ClipboardHelper is a utility class and should not be instantiated");
    }
    
    /**
     * Copies the provided content to the system clipboard.
     * @param content The text to be copied to the clipboard
     * @return true if the operation was successful, false otherwise
     */
    public static boolean setClipboardContent(String content) {
        try {
            StringSelection stringSelection = new StringSelection(content);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            logger.debug("Copied to clipboard: {}", content);
            return true;
        } catch (IllegalStateException | SecurityException e) {
            logger.error("Failed to copy to clipboard: {}", e.getMessage());
            return false;
        }
    }
}