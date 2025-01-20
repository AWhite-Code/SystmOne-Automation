package systmone.automation.state;

public class AutomationState {
    // Represents different stages in the document processing workflow
    public enum ProcessingStage {
        DOCUMENT_SELECTION,    // Selecting the document in the list
        CONTEXT_MENU_OPEN,    // Right-click menu is open
        PRINT_DIALOG_OPEN,    // Print dialog is visible
        SAVE_DIALOG_OPEN,     // Save dialog is visible
        DOCUMENT_SAVING,      // Document is being saved
        NAVIGATION_PENDING    // About to move to next document
    }
    
    private int currentDocumentIndex;
    private ProcessingStage currentStage;
    private String currentDocumentPath;
    private boolean interrupted;
    
    public AutomationState() {
        this.currentDocumentIndex = 0;
        this.currentStage = ProcessingStage.DOCUMENT_SELECTION;
        this.interrupted = false;
    }
    
    // Getter and setter methods
    public ProcessingStage getCurrentStage() {
        return currentStage;
    }
    
    public void setCurrentStage(ProcessingStage stage) {
        this.currentStage = stage;
    }
    
    public int getCurrentDocumentIndex() {
        return currentDocumentIndex;
    }
    
    public void setCurrentDocumentIndex(int index) {
        this.currentDocumentIndex = index;
    }
    
    public String getCurrentDocumentPath() {
        return currentDocumentPath;
    }
    
    public void setCurrentDocumentPath(String path) {
        this.currentDocumentPath = path;
    }
    
    public boolean isInterrupted() {
        return interrupted;
    }
    
    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }
    
    // Helper method to create a snapshot of current state
    public AutomationState createSnapshot() {
        AutomationState snapshot = new AutomationState();
        snapshot.currentDocumentIndex = this.currentDocumentIndex;
        snapshot.currentStage = this.currentStage;
        snapshot.currentDocumentPath = this.currentDocumentPath;
        snapshot.interrupted = this.interrupted;
        return snapshot;
    }
}