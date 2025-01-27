package systmone.automation.state;

import java.util.ArrayList;
import java.util.List;

import systmone.automation.document.DocumentError;

/**
 * Tracks statistics and errors during the document processing workflow.
 * This class maintains counters for total and processed documents while
 * collecting any errors that occur during processing. It provides a central
 * point for monitoring the progress and health of document processing operations.
 * 
 * The class maintains three key pieces of state:
 * - Total number of documents to be processed
 * - Current count of processed documents
 * - List of errors encountered during processing
 * 
 * This information is used for progress tracking, error reporting, and
 * generating processing summaries.
 */
public class ProcessingStats {
    // Processing state tracking
    private int totalDocuments;
    private int processedDocuments;
    private List<DocumentError> errors;
    
    /**
     * Creates a new ProcessingStats instance with initialized error tracking.
     * Document counters start at zero until explicitly set.
     */
    public ProcessingStats() {
        this.errors = new ArrayList<>();
    }
    
    /**
     * Returns the total number of documents to be processed.
     * 
     * @return Total document count
     */
    public int getTotalDocuments() {
        return totalDocuments;
    }
    
    /**
     * Sets the total number of documents to be processed.
     * This should be set once at the start of processing.
     * 
     * @param totalDocuments Number of documents to process
     */
    public void setTotalDocuments(int totalDocuments) {
        this.totalDocuments = totalDocuments;
    }
    
    /**
     * Returns the current number of processed documents.
     * 
     * @return Count of documents processed so far
     */
    public int getProcessedDocuments() {
        return processedDocuments;
    }
    
    /**
     * Updates the count of processed documents.
     * This should be incremented as documents are successfully processed.
     * 
     * @param processedDocuments Current count of processed documents
     */
    public void setProcessedDocuments(int processedDocuments) {
        this.processedDocuments = processedDocuments;
    }
    
    /**
     * Returns the list of errors encountered during processing.
     * 
     * @return List of document processing errors
     */
    public List<DocumentError> getErrors() {
        return errors;
    }
    
    /**
     * Adds a new error to the processing error list.
     * 
     * @param error The document processing error to record
     */
    public void addError(DocumentError error) {
        this.errors.add(error);
    }
}