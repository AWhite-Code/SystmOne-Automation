package systmone.automation.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import systmone.automation.state.ProcessingStats;

public class SummaryGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SummaryGenerator.class);
    private static final String SEPARATOR = "----------------------------------------";
    private static final String FORMAT_PERCENT = "%.2f%%";

    public static void generateProcessingSummary(ProcessingStats stats) {
        if (stats == null) {
            logger.error("Cannot generate summary: ProcessingStats object is null");
            return;
        }

        logSummaryHeader();
        logProcessingStatistics(stats);
        logErrorDetails(stats);
        logger.info(SEPARATOR);
    }

    private static void logSummaryHeader() {
        logger.info(SEPARATOR);
        logger.info("            Processing Summary           ");
        logger.info(SEPARATOR);
    }

    private static void logProcessingStatistics(ProcessingStats stats) {
        logger.info("Total Documents: {}", stats.getTotalDocuments());
        logger.info("Successfully Processed: {}", stats.getProcessedDocuments());
        logger.info("Errors Encountered: {}", stats.getErrors().size());
        
        double successRate = calculateSuccessRate(stats);
        logger.info("Success Rate: {}", String.format(FORMAT_PERCENT, successRate));
    }

    private static double calculateSuccessRate(ProcessingStats stats) {
        return stats.getTotalDocuments() > 0 
            ? (double) stats.getProcessedDocuments() / stats.getTotalDocuments() * 100 
            : 0.0;
    }

    private static void logErrorDetails(ProcessingStats stats) {
        if (!stats.getErrors().isEmpty()) {
            logger.info("\nError Details:");
            logger.info(SEPARATOR);
            stats.getErrors().forEach(error -> 
                logger.info("Document {}: {}", 
                    error.getDocumentIndex(), 
                    error.getErrorMessage())
            );
        }
    }

    public static void logProcessingError(int documentIndex, String errorMessage) {
        logger.error("Error processing document {}: {}", documentIndex, errorMessage);
    }
}