package systmone.automation.state;

/**
 * Manages and tracks the current state of the document processing automation workflow.
 * This class provides a centralized way to monitor the automation's progress through
 * different processing stages while maintaining contextual information about the
 * current document being processed.
 * 
 * The state tracking includes:
 * - Current processing stage in the workflow
 * - Document being processed and its details
 * - Workflow interruption status
 * - Support for state snapshots for recovery scenarios
 * 
 * This state management ensures the automation can handle interruptions gracefully
 * and maintain consistency throughout the document processing workflow.
 */
public class AutomationState {
    /**
     * Defines the distinct stages in the document processing workflow.
     * Each stage represents a specific point in the processing sequence
     * where different UI elements and actions are expected.
     */
    public enum ProcessingStage {
        /** Initial stage where document is being selected from the list */
        DOCUMENT_SELECTION,    
        
        /** Right-click context menu has been opened on the document */
        CONTEXT_MENU_OPEN,    
        
        /** Print dialog is currently displayed and active */
        PRINT_DIALOG_OPEN,    
        
        /** Save dialog is currently displayed and active */
        SAVE_DIALOG_OPEN,     
        
        /** Document save operation is in progress */
        DOCUMENT_SAVING,      
        
        /** System is preparing to move to the next document */
        NAVIGATION_PENDING    
    }
    
    // State tracking fields
    private int currentDocumentIndex;
    private ProcessingStage currentStage;
    private String currentDocumentPath;
    private boolean interrupted;
    
    /**
     * Creates a new automation state instance with initial values.
     * Sets the starting stage to document selection and initializes
     * tracking variables.
     */
    public AutomationState() {
        this.currentDocumentIndex = 0;
        this.currentStage = ProcessingStage.DOCUMENT_SELECTION;
        this.interrupted = false;
    }
    
    /**
     * Returns the current processing stage.
     * 
     * @return Current stage in the workflow
     */
    public ProcessingStage getCurrentStage() {
        return currentStage;
    }
    
    /**
     * Updates the current processing stage.
     * 
     * @param stage New processing stage
     */
    public void setCurrentStage(ProcessingStage stage) {
        this.currentStage = stage;
    }
    
    /**
     * Returns the index of the current document being processed.
     * 
     * @return Zero-based index of current document
     */
    public int getCurrentDocumentIndex() {
        return currentDocumentIndex;
    }
    
    /**
     * Updates the current document index.
     * 
     * @param index New document index
     */
    public void setCurrentDocumentIndex(int index) {
        this.currentDocumentIndex = index;
    }
    
    /**
     * Returns the file path for the current document.
     * 
     * @return Current document's save path
     */
    public String getCurrentDocumentPath() {
        return currentDocumentPath;
    }
    
    /**
     * Updates the current document's file path.
     * 
     * @param path New document path
     */
    public void setCurrentDocumentPath(String path) {
        this.currentDocumentPath = path;
    }
    
    /**
     * Checks if the automation process has been interrupted.
     * 
     * @return true if process was interrupted, false otherwise
     */
    public boolean isInterrupted() {
        return interrupted;
    }
    
    /**
     * Updates the interrupted status of the automation process.
     * 
     * @param interrupted New interrupted status
     */
    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }
    
    /**
     * Creates a complete copy of the current automation state.
     * This snapshot can be used for state recovery or progress tracking.
     * 
     * @return New AutomationState instance with copied values
     */
    public AutomationState createSnapshot() {
        AutomationState snapshot = new AutomationState();
        snapshot.currentDocumentIndex = this.currentDocumentIndex;
        snapshot.currentStage = this.currentStage;
        snapshot.currentDocumentPath = this.currentDocumentPath;
        snapshot.interrupted = this.interrupted;
        return snapshot;
    }
}