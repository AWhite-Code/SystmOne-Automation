package systmone.automation.document;

/**
 * Represents an error that occurred during document processing.
 * This class is designed to be immutable to ensure thread safety and
 * maintain data integrity throughout the error reporting process.
 * 
 * Each error instance captures two essential pieces of information:
 * - The index of the document where the error occurred
 * - A descriptive message explaining the error
 * 
 * The immutable design ensures that error information cannot be
 * modified after creation, providing reliable error tracking and
 * reporting capabilities.
 */
public class DocumentError {
    // Immutable error state
    private final int documentIndex;
    private final String errorMessage;
    
    /**
     * Creates a new document processing error.
     * 
     * @param index Zero-based index of the document that encountered the error
     * @param message Descriptive explanation of what went wrong
     * @throws IllegalArgumentException if message is null or empty
     */
    public DocumentError(int index, String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty");
        }
        
        this.documentIndex = index;
        this.errorMessage = message;
    }
    
    /**
     * Returns the index of the document that encountered the error.
     * 
     * @return Zero-based document index
     */
    public int getDocumentIndex() {
        return documentIndex;
    }
    
    /**
     * Returns the description of the error that occurred.
     * 
     * @return Error message describing what went wrong
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}