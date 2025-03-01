package systmone.automation.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.kwhat.jnativehook.NativeHookException;

import systmone.automation.document.DocumentProcessor;
import systmone.automation.state.InitialisationResult;
import systmone.automation.state.ProcessingStats;
import systmone.automation.util.LogManager;
import systmone.automation.util.SummaryGenerator;
import systmone.automation.killswitch.GlobalKillswitch;

/**
 * Main entry point for the SystmOne Document Processing Application.
 * This class orchestrates the complete document processing workflow,
 * from system initialization through document processing to final cleanup.
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static GlobalKillswitch killswitch;

    /**
     * Main entry point for the application. Coordinates the complete
     * document processing workflow with proper error handling and cleanup.
     */
    public static void main(String[] args) {
        LogManager.initializeLogging();
        ProcessingStats stats = null;
        boolean initializationSuccessful = false;
        boolean processingStarted = false;  // Add new flag for processing state
    
        try {
            // Initialize killswitch first
            try {
                killswitch = GlobalKillswitch.initialize();
                logger.info("Global killswitch initialized (F5 to terminate)");
                logger.debug("KillSwitch signal state: {}", killswitch.getKillSignal().get());
            } catch (NativeHookException e) {
                logger.error("Failed to initialize global killswitch: {}", e.getMessage());
                return;
            }
    
            Thread.sleep(100);
    
            SystemInitialiser initialiser = new SystemInitialiser();
    
            logger.debug("Passing killswitch to initialiser: {}", killswitch.getKillSignal().get());
            InitialisationResult initResult = initialiser.initialise(killswitch);
    
            if (!initResult.isSuccess()) {
                logger.error("System initialization failed: {}", initResult.getErrorMessage());
                return;
            }
    
            initializationSuccessful = true;
    
            // Initialize document processor with required components
            SystemComponents components = initResult.getComponents();
            DocumentProcessor processor = new DocumentProcessor(
                components.getAutomator(),
                components.getUiHandler(),
                components.getOutputFolder(),
                killswitch
            );
    
            logger.info("Running in production mode - full document processing");
            processingStarted = true;  // Set flag before processing starts
            stats = processor.processDocuments();
    
            if (killswitch.isKillSignalReceived()) {
                logger.info("Kill signal received, initiating shutdown...");
                return;
            }
    
        } catch (Exception e) {
            logger.error("Critical application failure: {}", e.getMessage(), e);
        } 
        finally {
            try {
                if (killswitch != null) {
                    if (initializationSuccessful) {
                        killswitch.cleanup();
                    }
                }
                // Only generate summary if we actually started processing
                if (processingStarted && stats != null) {
                    logger.info("Generating processing summary");
                    SummaryGenerator.generateProcessingSummary(stats);
                }
                logger.info("Application shutdown complete");
            } finally {
                System.exit(initializationSuccessful ? 0 : 1);
            }
        }
    }
}