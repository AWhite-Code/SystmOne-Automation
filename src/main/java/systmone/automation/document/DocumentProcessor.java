package systmone.automation.document;

import org.sikuli.script.FindFailed;
import org.sikuli.script.Match;
import org.sikuli.script.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import systmone.automation.config.ApplicationConfig;
import systmone.automation.state.ProcessingStats;
import systmone.automation.ui.PopupHandler;
import systmone.automation.ui.SystmOneAutomator;
import systmone.automation.ui.UiStateHandler;
import systmone.automation.util.ClipboardHelper;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the complete document processing workflow within the SystmOne automation system.
 * This class orchestrates the interaction between UI automation, document handling,
 * and navigation components to process document batches efficiently and reliably.
 * 
 * Key Responsibilities:
 * - Coordinates document identification and selection
 * - Manages document save operations with retry capability
 * - Handles inter-document navigation with performance optimization
 * - Maintains processing statistics and error tracking
 * - Supports both production and test operation modes
 * 
 * The processor implements two navigation strategies:
 * 1. Basic Navigation: Used for initial documents and fallback scenarios
 * 2. Scrollbar Tracking: Optimized navigation for better performance
 * 
 * Error handling includes:
 * - Automatic retry for save operations
 * - Popup detection and recovery
 * - Comprehensive error logging and statistics
 */
