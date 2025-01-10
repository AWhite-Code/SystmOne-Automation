package systmone.automation;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import systmone.automation.config.*;

public class PatternTest {
    private static final Logger logger = LoggerFactory.getLogger(PatternTest.class);
    
    // Core components
    private static ApplicationConfig.Location currentLocation;
    private static Pattern selectionBorderPattern;
    private static Pattern printMenuItemPattern;
    private static Pattern documentCountPattern;
    private static Pattern saveDialogPattern;
    private static Region systmOneWindow;
    private static App systmOne;
    private static String outputFolder;
    private static UiStateHandler uiHandler;
    
    // Thread-safe kill switch
    private static final AtomicBoolean killSwitch = new AtomicBoolean(false);

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
            SummaryGenerator.generateProcessingSummary(stats);
        }
    }
    
    private static boolean initializeSystem() {
        boolean success = initializeImageLibrary() &&
                         findAndFocusSystmOne() &&
                         initializePatterns() && 
                         initializeOutputDirectory();
                         
        if (success) {
            uiHandler = new UiStateHandler(systmOneWindow);
        }
        
        return success;
    }
    
    private static boolean initializeImageLibrary() {
        try {
            File imageDir = new File(ApplicationConfig.IMAGE_DIR_PATH).getAbsoluteFile();
            if (!imageDir.exists() || !imageDir.isDirectory()) {
                logger.error("Image directory not found: {}", imageDir.getAbsolutePath());
                return false;
            }
        
            File[] files = imageDir.listFiles();
            if (files != null) {
                logger.info("Found these images in directory:");
                for (File file : files) {
                    logger.info(" - {}", file.getName());
                }
            } else {
                logger.error("No files found in image directory");
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
            double similarity = (currentLocation == ApplicationConfig.Location.DENTON) ? 
                ApplicationConfig.DENTON_SIMILARITY : ApplicationConfig.WOOTTON_SIMILARITY;
                
            logger.info("Initializing patterns for {} with similarity {}", 
                currentLocation, similarity);
            
            selectionBorderPattern = new Pattern("selection_border" + locationSuffix + ".png")
                .similar(similarity);
            printMenuItemPattern = new Pattern("print_menu_item" + locationSuffix + ".png")
                .similar(similarity);
            documentCountPattern = new Pattern("document_count" + locationSuffix + ".png")
                .similar(similarity);
            saveDialogPattern = new Pattern("save_dialog_title" + locationSuffix + ".png")
                .similar(similarity);
            
            logger.info("All patterns initialized for {} location", currentLocation);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to initialize patterns: " + e.getMessage(), e);
            return false;
        }
    }
    
    private static boolean initializeOutputDirectory() {
        try {
            String folderName = LocalDateTime.now().format(ApplicationConfig.FOLDER_DATE_FORMAT);
            outputFolder = Paths.get(ApplicationConfig.OUTPUT_BASE_PATH, folderName).toString();
            
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
            systmOne = new App(ApplicationConfig.APP_TITLE);
            if (systmOne.window() == null) {
                logger.error("SystmOne window not found");
                return false;
            }
            
            systmOneWindow = systmOne.window();
            logger.info("Found SystmOne window: {}x{} at ({},{})", 
                systmOneWindow.w, systmOneWindow.h, systmOneWindow.x, systmOneWindow.y);
            
            systmOne.focus();
            TimeUnit.MILLISECONDS.sleep(ApplicationConfig.FOCUS_DELAY_MS);
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
            logger.info("Looking for document count pattern for location: {}", currentLocation);
            logger.info("Using pattern: document_count_{}.png", currentLocation.name().toLowerCase());
            
            Match countMatch = systmOneWindow.exists(documentCountPattern);
            
            if (countMatch == null) {
                logger.error("Document count pattern not found for location: {}", currentLocation);
                return -1;
            }
            
            logger.info("Found document count pattern at: ({},{})", countMatch.x, countMatch.y);
            
            // Locate the word 'Document' then draw a box around it to fix the number of documents. Drawn to left to avoid page counter
            Region textRegion = new Region(
                countMatch.x - 50, 
                countMatch.y, 
                150, 
                countMatch.h
            );
            
            String countText = textRegion.text();
            logger.info("Extracted text from region: '{}'", countText);
            
            int count = extractNumberFromText(countText);
            logger.info("Extracted count: {}", count);
            return count;
            
        } catch (Exception e) {
            logger.error("Error getting document count: " + e.getMessage(), e);
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
    
    // THIS METHOD CALLS THE ONE BELOW, I SHOULD, **REALLY** CHANGE THE NAMES
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
                
                // Only attempt navigation if not at the last document
                if (i < stats.totalDocuments - 1) {
                    navigateToNextDocument();
                } else {
                    logger.info("Reached final document - processing complete");
                }
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
        // Wait for a stable UI before processing
        Match documentMatch = uiHandler.waitForStableElement(
            selectionBorderPattern, 
            ApplicationConfig.DIALOG_TIMEOUT
        );

        int documentNumber = index + 1;
        logger.info("Processing document {} of {} at: ({},{})", 
            documentNumber,
            stats.totalDocuments,
            documentMatch.x, 
            documentMatch.y
        );

        String documentPath = Paths.get(outputFolder, "Document" + documentNumber + ".pdf")
            .toString();
            
        if (!ClipboardHelper.setClipboardContent(documentPath)) {
            throw new RuntimeException("Failed to copy document path to clipboard");
        }

        printDocument(documentMatch, documentPath);
        }

    private static void printDocument(Match documentMatch, String savePath) 
            throws FindFailed {
        // Right click on the document to open context menu
        documentMatch.rightClick();
        
        // Wait for and click the print menu item
        Match printMenuItem = systmOneWindow.wait(printMenuItemPattern, ApplicationConfig.MENU_TIMEOUT);
        printMenuItem.click();
        logger.debug("Clicked print menu item");
        
        // Wait for the save dialog title to appear
        systmOneWindow.wait(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        // Paste the full path and press enter
        systmOneWindow.type("a", KeyModifier.CTRL); 
        systmOneWindow.type("v", KeyModifier.CTRL); 
        systmOneWindow.type(Key.ENTER);
        
        // Wait for the save dialog to disappear
        systmOneWindow.waitVanish(saveDialogPattern, ApplicationConfig.DIALOG_TIMEOUT);
        
        logger.info("Saved document to: {}", savePath);
    }
    
    private static void navigateToNextDocument() throws FindFailed {
        systmOneWindow.type(Key.DOWN);
        
        if (!uiHandler.verifyNavigationComplete(selectionBorderPattern, 
            ApplicationConfig.DIALOG_TIMEOUT)) {
            throw new FindFailed("Navigation failed - selection did not move to new position");
        }
    }

    private static boolean determineLocation() {
        try {
            logger.info("Starting location determination...");
            
            // Try Denton patterns first
            Pattern dentonTest = new Pattern("selection_border_denton.png")
                .similar(ApplicationConfig.LOCATION_SIMILARITY);
            logger.info("Checking for Denton pattern with similarity {}", ApplicationConfig.LOCATION_SIMILARITY);
            Match dentonMatch = systmOneWindow.exists(dentonTest);
            
            if (dentonMatch != null) {
                logger.info("Found Denton match with score: {}", dentonMatch.getScore());
            } else {
                logger.info("No Denton match found");
            }
            
            // Try Wootton patterns
            Pattern woottonTest = new Pattern("selection_border_wootton.png")
                .similar(ApplicationConfig.LOCATION_SIMILARITY);
            logger.info("Checking for Wootton pattern with similarity {}", ApplicationConfig.LOCATION_SIMILARITY);
            Match woottonMatch = systmOneWindow.exists(woottonTest);
            
            if (woottonMatch != null) {
                logger.info("Found Wootton match with score: {}", woottonMatch.getScore());
            } else {
                logger.info("No Wootton match found");
            }
            
            // Compare matches
            if (dentonMatch != null && woottonMatch != null) {
                logger.info("Found both matches. Comparing scores...");
                logger.info("Denton score: {} vs Wootton score: {}", 
                    dentonMatch.getScore(), woottonMatch.getScore());
                
                if (dentonMatch.getScore() > woottonMatch.getScore()) {
                    currentLocation = ApplicationConfig.Location.DENTON;
                    logger.info("Selected Denton (higher score)");
                } else {
                    currentLocation = ApplicationConfig.Location.WOOTTON;
                    logger.info("Selected Wootton (higher score)");
                }
            } else if (dentonMatch != null) {
                currentLocation = ApplicationConfig.Location.DENTON;
                logger.info("Only Denton match found, selecting Denton");
            } else if (woottonMatch != null) {
                currentLocation = ApplicationConfig.Location.WOOTTON;
                logger.info("Only Wootton match found, selecting Wootton");
            } else {
                logger.error("No matches found for either location");
                return false;
            }
            
            logger.info("Final location selected: {}", currentLocation);
            return true;
                
        } catch (Exception e) {
            logger.error("Error in determineLocation: " + e.getMessage(), e);
            return false;
        }
    }
}