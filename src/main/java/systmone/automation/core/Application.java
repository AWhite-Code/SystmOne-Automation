package systmone.automation.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import systmone.automation.config.ApplicationConfig;
import systmone.automation.document.DocumentProcessor;
import systmone.automation.state.InitialisationResult;
import systmone.automation.state.ProcessingStats;
import systmone.automation.util.SummaryGenerator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the SystmOne Document Processing Application.
 * This class coordinates the high-level workflow of the application,
 * managing initialization, document processing, and graceful shutdown.
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final AtomicBoolean killSwitch = new AtomicBoolean(false);
public static void main(String[] args) {
    logger.info("Starting SystmOne Document Processing Application");
    ProcessingStats stats = null;

    try {
        // Initialize the system using our new initializer
        SystemInitialiser initialiser = new SystemInitialiser();
        InitialisationResult initResult = initialiser.initialise();

        if (!initResult.isSuccess()) {
            logger.error("System initialization failed: {}", initResult.getErrorMessage());
            return;
        }

        // Set up monitoring for graceful shutdown - only in production mode
        if (!ApplicationConfig.TEST_MODE) {
            setupKillSwitch();
        }

        // Create document processor with initialized components
        SystemComponents components = initResult.getComponents();
        DocumentProcessor processor = new DocumentProcessor(
            components.getAutomator(),
            components.getUiHandler(),
            components.getOutputFolder(),
            killSwitch
        );

        // Process based on mode
        if (ApplicationConfig.TEST_MODE) {
            logger.info("Running in test mode - popup handling test only");
            processor.runTestOperations();
        } else {
            logger.info("Running in production mode - full document processing");
            stats = processor.processDocuments();
        }

    } catch (Exception e) {
        logger.error("Critical application failure: {}", e.getMessage(), e);
    } finally {
        // Only generate summary in production mode
        if (!ApplicationConfig.TEST_MODE && stats != null) {
            logger.info("Generating processing summary");
            SummaryGenerator.generateProcessingSummary(stats);
        }
        logger.info("Application shutdown complete");
        }
    }

    /**
     * Sets up a daemon thread to monitor the kill switch for graceful shutdown.
     * This allows the application to be terminated safely when requested.
     */
    private static void setupKillSwitch() {
        Thread killSwitchThread = new Thread(() -> {
            while (!killSwitch.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        killSwitchThread.setDaemon(true);
        killSwitchThread.start();
        logger.info("Kill switch monitoring initialized");
    }
}