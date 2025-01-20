package systmone.automation.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import systmone.automation.state.ProcessingStats;

public class SummaryGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    
    // Make the method public so other classes can access it
    // Add static modifier if we want to call it without creating an instance
    public static void generateProcessingSummary(ProcessingStats stats) {
        
        // Validate input to prevent null pointer exceptions
        if (stats == null) {
            logger.error("Cannot generate summary: ProcessingStats object is null");
            return;
        }

        // Generate the summary
        logger.info("----------------------------------------");
        logger.info("            Processing Summary           ");
        logger.info("----------------------------------------");
        logger.info("Total Documents: {}", stats.getTotalDocuments());
        logger.info("Successfully Processed: {}", stats.getProcessedDocuments());
        logger.info("Errors Encountered: {}", stats.getErrors().size());
        
        // Calculate and display success rate
        double successRate = (stats.getTotalDocuments() > 0) 
            ? (double) stats.getProcessedDocuments() / stats.getTotalDocuments() * 100 
            : 0;
        logger.info("Success Rate: {:.2f}%", successRate);
        
        // Show detailed error information for any errors that occurred
        if (!stats.getErrors().isEmpty()) {
            logger.info("\nError Details:");
            logger.info("----------------------------------------");
            stats.getErrors().forEach(error -> 
                logger.info("Document {}: {}", 
                    error.getDocumentIndex(), 
                    error.getErrorMessage())
            );
        }
        
        logger.info("----------------------------------------");
    }
    
    // Convenience method for single error reporting
    public void logProcessingError(int documentIndex, String errorMessage) {
        logger.error("Error processing document {}: {}", documentIndex, errorMessage);
    }
}