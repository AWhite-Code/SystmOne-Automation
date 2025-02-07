package systmone.automation.util;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
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
            // Stop any existing logging
            LoggerContext existingContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            existingContext.stop();

            // Set up our properties first
            currentMonthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM_yyyy"));
            currentFileName = "In Progress";
            
            // Create directory
            Path monthlyPath = Paths.get(LOG_BASE_PATH, currentMonthYear);
            Files.createDirectories(monthlyPath);

            // Get fresh context
            context = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // Configure properties before reset
            configureContextProperties();
            
            // Now configure
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogManager.class.getResource("/logback.xml"));
            
            isInitialized = true;
            
            // Test log
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

        } catch (Exception e) {
            System.err.println("Failed to update log filename: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void createEmergencyLog(Exception e) {
        try {
            Path emergencyPath = Paths.get("emergency_logs.txt");
            String logContent = String.format(
                "Emergency log created at %s%n" +
                "Initialization error: %s%n" +
                "Current classpath: %s%n" +
                "SLF4J binding: %s%n",
                LocalDateTime.now(),
                e.getMessage(),
                System.getProperty("java.class.path"),
                LoggerFactory.getILoggerFactory().getClass().getName()
            );
            Files.writeString(emergencyPath, logContent);
            System.err.println("Created emergency log at: " + emergencyPath.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Failed to create emergency log: " + ex.getMessage());
        }
    }
}