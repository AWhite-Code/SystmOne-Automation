package com.nhs.automation.test;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

public class PatternTest {
    private static final Logger logger = LoggerFactory.getLogger(PatternTest.class);
    
    // Application constants
    private static final String APP_TITLE = "SystmOne GP:";
    private static final String IMAGE_DIR_PATH = "src/main/resources/images";
    private static final String OUTPUT_BASE_PATH = "C:\\Users\\Alexwh\\Dev Environs\\SystmOne_Automation_Output"; // REMEMBER TO CHANGE THIS TO DEV ENVIRONS INSTEAD OF PROGRAMMING
    private static final DateTimeFormatter FOLDER_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd-MM-yyyy - HH-mm-ss");
    
    private enum Location {
        DENTON, WOOTTON
    }    

    // Pattern matching settings
    private static final double PATTERN_SIMILARITY = 0.8;
    private static final long NAVIGATION_DELAY_MS = 200;
    private static final int FOCUS_DELAY_MS = 1000;
    
    // UI Detection timeouts (in seconds)
    private static final double MENU_TIMEOUT = 5.0;
    private static final double DIALOG_TIMEOUT = 10.0;
    
    // UI Elements
    private static final String SAVE_DIALOG_TITLE = "Save Print Output As";
    
    // Core components
    private static Location currentLocation;
    private static Pattern selectionBorderPattern;
    private static Pattern printMenuItemPattern;
    private static Pattern documentCountPattern;
    private static Pattern saveDialogPattern;
    private static Region systmOneWindow;
    private static App systmOne;
    private static String outputFolder;
    
    // Thread-safe kill switch
    private static final AtomicBoolean killSwitch = new AtomicBoolean(false);
    
    // Document processing statistics
    private static class ProcessingStats {
        int totalDocuments;
        int processedDocuments;
        List<DocumentError> errors;
        
        ProcessingStats() {
            this.errors = new ArrayList<>();
        }
    }
    
    private static class DocumentError {
        final int documentIndex;
        final String errorMessage;
        
        DocumentError(int index, String message) {
            this.documentIndex = index;
            this.errorMessage = message;
        }
    }

    public static void main(String[] args) {
        ProcessingStats stats = new ProcessingStats();
        
        try {
            if (!initializeSystem()) {
                logger.error("System initialization failed");
                return;
            }
            
            setupKillSwitch();
            processDocuments(stats);
            
        } catch (Exception e) {
            logger.error("Critical application failure: " + e.getMessage(), e);
        } finally {
            generateProcessingSummary(stats);
        }
    }
    
    private static boolean initializeSystem() {
        return initializeImageLibrary() &&
                findAndFocusSystmOne() &&
               initializePatterns() && 
               initializeOutputDirectory();
    }
    
