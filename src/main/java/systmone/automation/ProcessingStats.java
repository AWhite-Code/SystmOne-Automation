package systmone.automation;

import java.util.ArrayList;
import java.util.List;

public class ProcessingStats {
    int totalDocuments;
    int processedDocuments;
    List<DocumentError> errors;
    
    ProcessingStats() {
        this.errors = new ArrayList<>();
    }
}
