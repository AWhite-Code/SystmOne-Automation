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
 * Handles the complete initialization process for the SystmOne automation system.
 * This class manages the sequential setup of all required components, ensuring each
 * step is completed successfully before proceeding to the next.
 */
public class SystemInitialiser {
    private static final Logger logger = LoggerFactory.getLogger(SystemInitialiser.class);

    /**
     * Performs the complete system initialization process.
     * Each step is executed in sequence, with proper error handling and reporting.
     */
    public InitialisationResult initialise() {
        try {
            // Step 1: Initialize image library
            if (!initializeImageLibrary()) {
                return InitialisationResult.failed("Image library initialization failed");
            }

            // Step 2: Determine location
            ApplicationConfig.Location location = determineLocation();
            if (location == null) {
                return InitialisationResult.failed("Location determination failed");
            }

            // Step 3: Create and initialize automator
            SystmOneAutomator automator = createAutomator(location);
            if (automator == null) {
                return InitialisationResult.failed("Failed to create SystmOne automator");
            }

            try {
                automator.focus();
            } catch (InterruptedException e) {
                return InitialisationResult.failed("Failed to focus SystmOne window: " + e.getMessage());
            }

            // Step 4: Create output directory
            String outputFolder = initializeOutputDirectory();
            if (outputFolder == null) {
                return InitialisationResult.failed("Failed to create output directory");
            }

            // Step 5: Create system components
            // Note: We no longer create UiStateHandler here - SystemComponents handles that
            SystemComponents components = new SystemComponents(automator, outputFolder);
            
            // Step 6: Verify complete initialization
            if (components.getUiHandler() == null || components.getPopupHandler() == null) {
                return InitialisationResult.failed("Failed to initialize all required components");
            }

            return InitialisationResult.succeeded(components);

        } catch (Exception e) {
            return InitialisationResult.failed("Unexpected error during initialization: " + e.getMessage());
        }
    }

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