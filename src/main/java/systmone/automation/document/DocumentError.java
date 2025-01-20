package systmone.automation.document;

public class DocumentError {
    // These fields are private but final to maintain immutability
    private final int documentIndex;
    private final String errorMessage;
    
    // Public constructor since we need to create these from other packages
    public DocumentError(int index, String message) {
        this.documentIndex = index;
        this.errorMessage = message;
    }
    
    // Getter methods to access the private fields
    public int getDocumentIndex() {
        return documentIndex;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}