package systmone.automation;

public class DocumentError {
    final int documentIndex;
    final String errorMessage;
    
    DocumentError(int index, String message) {
        this.documentIndex = index;
        this.errorMessage = message;
    }
}