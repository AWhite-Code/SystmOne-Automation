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
    
        // Add shutdown hook first thing
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered, cleaning up resources...");
            if (killswitch != null) {
                killswitch.cleanup();
            }
        }));
    
        try {
            // Initialize killswitch first
            try {
                killswitch = GlobalKillswitch.initialize();
                logger.info("Global killswitch initialized (F10 to terminate)");
            } catch (NativeHookException e) {
                logger.error("Failed to initialize global killswitch: {}", e.getMessage());
                System.exit(1);
            }
    
            // Initialize system components with validation
            SystemInitialiser initialiser = new SystemInitialiser();
            InitialisationResult initResult = initialiser.initialise(killswitch.getKillSignal());
    
            if (!initResult.isSuccess()) {
                logger.error("System initialization failed: {}", initResult.getErrorMessage());
                System.exit(1);
            }
    
            // Initialize document processor with required components
            SystemComponents components = initResult.getComponents();
            DocumentProcessor processor = new DocumentProcessor(
                components.getAutomator(),
                components.getUiHandler(),
                components.getOutputFolder(),
                killswitch.getKillSignal()
            );
    
            logger.info("Running in production mode - full document processing");
            stats = processor.processDocuments();
    
            // Check if killswitch was triggered during processing
            if (killswitch.isKillSignalReceived()) {
                logger.info("Kill signal received, initiating shutdown...");
                System.exit(0);
            }
    
        } catch (Exception e) {
            logger.error("Critical application failure: {}", e.getMessage(), e);
            System.exit(1);
        } finally {
            if (killswitch != null) {
                killswitch.cleanup();
            }
            logger.info("Generating processing summary");
            SummaryGenerator.generateProcessingSummary(stats);
            logger.info("Application shutdown complete");
            // Force exit after cleanup
            System.exit(0);
        }
    }
}