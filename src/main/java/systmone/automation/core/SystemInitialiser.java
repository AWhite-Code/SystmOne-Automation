package systmone.automation.core;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import systmone.automation.config.ApplicationConfig;
import systmone.automation.state.InitialisationResult;
import systmone.automation.ui.SystmOneAutomator;

/**
 * Manages the complete initialization sequence for the SystmOne automation system.
 * This class coordinates the setup of all required components in a specific order,
 * ensuring proper dependency management and validation at each step.
 * 
 * The initialization process follows a strict sequence:
 * 1. Image Library Setup - Loads and validates required UI pattern images
 * 2. Location Determination - Identifies the deployment environment
 * 3. Automator Creation - Initializes the core automation component
 * 4. Window Focus - Ensures proper application window state
 * 5. Output Directory Setup - Creates the document storage structure
 * 6. Component Integration - Assembles and validates all system components
 * 
 * Each step includes comprehensive error handling and validation to ensure
 * the system starts in a known-good state. The process can be safely
 * interrupted at any point, with appropriate cleanup and error reporting.
 * 
 * Dependencies:
 * - Requires access to an image directory with UI pattern files
 * - Needs write access for output directory creation
 * - Requires an active SystmOne application instance
 */
public class SystemInitialiser {
    private static final Logger logger = LoggerFactory.getLogger(SystemInitialiser.class);

    /**
     * Executes the complete system initialization sequence.
     * Each initialization step is executed in order, with validation
     * between steps to ensure system integrity.
     * 
     * @return InitialisationResult containing either initialized components
     *         or a detailed error message if initialization fails
     */
    public InitialisationResult initialise() {
        try {
            // Validate and load UI pattern images required for automation
            if (!initializeImageLibrary()) {
                return InitialisationResult.failed("Image library initialization failed");
            }
    
            // Analyze UI patterns to identify deployment environment
            ApplicationConfig.Location location = determineLocation();
            if (location == null) {
                return InitialisationResult.failed("Location determination failed");
            }
    
            // Create automator with location-specific configuration
            SystmOneAutomator automator = createAutomator(location);
            if (automator == null) {
                return InitialisationResult.failed("Failed to create SystmOne automator");
            }
    
            // Ensure proper application window state
            try {
                automator.focus();
            } catch (InterruptedException e) {
                return InitialisationResult.failed("Failed to focus SystmOne window: " + e.getMessage());
            }
    
            // Establish document storage structure
            String outputFolder = initializeOutputDirectory();
            if (outputFolder == null) {
                return InitialisationResult.failed("Failed to create output directory");
            }
    
            // Initialize core system component container
            SystemComponents components = new SystemComponents(automator, outputFolder);
            
            // Verify all required components are properly initialized
            if (components.getUiHandler() == null || components.getPopupHandler() == null) {
                return InitialisationResult.failed("Failed to initialize all required components");
            }
    
            return InitialisationResult.succeeded(components);
    
        } catch (Exception e) {
            return InitialisationResult.failed("Unexpected error during initialization: " + e.getMessage());
        }
    }

    /**
     * Initializes the image pattern library for UI automation.
     * Validates the image directory existence and contents, ensuring
     * all required pattern files are available.
     * 
     * @return true if the image library is successfully initialized,
     *         false if any validation fails
     */
    private boolean initializeImageLibrary() {
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
     * Creates a timestamped output directory for document storage.
     * The directory structure follows the configured format and base path.
     * 
     * @return The absolute path to the created output directory,
     *         or null if directory creation fails
     */
    private String initializeOutputDirectory() {
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
     * Creates and configures a SystmOneAutomator instance for the specified location.
     * Applies location-specific pattern matching thresholds and configurations.
     * 
     * @param location The determined deployment location
     * @return Configured SystmOneAutomator instance, or null if creation fails
     */
    private SystmOneAutomator createAutomator(ApplicationConfig.Location location) {
        try {
            double similarity = (location == ApplicationConfig.Location.DENTON) ? 
                ApplicationConfig.DENTON_SIMILARITY : ApplicationConfig.WOOTTON_SIMILARITY;
                
            return new SystmOneAutomator(location, similarity);
            
        } catch (FindFailed e) {
            logger.error("Failed to create automator: " + e.getMessage());
            return null;
        }
    }

    /**
     * Determines the deployment location by analyzing the application UI.
     * Uses pattern matching to identify location-specific UI elements
     * and selects the best match based on confidence scores.
     * 
     * The process involves:
     * 1. Loading location-specific test patterns
     * 2. Searching for matches in the application window
     * 3. Analyzing match scores to determine the correct location
     * 
     * @return The detected ApplicationConfig.Location, or null if
     *         location cannot be determined conclusively
     */
    private ApplicationConfig.Location determineLocation() {
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