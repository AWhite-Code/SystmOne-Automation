package systmone.automation.core;

import org.sikuli.script.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import systmone.automation.config.ApplicationConfig;
import systmone.automation.killswitch.GlobalKillswitch;
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
    public InitialisationResult initialise(GlobalKillswitch killSwitch) {  // Add parameter here
        try {
            // Validate and load UI pattern images required for automation
            if (!initializeImageLibrary()) {
                return InitialisationResult.failed("Image library initialization failed");
            }
       
            // Create automator with standard configuration and killswitch
            SystmOneAutomator automator = createAutomator(killSwitch);  // Pass killSwitch here
            if (automator == null) {
                return InitialisationResult.failed("Failed to create SystmOne automator");
            }
       
            // Ensure proper application window state
            try {
                automator.focus();
            } catch (InterruptedException e) {
                return InitialisationResult.failed("Failed to focus SystmOne window: " + e.getMessage());
            }
       
            // Configure printer if enabled
            // TODO: Make this configurable via settings file
            if (ApplicationConfig.AUTO_CONFIGURE_PDF_PRINTER) {  // Add this setting to ApplicationConfig
                try {
                    if (!automator.configurePDFPrinter()) {
                        logger.warn("Failed to configure PDF printer - manual setup may be required");
                        // Continue anyway as this isn't critical
                    }
                } catch (FindFailed e) {
                    logger.warn("Failed to configure PDF printer: " + e.getMessage());
                    // Continue with initialization even if printer config fails
                }
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
            Path outputPath = Paths.get(ApplicationConfig.OUTPUT_BASE_PATH, folderName);
            Path absolutePath = outputPath.toAbsolutePath().normalize();
            
            // Create all necessary directories
            Files.createDirectories(absolutePath);
            
            logger.info("Created output directory: {}", absolutePath);
            return absolutePath.toString();
            
        } catch (Exception e) {
            logger.error("Failed to create output directory: " + e.getMessage());
            return null;
        }
    }


    /**
     * Creates and configures a SystmOneAutomator instance.
     * Uses a standard similarity threshold for all pattern matching.
     * 
     * @return Configured SystmOneAutomator instance, or null if creation fails
     */
    private SystmOneAutomator createAutomator(GlobalKillswitch killSwitch) {
        try {
            if (killSwitch == null) {
                logger.error("Null killSwitch provided to createAutomator");
                return null;
            }
            logger.debug("Creating automator with killSwitch state: {}", killSwitch.getKillSignal().get());
            SystmOneAutomator automator = new SystmOneAutomator(ApplicationConfig.DEFAULT_SIMILARITY, killSwitch);
            logger.debug("Automator created successfully with killSwitch: {}", (automator != null));
            return automator;
        } catch (FindFailed e) {
            logger.error("Failed to create automator: " + e.getMessage());
            return null;
        }
    }
}