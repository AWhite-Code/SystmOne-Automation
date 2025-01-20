package systmone.automation;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import systmone.automation.config.ApplicationConfig;

public class PatternTest {
    private static final Logger logger = LoggerFactory.getLogger(PatternTest.class);
    
    private static String outputFolder;
    private static UiStateHandler uiHandler;
    private static SystmOneAutomator automator;
    
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
        try {
            // Initialize the image library
            if (!initializeImageLibrary()) {
                return false;
            }
            
            // Determine location and create automator
            ApplicationConfig.Location location = determineLocation();
            if (location == null) {
                return false;
            }
            
            // Initialize the automator with appropriate similarity settings
            double similarity = (location == ApplicationConfig.Location.DENTON) ? 
                ApplicationConfig.DENTON_SIMILARITY : ApplicationConfig.WOOTTON_SIMILARITY;
            
            automator = new SystmOneAutomator(location, similarity);
            automator.focus();
            
            // Initialize output directory
            if (!initializeOutputDirectory()) {
                return false;
            }
            
            // Initialize UI handler with the automator's window
            uiHandler = new UiStateHandler(automator.getWindow());
            
            // Initialize scrollbar tracking
            if (!uiHandler.initializeScrollbarTracking(automator.getSelectionBorderPattern())) {
                logger.warn("Failed to initialize scrollbar tracking - will use basic verification");
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error during system initialization: {}", e.getMessage());
            return false;
        }
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
            }
            
            ImagePath.add(imageDir.getAbsolutePath());
            logger.info("Image library initialized: {}", imageDir.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to initialize image library: " + e.getMessage());
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
    
    private static void setupKillSwitch() {
        Thread killSwitchThread = new Thread(() -> {
            while (!killSwitch.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        killSwitchThread.setDaemon(true);
        killSwitchThread.start();
        logger.info("Kill switch thread initialized");
    }
    
    private static void processDocuments(ProcessingStats stats) {
        logger.info("Starting document processing");
        
        stats.totalDocuments = automator.getDocumentCount();
        if (stats.totalDocuments <= 0) {
            logger.error("Invalid document count: {}", stats.totalDocuments);
            return;
        }
        
        logger.info("Processing {} documents", stats.totalDocuments);
        
        for (int i = 0; i < stats.totalDocuments && !killSwitch.get(); i++) {
            try {
                processDocument(i, stats);
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
        Match documentMatch = uiHandler.waitForStableElement(
            automator.getSelectionBorderPattern(), 
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

        automator.printDocument(documentMatch, documentPath);

        if (documentNumber < stats.totalDocuments) {
            if (!navigateToNextDocument(stats)) {
                throw new FindFailed("Navigation to next document failed");
            }
        } else {
            logger.info("Reached final document - processing complete");
        }
    }
    
    private static boolean navigateToNextDocument(ProcessingStats stats) 
            throws FindFailed, InterruptedException {
        if (stats.processedDocuments < ApplicationConfig.MIN_DOCUMENTS_FOR_SCROLLBAR) {
            automator.navigateDown();
            Match match = uiHandler.waitForStableElement(
                automator.getSelectionBorderPattern(), 
                ApplicationConfig.DIALOG_TIMEOUT
            );
            return match != null;
        }

        if (!uiHandler.startDocumentTracking()) {
            logger.warn("Could not start scrollbar tracking, falling back to basic verification");
            automator.navigateDown();
            Match match = uiHandler.waitForStableElement(
                automator.getSelectionBorderPattern(), 
                ApplicationConfig.DIALOG_TIMEOUT
            );
            return match != null;
        }

        automator.navigateDown();
        return uiHandler.verifyDocumentLoaded(ApplicationConfig.DIALOG_TIMEOUT);
    }
    
    private static ApplicationConfig.Location determineLocation() {
        try {
            logger.info("Starting location determination...");
            
            Pattern dentonTest = new Pattern("selection_border_denton.png")
                .similar(ApplicationConfig.LOCATION_SIMILARITY);
            Pattern woottonTest = new Pattern("selection_border_wootton.png")
                .similar(ApplicationConfig.LOCATION_SIMILARITY);
            
            Region window = new App(ApplicationConfig.APP_TITLE).window();
            if (window == null) {
                logger.error("Could not find application window");
                return null;
            }
            
            Match dentonMatch = window.exists(dentonTest);
            Match woottonMatch = window.exists(woottonTest);
            
            if (dentonMatch != null && woottonMatch != null) {
                return dentonMatch.getScore() > woottonMatch.getScore() ?
                    ApplicationConfig.Location.DENTON : ApplicationConfig.Location.WOOTTON;
            } else if (dentonMatch != null) {
                return ApplicationConfig.Location.DENTON;
            } else if (woottonMatch != null) {
                return ApplicationConfig.Location.WOOTTON;
            }
            
            logger.error("No matches found for either location");
            return null;
                
        } catch (Exception e) {
            logger.error("Error in determineLocation: " + e.getMessage(), e);
            return null;
        }
    }
}