    private static boolean initializeImageLibrary() {
        try {
            File imageDir = new File(IMAGE_DIR_PATH).getAbsoluteFile();
            if (!imageDir.exists() || !imageDir.isDirectory()) {
                logger.error("Image directory not found: {}", imageDir.getAbsolutePath());
                return false;
            }
            
            ImagePath.add(imageDir.getAbsolutePath());
            logger.info("Image library initialized: {}", imageDir.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to initialize image library: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean initializePatterns() {
        try {
            if (!determineLocation()) {
                return false;
            }
            
            String locationSuffix = "_" + currentLocation.name().toLowerCase();
            
            selectionBorderPattern = new Pattern("selection_border" + locationSuffix + ".png")
                .similar(PATTERN_SIMILARITY);
            printMenuItemPattern = new Pattern("print_menu_item" + locationSuffix + ".png")
                .similar(PATTERN_SIMILARITY);
            documentCountPattern = new Pattern("document_count" + locationSuffix + ".png")
                .similar(PATTERN_SIMILARITY);
            saveDialogPattern = new Pattern("save_dialog_title" + locationSuffix + ".png")
                .similar(PATTERN_SIMILARITY);
            
            logger.info("All patterns initialized for {} location with similarity: {}", 
                currentLocation, PATTERN_SIMILARITY);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to initialize patterns: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean initializeOutputDirectory() {
        try {
            String folderName = LocalDateTime.now().format(FOLDER_DATE_FORMAT);
            outputFolder = Paths.get(OUTPUT_BASE_PATH, folderName).toString();
            
            Files.createDirectories(Paths.get(outputFolder));
            logger.info("Created output directory: {}", outputFolder);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to create output directory: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean findAndFocusSystmOne() {
        try {
            systmOne = new App(APP_TITLE);
            if (systmOne.window() == null) {
                logger.error("SystmOne window not found");
                return false;
            }
            
            systmOneWindow = systmOne.window();
            logger.info("Found SystmOne window: {}x{} at ({},{})", 
                systmOneWindow.w, systmOneWindow.h, systmOneWindow.x, systmOneWindow.y);
            
            systmOne.focus();
            TimeUnit.MILLISECONDS.sleep(FOCUS_DELAY_MS);
            return true;
            
        } catch (InterruptedException e) {
            logger.warn("Sleep interrupted after focus");
            return false;
        } catch (Exception e) {
            logger.error("Error finding SystmOne window: " + e.getMessage());
            return false;
        }
    }
    
    private static void setupKillSwitch() {
        Thread killSwitchThread = new Thread(() -> {
            while (!killSwitch.get()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        killSwitchThread.setDaemon(true);
        killSwitchThread.start();
        logger.info("Kill switch thread initialized");
    }
    
    private static int getDocumentCount() {
        try {
            Match countMatch = systmOneWindow.exists(documentCountPattern);
            
            if (countMatch == null) {
                logger.error("Document count pattern not found");
                return -1;
            }
            
            // Extract document count from larger region to ensure full text capture
            Region textRegion = new Region(
                countMatch.x - 50, 
                countMatch.y, 
                150, 
                countMatch.h
            );
            
            String countText = textRegion.text();
            return extractNumberFromText(countText);
            
        } catch (Exception e) {
            logger.error("Error getting document count: " + e.getMessage());
            return -1;
        }
    }
    
    private static int extractNumberFromText(String text) {
        String[] parts = text.split("\\s+");
        for (String part : parts) {
            if (part.matches("\\d+")) {
                return Integer.parseInt(part);
            }
        }
        logger.error("No numeric value found in text: {}", text);
        return -1;
    }
    
    private static void processDocuments(ProcessingStats stats) {
        logger.info("Starting document processing");
        
        stats.totalDocuments = getDocumentCount();
        if (stats.totalDocuments <= 0) {
            logger.error("Invalid document count: {}", stats.totalDocuments);
            return;
        }
        
        logger.info("Processing {} documents", stats.totalDocuments);
        
        for (int i = 0; i < stats.totalDocuments && !killSwitch.get(); i++) {
            try {
                processDocument(i, stats);
                navigateToNextDocument();
                stats.processedDocuments++;
                
            } catch (Exception e) {
                String errorMessage = String.format("Error processing document %d: %s", 
                    i + 1, e.getMessage());
                stats.errors.add(new DocumentError(i + 1, errorMessage));
                logger.error(errorMessage, e);
            }
        }
    }
    
    private static void processDocument(int index, ProcessingStats stats) 
            throws FindFailed, InterruptedException {
        Match documentMatch = systmOneWindow.exists(selectionBorderPattern);
        if (documentMatch == null) {
            throw new FindFailed("Failed to find document selection border");
        }
        
        int documentNumber = index + 1;
        logger.info("Processing document {} of {} at: ({},{})", 
            documentNumber,
            stats.totalDocuments,
            documentMatch.x, 
            documentMatch.y
        );
        
        // Generate the full save path for this document
        String documentPath = Paths.get(outputFolder, "Document" + documentNumber + ".pdf")
            .toString();
            
        // Copy the path to clipboard for quick access in save dialog
        setClipboardContent(documentPath);
        
        // Perform the print operation
        printDocument(documentMatch, documentPath);
    }
    
    private static void setClipboardContent(String content) {
        StringSelection stringSelection = new StringSelection(content);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
        logger.debug("Copied to clipboard: {}", content);
    }
    
    private static void printDocument(Match documentMatch, String savePath) 
            throws FindFailed {
        // Right click on the document to open context menu
        documentMatch.rightClick();
        
        // Wait for and click the print menu item
        Match printMenuItem = systmOneWindow.wait(printMenuItemPattern, MENU_TIMEOUT);
        printMenuItem.click();
        logger.debug("Clicked print menu item");
        
        // Wait for the save dialog title to appear
        systmOneWindow.wait(saveDialogPattern, DIALOG_TIMEOUT);
        
        // Paste the full path and press enter
        systmOneWindow.type("a", KeyModifier.CTRL); 
        systmOneWindow.type("v", KeyModifier.CTRL); 
        systmOneWindow.type(Key.ENTER);
        
        // Wait for the save dialog to disappear
        systmOneWindow.waitVanish(saveDialogPattern, DIALOG_TIMEOUT);
        
        logger.info("Saved document to: {}", savePath);
    }
    
    private static void navigateToNextDocument() throws InterruptedException {
        systmOneWindow.type(Key.DOWN);
        TimeUnit.MILLISECONDS.sleep(NAVIGATION_DELAY_MS);
    }
    
    private static void generateProcessingSummary(ProcessingStats stats) {
        logger.info("\nProcessing Summary:");
        logger.info("Total Documents: {}", stats.totalDocuments);
        logger.info("Successfully Processed: {}", stats.processedDocuments);
        logger.info("Errors Encountered: {}", stats.errors.size());
        
        if (!stats.errors.isEmpty()) {
            logger.info("\nError Details:");
            stats.errors.forEach(error -> 
                logger.info("Document {}: {}", 
                    error.documentIndex, 
                    error.errorMessage)
            );
        }
    }

    private static boolean determineLocation() {
        try {
            // Try Denton patterns first with higher similarity
            Pattern dentonTest = new Pattern("selection_border_denton.png")
                .similar(0.95);  // Increased similarity threshold
            Match dentonMatch = systmOneWindow.exists(dentonTest);
            
            // Try Wootton patterns with higher similarity
            Pattern woottonTest = new Pattern("selection_border_wootton.png")
                .similar(0.95);  // Increased similarity threshold
            Match woottonMatch = systmOneWindow.exists(woottonTest);
            
            // Compare match scores if both exist
            if (dentonMatch != null && woottonMatch != null) {
                if (dentonMatch.getScore() > woottonMatch.getScore()) {
                    currentLocation = Location.DENTON;
                    logger.info("Detected Denton location (score: {})", dentonMatch.getScore());
                } else {
                    currentLocation = Location.WOOTTON;
                    logger.info("Detected Wootton location (score: {})", woottonMatch.getScore());
                }
                return true;
            } else if (dentonMatch != null) {
                currentLocation = Location.DENTON;
                logger.info("Detected Denton location (score: {})", dentonMatch.getScore());
                return true;
            } else if (woottonMatch != null) {
                currentLocation = Location.WOOTTON;
                logger.info("Detected Wootton location (score: {})", woottonMatch.getScore());
                return true;
            }
            
            logger.error("Could not determine location - no matching patterns found");
            return false;
            
        } catch (Exception e) {
            logger.error("Error determining location: " + e.getMessage());
            return false;
        }
    }
}