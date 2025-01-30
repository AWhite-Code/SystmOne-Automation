package systmone.automation.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import systmone.automation.document.DocumentProcessor;
import systmone.automation.state.InitialisationResult;
import systmone.automation.state.ProcessingStats;
import systmone.automation.util.SummaryGenerator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the SystmOne Document Processing Application.
 * This class orchestrates the complete document processing workflow,
 * from system initialization through document processing to final cleanup.
 * 
 * Key Responsibilities:
 * - Coordinates system initialization and component setup
 * - Manages the transition between initialization and processing
 * - Handles different operational modes (test/production)
 * - Ensures graceful shutdown and cleanup
 * - Coordinates summary generation and reporting
 * 
 * The application supports two operational modes:
 * 1. Production Mode: Processes all documents with full error tracking
 * 2. Test Mode: Runs simplified operations for popup handling verification
 * 
 * The workflow is designed to maintain stability through:
 * - Comprehensive error handling at all stages
 * - Graceful shutdown capability via kill switch
 * - Proper resource cleanup in all scenarios
 * - Complete operation reporting through logs and summaries
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    // Thread-safe kill switch for graceful shutdown
    private static final AtomicBoolean killSwitch = new AtomicBoolean(false);

    /**
     * Main entry point for the application. Coordinates the complete
     * document processing workflow with proper error handling and cleanup.
     * 
     * The workflow consists of:
     * 1. System initialization
     * 2. Kill switch setup (production mode only)
     * 3. Document processor creation and configuration
     * 4. Document processing execution
     * 5. Summary generation and cleanup
     * 
     * @param args Command line arguments (not currently used)
     */
    public static void main(String[] args) {
        logger.info("Starting SystmOne Document Processing Application");
        ProcessingStats stats = null;

        try {
            // Initialize system components with validation
            SystemInitialiser initialiser = new SystemInitialiser();
            InitialisationResult initResult = initialiser.initialise();

            if (!initResult.isSuccess()) {
                logger.error("System initialization failed: {}", initResult.getErrorMessage());
                return;
            }

            setupKillSwitch();

            // Initialize document processor with required components
            SystemComponents components = initResult.getComponents();
            DocumentProcessor processor = new DocumentProcessor(
                components.getAutomator(),
                components.getUiHandler(),
                components.getOutputFolder(),
                killSwitch
            );

            logger.info("Running in production mode - full document processing");
            stats = processor.processDocuments();

        } catch (Exception e) {
            logger.error("Critical application failure: {}", e.getMessage(), e);
        } finally {
                logger.info("Generating processing summary");
                SummaryGenerator.generateProcessingSummary(stats);
            }
            logger.info("Application shutdown complete");
        }

    /**
     * Initializes the kill switch monitoring system for graceful shutdown.
     * Creates a daemon thread that monitors the kill switch state and
     * allows the application to terminate safely when requested.
     * 
     * The monitoring thread runs at low priority and checks the kill
     * switch state every 100ms to balance responsiveness with resource usage.
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