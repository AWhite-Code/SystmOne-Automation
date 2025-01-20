package systmone.automation.state;

import java.util.ArrayList;
import java.util.List;

import systmone.automation.document.DocumentError;

public class ProcessingStats {
    private int totalDocuments;
    private int processedDocuments;
    private List<DocumentError> errors;
    
    public ProcessingStats() {
        this.errors = new ArrayList<>();
    }
    
    public int getTotalDocuments() {
        return totalDocuments;
    }
    
    public void setTotalDocuments(int totalDocuments) {
        this.totalDocuments = totalDocuments;
    }
    
    public int getProcessedDocuments() {
        return processedDocuments;
    }
    
    public void setProcessedDocuments(int processedDocuments) {
        this.processedDocuments = processedDocuments;
    }
    
    public List<DocumentError> getErrors() {
        return errors;
    }
    
    public void addError(DocumentError error) {
        this.errors.add(error);
    }
}
