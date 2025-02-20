package systmone.automation.document;

import org.sikuli.script.FindFailed;
import org.sikuli.script.Match;
import org.sikuli.script.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import systmone.automation.config.ApplicationConfig;
import systmone.automation.killswitch.GlobalKillswitch;
import systmone.automation.state.ProcessingStats;
import systmone.automation.ui.PopupHandler;
import systmone.automation.ui.SystmOneAutomator;
import systmone.automation.ui.UiStateHandler;
import systmone.automation.util.ClipboardHelper;
import systmone.automation.util.LogManager;

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
     * Constructor for the DocumentProcessor.
     * Initializes core components and sets up scrollbar tracking for document navigation.
     *
     * @param automator Handles SystmOne application interactions
     * @param uiHandler Manages UI state and stability verification
     * @param outputFolder Directory path for saving processed documents
     * @param killSwitch Control flag for graceful shutdown
     */
    public DocumentProcessor(
        SystmOneAutomator automator,
        UiStateHandler uiHandler,
        String outputFolder,
        GlobalKillswitch killSwitch) {
            
            this.automator = automator;
            this.uiHandler = uiHandler;
            this.outputFolder = outputFolder;
            this.killSwitch = killSwitch.getKillSignal();  // Store the signal for internal use
            this.stats = new ProcessingStats();
            
            if (!uiHandler.initializeScrollbarTracking(automator.getSelectionBorderPattern())) {
                logger.warn("Failed to initialize scrollbar tracking - will use basic verification");
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
        System.out.println("DocumentProcessor: Starting document processing");
        logger.info("Starting document processing workflow");
        
        // Remove pre-counting since we're counting as we go
        int failedMoveAttempts = 0;
        
        while (!killSwitch.get() && failedMoveAttempts < 3) {
            try {
                processSingleDocument(stats.getProcessedDocuments());
                stats.setProcessedDocuments(stats.getProcessedDocuments() + 1);  // Increment right after processing
                
                // Try to move to next document
                boolean moved = handleNavigation();
                if (!moved) {
                    logger.info("Navigation failed - processing complete after {} documents", 
                        stats.getProcessedDocuments());
                    // Set final count and break out
                    stats.setTotalDocuments(stats.getProcessedDocuments());
                    break;
                }
            } catch (Exception e) {
                handleProcessingError(stats.getProcessedDocuments() + 1, e);
                logger.warn("Error during processing - will treat as end of documents");
                stats.setTotalDocuments(stats.getProcessedDocuments());
                break;
            }
        }
    
        if (killSwitch.get()) {
            logger.info("Processing terminated by kill switch after {} documents", 
                stats.getProcessedDocuments());
        }

        System.out.println("DocumentProcessor: Processing completed, updating log with final count: " + stats.getTotalDocuments());
        LogManager.updateDocumentCount(stats.getTotalDocuments());
    
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
        
        while (attempts < ApplicationConfig.MAX_SAVE_ATTEMPTS) {
            attempts++;
            logger.info("Save attempt {} of {}", attempts, ApplicationConfig.MAX_SAVE_ATTEMPTS);

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
        
        throw new FindFailed("Save operation failed after " + ApplicationConfig.MAX_SAVE_ATTEMPTS + " attempts");
    }

    /**
     * Handles inter-document navigation using the appropriate strategy.
     * Returns true if navigation was successful, false if we've reached the end
     * or maximum attempts were exhausted.
     *
     * @return boolean indicating if navigation was successful
     * @throws FindFailed if a critical navigation error occurs
     * @throws InterruptedException if navigation is interrupted
     */
    private boolean handleNavigation() throws FindFailed, InterruptedException {
        long startTime = System.currentTimeMillis();
        logger.info("Navigation started at: {}", startTime);
        
        int attempts = 0;
        
        while (attempts < ApplicationConfig.MAX_NAVIGATION_ATTEMPTS) {
            attempts++;
            logger.info("Navigation attempt {} of {}", attempts, ApplicationConfig.MAX_NAVIGATION_ATTEMPTS);
            
            // Check for popup before attempting navigation
            PopupHandler popupHandler = automator.getPopupHandler();
            if (popupHandler.isPopupPresent()) {
                logger.info("Popup detected before navigation - handling");
                popupHandler.dismissPopup(false);
                Thread.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
                continue;
            }
    
            // Determine navigation mode
            if (stats.getProcessedDocuments() < ApplicationConfig.MIN_DOCUMENTS_FOR_SCROLLBAR) {
                try {
                    // Only press Down once for basic navigation
                    automator.navigateDown();
                    Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
                    if (verifyBasicNavigation()){;
                    return true;
                    }
                } catch (FindFailed e) {
                    if (popupHandler.isPopupPresent()) {
                        logger.info("Popup detected after basic navigation failure - handling");
                        popupHandler.dismissPopup(false);
                        Thread.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
                        continue;  // Retry navigation
                    }
                    throw e;  // Rethrow if no popup found
                }
            }
    
            // Start scrollbar tracking BEFORE navigation
            if (!uiHandler.startDocumentTracking()) {
                logger.warn("Scrollbar tracking failed - falling back to basic navigation");
                try {
                    verifyBasicNavigation();
                    return true;
                } catch (FindFailed e) {
                    if (popupHandler.isPopupPresent()) {
                        logger.info("Popup detected during fallback navigation - handling");
                        popupHandler.dismissPopup(false);
                        Thread.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
                        continue;
                    }
                    throw e;
                }
            }
    
            // Now that we have baseline, perform navigation
            automator.navigateDown();
            Thread.sleep(ApplicationConfig.NAVIGATION_DELAY_MS);
    
            int verificationAttempts = 0;
            while (verificationAttempts < ApplicationConfig.MAX_VERIFICATION_ATTEMPTS) {
                verificationAttempts++;
                logger.info("Navigation verification attempt {} of {}", 
                    verificationAttempts, ApplicationConfig.MAX_VERIFICATION_ATTEMPTS);
                
                // Just verify, don't navigate again!
                if (uiHandler.verifyDocumentLoaded(ApplicationConfig.NAVIGATION_VERIFY_TIMEOUT)) {
                    logger.info("Navigation successful on verification attempt {}", verificationAttempts);
                    return true;
                }
            
                if (popupHandler.isPopupPresent()) {
                    logger.info("Popup detected during verification - handling and retrying");
                    popupHandler.dismissPopup(false);
                    Thread.sleep(ApplicationConfig.POPUP_CLEANUP_DELAY_MS);
                    verificationAttempts--;
                    continue;
                }
            
                logger.warn("Navigation verification failed without popup - retrying");
                Thread.sleep(ApplicationConfig.VERIFICATION_DELAY_MS);
            }
            
            // If we've exhausted all verification attempts for this navigation
            logger.info("Navigation verification failed after {} verification attempts", 
                ApplicationConfig.MAX_VERIFICATION_ATTEMPTS);
        }
        
        // If we've exhausted all navigation attempts
        logger.info("Navigation failed after {} attempts", attempts);
        return false;
    }
    
    /**
     * Verifies document selection using the selection border.
     * Used for initial documents and as a fallback when scrollbar tracking fails.
     */
    private boolean verifyBasicNavigation() throws FindFailed {
        Match match = uiHandler.waitForStableElement(
            automator.getSelectionBorderPattern(),
            ApplicationConfig.DIALOG_TIMEOUT
        );
        
        return match != null;
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