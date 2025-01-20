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

/**
 * Main entry point for the SystmOne document processing automation.
 * This class handles initialization of the system components and orchestrates
 * the overall document processing workflow.
 */
public class PatternTest {
    private static final Logger logger = LoggerFactory.getLogger(PatternTest.class);
    
    // Thread-safe kill switch for graceful termination
    private static final AtomicBoolean killSwitch = new AtomicBoolean(false);

    public static void main(String[] args) {
        ProcessingStats stats = null;
        
        try {
            // Initialize core system components
            SystemComponents components = initializeSystem();
            if (components == null) {
                logger.error("System initialization failed");
                return;
            }
            
            // Set up kill switch monitoring
            setupKillSwitch();
            
            // Create and run document processor
            DocumentProcessor processor = new DocumentProcessor(
                components.automator,
                components.uiHandler,
                components.outputFolder,
                killSwitch
            );
            
            // Process all documents and get results
            stats = processor.processDocuments();
            
        } catch (Exception e) {
            logger.error("Critical application failure: " + e.getMessage(), e);
        } finally {
            if (stats != null) {
                SummaryGenerator.generateProcessingSummary(stats);
            }
        }
    }
    
    /**
     * Initializes all required system components.
     * Returns null if any component fails to initialize.
     */
    private static SystemComponents initializeSystem() {
        try {
            // Initialize the image library first
            if (!initializeImageLibrary()) {
                return null;
            }
            
            // Determine location and create automator
            ApplicationConfig.Location location = determineLocation();
            if (location == null) {
                return null;
            }
            
            // Initialize SystmOne automator with appropriate settings
            double similarity = (location == ApplicationConfig.Location.DENTON) ? 
                ApplicationConfig.DENTON_SIMILARITY : ApplicationConfig.WOOTTON_SIMILARITY;
            
            SystmOneAutomator automator = new SystmOneAutomator(location, similarity);
            
            // Focus the application window
            automator.focus();
            
            // Create output directory
            String outputFolder = initializeOutputDirectory();
            if (outputFolder == null) {
                return null;
            }
            
            // Initialize UI handler
            UiStateHandler uiHandler = new UiStateHandler(automator.getWindow());
            
            return new SystemComponents(automator, uiHandler, outputFolder);
            
        } catch (Exception e) {
            logger.error("Error during system initialization: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Initializes the image library for pattern matching.
     */
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
    
    /**
     * Creates and initializes the output directory for document storage.
     */
    private static String initializeOutputDirectory() {
        try {
            String folderName = LocalDateTime.now().format(ApplicationConfig.FOLDER_DATE_FORMAT);
            String outputFolder = Paths.get(ApplicationConfig.OUTPUT_BASE_PATH, folderName).toString();
            
            Files.createDirectories(Paths.get(outputFolder));
            logger.info("Created output directory: {}", outputFolder);
            return outputFolder;
            
        } catch (Exception e) {
            logger.error("Failed to create output directory: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Sets up a daemon thread to monitor the kill switch.
     */
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
    
    /**
     * Determines the current location based on pattern matching.
     */
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
    
    /**
     * Helper class to hold initialized system components.
     * Makes it easier to pass around related components and ensures all are initialized together.
     * Should be pulled out into its own class later probably
     */
    private static class SystemComponents {
        final SystmOneAutomator automator;
        final UiStateHandler uiHandler;
        final String outputFolder;
        
        SystemComponents(SystmOneAutomator automator, UiStateHandler uiHandler, String outputFolder) {
            this.automator = automator;
            this.uiHandler = uiHandler;
            this.outputFolder = outputFolder;
        }
    }
}