package systmone.automation.util;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {
    private static final String LOG_BASE_PATH = "logs";
    private static LoggerContext context;

    public static void initializeLogging() {
        try {
            // Create monthly directory structure
            String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM_yyyy"));
            Path monthlyPath = Paths.get(LOG_BASE_PATH, monthYear);
            Files.createDirectories(monthlyPath);

            // Initialize Logback configuration
            context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.putProperty("CURRENT_MONTH", monthYear);
            context.putProperty("LOG_FILENAME", LogFileNameCreator.getCurrentFileName());

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            
            // Load the logback.xml from classpath
            configurator.doConfigure(LogManager.class.getResourceAsStream("/logback.xml"));
            
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Update log filename when document count is finalized
    public static void updateDocumentCount(int count) {
        if (context != null) {
            LogFileNameCreator.setDocumentCount(count);
            context.putProperty("LOG_FILENAME", LogFileNameCreator.getCurrentFileName());
        }
    }
}