public class DocumentProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);

    private final SystmOneAutomator automator;
    private final UiStateHandler uiHandler;
    private final String outputFolder;
    private final AtomicBoolean killSwitch;
    private final ProcessingStats stats;

    /**
     * Main constructor for production use. Automatically sets testMode to false.
     */
    public DocumentProcessor(
            SystmOneAutomator automator,
            UiStateHandler uiHandler,
            String outputFolder,
            AtomicBoolean killSwitch) {
        this(automator, uiHandler, outputFolder, killSwitch, ApplicationConfig.TEST_MODE);
    }

    /**
     * Extended constructor that allows explicit control of test mode.
     * This is used internally and for testing purposes.
     */
    public DocumentProcessor(
            SystmOneAutomator automator,
            UiStateHandler uiHandler,
            String outputFolder,
            AtomicBoolean killSwitch,
            boolean testMode) {
        this.automator = automator;
        this.uiHandler = uiHandler;
        this.outputFolder = outputFolder;
        this.killSwitch = killSwitch;
        this.stats = new ProcessingStats();
        
        // Only initialize scrollbar tracking in production mode
        if (!testMode) {
            if (!uiHandler.initializeScrollbarTracking(automator.getSelectionBorderPattern())) {
                logger.warn("Failed to initialize scrollbar tracking - will use basic verification");
            }
        }
    }

    /**
     * Orchestrates the complete document processing workflow.
     * Manages document counting, individual processing operations,
     * and maintains comprehensive processing statistics.
     *
     * The workflow includes:
     * - Initial document count validation
     * - Sequential document processing with error handling
     * - Progress tracking and statistics collection
     * - Graceful handling of kill switch interruption
     *
     * @return ProcessingStats containing detailed processing results
     */
    public ProcessingStats processDocuments() {
        logger.info("Starting document processing workflow");
        
        // Get total document count and validate
        stats.setTotalDocuments(automator.getDocumentCount());
        if (stats.getTotalDocuments() <= 0) {
            logger.error("Invalid document count: {}", stats.getTotalDocuments());
            return stats;
        }
        
        logger.info("Beginning processing of {} documents", stats.getTotalDocuments());
    
        // Process each document until complete or killed
        for (int i = 0; i < stats.getTotalDocuments() && !killSwitch.get(); i++) {
            try {
                processSingleDocument(i);
                stats.setProcessedDocuments(stats.getProcessedDocuments() + 1);
                
            } catch (Exception e) {
                handleProcessingError(i + 1, e);
            }
        }
    
        if (killSwitch.get()) {
            logger.info("Processing terminated by kill switch after {} documents", 
                stats.getProcessedDocuments());
        }
    
        return stats;
    }

    /**
     * Processes a single document using an optimized approach based on
     * the current processing stage. Early documents use full stability
     * verification, while later documents can use an optimized path
     * with fallback to full verification if needed.
     *
     * The processing sequence includes:
     * - Document element detection and verification
     * - Path generation for document storage
     * - Document save operation
     * - Navigation to next document
     *
     * @param index Zero-based index of the document to process
     * @throws FindFailed if document elements cannot be located
     * @throws InterruptedException if processing is interrupted
     */
    private void processSingleDocument(int index) throws FindFailed, InterruptedException {
        int documentNumber = index + 1;
        Match documentMatch;
    
        // Only use full stability check for early documents or after failures
        if (stats.getProcessedDocuments() < ApplicationConfig.MIN_DOCUMENTS_FOR_SCROLLBAR) {
            documentMatch = uiHandler.waitForStableElement(
                automator.getSelectionBorderPattern(),
                ApplicationConfig.DIALOG_TIMEOUT
            );
        } else {
            // Fast path - just check if element exists
            documentMatch = uiHandler.quickMatchCheck(automator.getSelectionBorderPattern());
            if (documentMatch == null) {
                // Fall back to full stability check if fast check fails
                documentMatch = uiHandler.waitForStableElement(
                    automator.getSelectionBorderPattern(),
                    ApplicationConfig.DIALOG_TIMEOUT
                );
            }
        }
    
        if (documentMatch == null) {
            throw new FindFailed("Could not locate document selection border");
        }
    
        logger.info("Processing document {} of {} at coordinates ({},{})",
            documentNumber,
            stats.getTotalDocuments(),
            documentMatch.x,
            documentMatch.y
        );
    
        // Rest of the method remains the same
        String documentPath = buildDocumentPath(documentNumber);
        saveDocument(documentMatch, documentPath);
    
        if (documentNumber < stats.getTotalDocuments()) {
            handleNavigation();
        } else {
            logger.info("Reached final document - processing complete");
        }
    }

    /**
     * Generates the standardized output path for a document.
     * Creates consistent, numbered filenames within the output directory.
     */
    private String buildDocumentPath(int documentNumber) {
        return Paths.get(outputFolder, "Document" + documentNumber + ".pdf").toString();
    }

    /**
     * Manages document save operations with automatic retry capability.
     * Implements a robust save process that handles:
     * - Clipboard path management
     * - Save dialog interaction
     * - Popup detection and recovery
     * - Multiple save attempts on failure
     */
    private void saveDocument(Match documentMatch, String documentPath) 
            throws FindFailed, InterruptedException {
        int attempts = 0;
        final int MAX_SAVE_ATTEMPTS = 3;
        
        while (attempts < MAX_SAVE_ATTEMPTS) {
            attempts++;
            logger.info("Save attempt {} of {}", attempts, MAX_SAVE_ATTEMPTS);

            try {
                // Ensure clipboard has the path
                if (!ClipboardHelper.setClipboardContent(documentPath)) {
                    throw new RuntimeException("Failed to copy path to clipboard");
                }

                // Start the print/save operation
                automator.printDocument(documentMatch, documentPath);
                return;  // Success!

            } catch (FindFailed e) {
                PopupHandler popupHandler = automator.getPopupHandler();
                
                if (popupHandler.isPopupPresent()) {
                    logger.info("Popup detected during save - handling retry dialog");
                    
                    // Send Enter to accept retry
                    automator.getWindow().type(Key.ESC);
                    Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    
                    // Continue to next attempt
                    continue;
                }
                
                // If no popup found, this is an unexpected error
                throw e;
            }
        }
        
        throw new FindFailed("Save operation failed after " + MAX_SAVE_ATTEMPTS + " attempts");
    }

    /**
     * Handles inter-document navigation using the appropriate strategy.
     * Selects between basic and scrollbar-based navigation based on
     * processing progress and system capabilities.
     *
     * The method implements performance optimization by:
     * - Using simpler verification for early documents
     * - Employing scrollbar tracking for later documents
     * - Providing automatic fallback to basic navigation
     */
    private void handleNavigation() throws FindFailed, InterruptedException {
        long startTime = System.currentTimeMillis();
        
        if (stats.getProcessedDocuments() < ApplicationConfig.MIN_DOCUMENTS_FOR_SCROLLBAR) {
            logger.info("Navigation started (basic mode) at: {}", startTime);
            performBasicNavigation();
            return;
        }
    
        logger.info("Navigation started (scrollbar mode) at: {}", startTime);
        
        if (!uiHandler.startDocumentTracking()) {
            logger.warn("Scrollbar tracking failed after {} ms", System.currentTimeMillis() - startTime);
            performBasicNavigation();
            return;
        }
    
        logger.info("Tracking started, sending DOWN key at: {}", System.currentTimeMillis());
        automator.navigateDown();
        logger.info("DOWN key sent at: {}", System.currentTimeMillis());
        
        if (!uiHandler.verifyDocumentLoaded(ApplicationConfig.DIALOG_TIMEOUT)) {
            throw new FindFailed("Failed to verify document loaded after navigation");
        }
        
        logger.info("Navigation completed at: {}, total time: {} ms", 
            System.currentTimeMillis(), System.currentTimeMillis() - startTime);
    }
    
    /**
     * Provides basic document-to-document navigation functionality.
     * Used for initial documents and as a fallback when optimized
     * navigation is unavailable or fails.
     */
    private void performBasicNavigation() throws FindFailed {
        automator.navigateDown();
        Match match = uiHandler.waitForStableElement(
            automator.getSelectionBorderPattern(),
            ApplicationConfig.DIALOG_TIMEOUT
        );
        
        if (match == null) {
            throw new FindFailed("Failed to verify document after basic navigation");
        }
    }

    /**
     * Manages processing errors with comprehensive tracking and logging.
     * Creates structured error records and maintains processing statistics
     * while ensuring appropriate error visibility.
     */
    private void handleProcessingError(int documentNumber, Exception e) {
        String errorMessage = String.format("Error processing document %d: %s",
            documentNumber, e.getMessage());
        DocumentError error = new DocumentError(documentNumber, errorMessage);
        stats.addError(error);
        logger.error(errorMessage, e);
    }


    /**
     * Executes test operations for popup handling verification.
     * Provides a simplified workflow focused on popup interaction
     * testing in a controlled environment.
     */
    public void runTestOperations() throws FindFailed, InterruptedException {
        logger.info("TEST MODE: Starting popup handling verification");
        
        Match documentMatch = uiHandler.waitForStableElement(
            automator.getSelectionBorderPattern(),
            ApplicationConfig.DIALOG_TIMEOUT
        );

        if (documentMatch == null) {
            logger.error("TEST MODE: No document found for testing");
            return;
        }

        // Use actual save method with test path
        String testPath = buildDocumentPath(1);
        saveDocument(documentMatch, testPath);
    }
}