package systmone.automation;

import org.sikuli.script.FindFailed;
import org.sikuli.script.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.config.ApplicationConfig;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the core document processing workflow for SystmOne automation.
 * This class coordinates between the UI automation, document saving,
 * and navigation components to process a batch of documents.
 */
public class DocumentProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);

    private final SystmOneAutomator automator;
    private final UiStateHandler uiHandler;
    private final String outputFolder;
    private final AtomicBoolean killSwitch;
    private final ProcessingStats stats;

    public DocumentProcessor(
            SystmOneAutomator automator,
            UiStateHandler uiHandler,
            String outputFolder,
            AtomicBoolean killSwitch) {
        this.automator = automator;
        this.uiHandler = uiHandler;
        this.outputFolder = outputFolder;
        this.killSwitch = killSwitch;
        this.stats = new ProcessingStats();
        
        // Initialize scrollbar tracking if possible
        if (!uiHandler.initializeScrollbarTracking(automator.getSelectionBorderPattern())) {
            logger.warn("Failed to initialize scrollbar tracking - will use basic verification");
        }
    }

    /**
     * Initiates the document processing workflow.
     * This method coordinates the entire process of counting documents,
     * processing each one, and maintaining processing statistics.
     *
     * @return ProcessingStats containing the results of the processing run
     */
    public ProcessingStats processDocuments() {
        logger.info("Starting document processing workflow");
        
        // Get total document count and validate
        stats.totalDocuments = automator.getDocumentCount();
        if (stats.totalDocuments <= 0) {
            logger.error("Invalid document count: {}", stats.totalDocuments);
            return stats;
        }
        
        logger.info("Beginning processing of {} documents", stats.totalDocuments);

        // Process each document until complete or killed
        for (int i = 0; i < stats.totalDocuments && !killSwitch.get(); i++) {
            try {
                processSingleDocument(i);
                stats.processedDocuments++;
                
            } catch (Exception e) {
                handleProcessingError(i + 1, e);
            }
        }

        if (killSwitch.get()) {
            logger.info("Processing terminated by kill switch after {} documents", 
                stats.processedDocuments);
        }

        return stats;
    }

    /**
     * Processes a single document at the specified index.
     * This includes identifying the document on screen, saving it,
     * and navigating to the next document if applicable.
     *
     * @param index The zero-based index of the document being processed
     */
    private void processSingleDocument(int index) throws FindFailed, InterruptedException {
        // Wait for the document to be stable on screen
        Match documentMatch = uiHandler.waitForStableElement(
            automator.getSelectionBorderPattern(),
            ApplicationConfig.DIALOG_TIMEOUT
        );

        int documentNumber = index + 1;
        logger.info("Processing document {} of {} at coordinates ({},{})",
            documentNumber,
            stats.totalDocuments,
            documentMatch.x,
            documentMatch.y
        );

        // Prepare the save path and process the document
        String documentPath = buildDocumentPath(documentNumber);
        saveDocument(documentMatch, documentPath);

        // Navigate to next document if not on the last one
        if (documentNumber < stats.totalDocuments) {
            handleNavigation();
        } else {
            logger.info("Reached final document - processing complete");
        }
    }

    /**
     * Builds the full path for saving the current document.
     * Creates a standardized filename based on the document number.
     */
    private String buildDocumentPath(int documentNumber) {
        return Paths.get(outputFolder, "Document" + documentNumber + ".pdf").toString();
    }

    /**
     * Handles the actual saving of the document to disk.
     * Coordinates clipboard operations and UI interactions for saving.
     */
    private void saveDocument(Match documentMatch, String documentPath) 
            throws FindFailed {
        if (!ClipboardHelper.setClipboardContent(documentPath)) {
            throw new RuntimeException("Failed to copy document path to clipboard");
        }

        automator.printDocument(documentMatch, documentPath);
    }

    /**
     * Manages navigation between documents, using either basic or scrollbar verification
     * depending on the current document number.
     */
    private void handleNavigation() throws FindFailed, InterruptedException {
        // For early documents, use basic verification
        if (stats.processedDocuments < ApplicationConfig.MIN_DOCUMENTS_FOR_SCROLLBAR) {
            performBasicNavigation();
            return;
        }

        // Attempt scrollbar tracking for later documents
        if (!uiHandler.startDocumentTracking()) {
            logger.warn("Scrollbar tracking failed, falling back to basic verification");
            performBasicNavigation();
            return;
        }

        // Navigate with scrollbar verification
        automator.navigateDown();
        if (!uiHandler.verifyDocumentLoaded(ApplicationConfig.DIALOG_TIMEOUT)) {
            throw new FindFailed("Failed to verify document loaded after navigation");
        }
    }

    /**
     * Performs basic navigation without scrollbar tracking.
     * Used for early documents or as a fallback when scrollbar tracking fails.
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
     * Handles errors that occur during document processing.
     * Records the error in the processing stats and logs it appropriately.
     */
    private void handleProcessingError(int documentNumber, Exception e) {
        String errorMessage = String.format("Error processing document %d: %s",
            documentNumber, e.getMessage());
        stats.errors.add(new DocumentError(documentNumber, errorMessage));
        logger.error(errorMessage, e);
    }
}