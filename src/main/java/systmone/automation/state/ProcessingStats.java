package systmone.automation.state;

import java.util.ArrayList;
import java.util.List;

import systmone.automation.document.DocumentError;

public class ProcessingStats {
    int totalDocuments;
    int processedDocuments;
    List<DocumentError> errors;
    
    ProcessingStats() {
        this.errors = new ArrayList<>();
    }
}
