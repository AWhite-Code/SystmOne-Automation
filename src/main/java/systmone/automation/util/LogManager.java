package systmone.automation.util;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {
    private static final String LOG_BASE_PATH = "logs";
    private static LoggerContext context;
    private static boolean isInitialized = false;
    // Add these to track our current properties
    private static String currentMonthYear;
    private static String currentFileName;

    public static void initializeLogging() {
        if (isInitialized) {
            return;
        }

        try {
            currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM_yyyy"));
            
            // Get the initial filename with timestamp from LogFileNameCreator
            String initialFileName = LogFileNameCreator.getCurrentFileName();
            
            // Create directory
            Path monthlyPath = Paths.get(LOG_BASE_PATH, currentMonthYear);
            Files.createDirectories(monthlyPath);

            // Get fresh context and configure
            context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.stop();
            
            // Set these properties BEFORE configuring
            context.putProperty("LOG_PATH", LOG_BASE_PATH);
            context.putProperty("CURRENT_MONTH", currentMonthYear);
            context.putProperty("LOG_FILENAME", initialFileName);

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogManager.class.getResource("/logback.xml"));
            
            context.start();
            isInitialized = true;
            
            Logger logger = LoggerFactory.getLogger(LogManager.class);
            logger.info("Logging system initialized in: {}", currentMonthYear);
            
        } catch (Exception e) {
            System.err.println("Failed to initialize logging system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void configureContextProperties() {
        // Helper method to ensure consistent property setting
        context.putProperty("LOG_PATH", LOG_BASE_PATH);
        context.putProperty("CURRENT_MONTH", currentMonthYear);
        context.putProperty("LOG_FILENAME", currentFileName);
    }

    public static void updateDocumentCount(int count) {
        if (!isInitialized || context == null) {
            return;
        }

        try {
            // Stop logging
            context.stop();
            Thread.sleep(100);

            // Update our tracked filename
            String oldFileName = currentFileName;
            currentFileName = String.format("%s - %d Documents", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("d.M.yy - HH.mm.ss")),
                count);

            // Define paths using our tracked properties
            Path oldFile = Paths.get(LOG_BASE_PATH, currentMonthYear, oldFileName + ".log");
            Path newFile = Paths.get(LOG_BASE_PATH, currentMonthYear, currentFileName + ".log");

            // Copy content
            if (Files.exists(oldFile)) {
                Files.copy(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(oldFile);
            }

            // Update context with new properties
            configureContextProperties();

            // Reconfigure logging
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogManager.class.getResource("/logback.xml"));
            
            context.start();
            cleanupUndefinedLogs();

        } catch (Exception e) {
            System.err.println("Failed to update log filename: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void cleanupUndefinedLogs() {
        try {
            // Path to the undefined directory
            Path undefinedDir = Paths.get(LOG_BASE_PATH, "CURRENT_MONTH_IS_UNDEFINED");
            
            // Only attempt cleanup if the directory exists
            if (Files.exists(undefinedDir)) {
                // Delete any files in the undefined directory
                Files.list(undefinedDir).forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        System.err.println("Failed to delete file: " + file);
                    }
                });
                
                // Delete the empty directory
                Files.delete(undefinedDir);
            }
        } catch (IOException e) {
            System.err.println("Failed to cleanup undefined logs: " + e.getMessage());
        }
    }


    public static void updateLogName(String newName) {
        if (!isInitialized || context == null) {
            return;
        }

        try {
            // Stop logging temporarily
            context.stop();
            Thread.sleep(100);

            Path monthlyPath = Paths.get(LOG_BASE_PATH, currentMonthYear);
            Path oldFile = Paths.get(monthlyPath.toString(), currentFileName + ".log");
            Path newFile = Paths.get(monthlyPath.toString(), newName + ".log");

            // Read content from old log
            String oldContent = "";
            if (Files.exists(oldFile)) {
                oldContent = Files.readString(oldFile);
                Files.delete(oldFile);
            }

            // Update the properties
            currentFileName = newName;
            configureContextProperties();

            // Write combined content to new file
            Files.writeString(newFile, oldContent, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            // Reconfigure logging to use new file
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogManager.class.getResource("/logback.xml"));
            
            context.start();

        } catch (Exception e) {
            System.err.println("Failed to update log name: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void finalizeLog(String status, int processedCount) {
        try {
            // Ensure we have all pending logs written
            context.stop();
            Thread.sleep(200);  // Give more time for logs to flush

            String finalFileName = String.format("%s - %d Documents%s", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("d.M.yy - HH.mm.ss")),
                processedCount,
                status != null ? " - " + status : "");

            Path monthlyPath = Paths.get(LOG_BASE_PATH, currentMonthYear);
            Path inProgressFile = Paths.get(monthlyPath.toString(), currentFileName + ".log");
            Path finalFile = Paths.get(monthlyPath.toString(), finalFileName + ".log");

            // Combine all existing logs
            StringBuilder combinedContent = new StringBuilder();
            if (Files.exists(inProgressFile)) {
                combinedContent.append(Files.readString(inProgressFile));
                Files.delete(inProgressFile);
            }

            // Write combined content to final file
            Files.writeString(finalFile, combinedContent.toString(), 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Update tracking
            currentFileName = finalFileName;
            configureContextProperties();

            // Restart logging with new configuration
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogManager.class.getResource("/logback.xml"));
            
            context.start();

        } catch (Exception e) {
            System.err.println("Failed to finalize log: " + e.getMessage());
            e.printStackTrace();
        }
    }
}