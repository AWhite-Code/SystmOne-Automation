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
        logger.info("Total Documents: {}", stats.totalDocuments);
        logger.info("Successfully Processed: {}", stats.processedDocuments);
        logger.info("Errors Encountered: {}", stats.errors.size());
        
        // Calculate and display success rate
        double successRate = (stats.totalDocuments > 0) 
            ? (double) stats.processedDocuments / stats.totalDocuments * 100 
            : 0;
        logger.info("Success Rate: {:.2f}%", successRate);
        
        // Show detailed error information for any errors that occurred
        if (!stats.errors.isEmpty()) {
            logger.info("\nError Details:");
            logger.info("----------------------------------------");
            stats.errors.forEach(error -> 
                logger.info("Document {}: {}", 
                    error.documentIndex, 
                    error.errorMessage)
            );
        }
        
        logger.info("----------------------------------------");
    }
    
    // Convenience method for single error reporting
    public void logProcessingError(int documentIndex, String errorMessage) {
        logger.error("Error processing document {}: {}", documentIndex, errorMessage);
    }
}