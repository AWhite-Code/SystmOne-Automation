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

    public static void initializeLogging() {
        if (isInitialized) {
            System.out.println("LogManager already initialized");
            return;
        }

        System.out.println("Starting LogManager initialization...");
        
        try {
            // Verify we're using Logback
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
                throw new RuntimeException("SLF4J is not using Logback. Current binding: " + 
                    LoggerFactory.getILoggerFactory().getClass().getName());
            }
            
            // Create monthly directory structure
            String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM_yyyy"));
            Path monthlyPath = Paths.get(LOG_BASE_PATH, monthYear);
            Path absoluteMonthlyPath = monthlyPath.toAbsolutePath();
            
            // Create directories if they don't exist
            Files.createDirectories(absoluteMonthlyPath);
            System.out.println("Created/verified log directory at: " + absoluteMonthlyPath);
            
            // Get and configure Logback context
            context = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // Set properties needed by logback.xml
            context.putProperty("LOG_PATH", LOG_BASE_PATH);  // Using relative path as defined in logback.xml
            context.putProperty("CURRENT_MONTH", monthYear);
            context.putProperty("LOG_FILENAME", LogFileNameCreator.getCurrentFileName());

            // Load and apply configuration
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            
            // Reset context but maintain properties
            context.reset();
            
            // Reapply properties after reset
            context.putProperty("LOG_PATH", LOG_BASE_PATH);
            context.putProperty("CURRENT_MONTH", monthYear);
            context.putProperty("LOG_FILENAME", LogFileNameCreator.getCurrentFileName());

            // Verify and load logback.xml
            URL logbackConfig = LogManager.class.getResource("/logback.xml");
            if (logbackConfig == null) {
                throw new RuntimeException("Could not find logback.xml in classpath");
            }
            
            configurator.doConfigure(logbackConfig);
            isInitialized = true;
            
            // Test log output
            org.slf4j.Logger logger = LoggerFactory.getLogger(LogManager.class);
            logger.info("Logging system initialized successfully");
            logger.info("Log file path: {}/{}/{}.log", 
                LOG_BASE_PATH, monthYear, LogFileNameCreator.getCurrentFileName());
            
        } catch (Exception e) {
            System.err.println("Failed to initialize logging system: " + e.getMessage());
            e.printStackTrace();
            //createEmergencyLog(e);
        }
    }

    public static void updateDocumentCount(int count) {
        System.out.println("Updating document count to: " + count);
        
        if (!isInitialized) {
            System.err.println("Warning: Attempting to update document count before initialization");
            initializeLogging();
        }
        
        if (context != null) {
            try {
                // Update filename through LogFileNameCreator
                LogFileNameCreator.setDocumentCount(count);
                String updatedFileName = LogFileNameCreator.getCurrentFileName();
                String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM_yyyy"));
                
                // Define our file paths
                Path oldFile = Paths.get(LOG_BASE_PATH, monthYear, "In Progress.log");
                Path newFile = Paths.get(LOG_BASE_PATH, monthYear, updatedFileName + ".log");
                
                // First, stop the logging context to release file handles
                context.stop();
                Thread.sleep(100); // Brief pause to ensure file handles are released
                
                // Copy the contents from the old file to the new one
                if (Files.exists(oldFile)) {
                    String logContent = Files.readString(oldFile);
                    Files.writeString(newFile, logContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    Files.delete(oldFile); // Remove the old file after successful copy
                    System.out.println("Successfully transferred log contents to new file: " + newFile);
                } else {
                    System.err.println("Warning: Could not find original log file at: " + oldFile);
                }
                
                // Update logging configuration to use new file
                context.putProperty("LOG_FILENAME", updatedFileName);
                
                // Reconfigure and restart logging
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(context);
                configurator.doConfigure(LogManager.class.getResource("/logback.xml"));
                context.start();
                
                // Log the update completion
                Logger logger = LoggerFactory.getLogger(LogManager.class);
                logger.info("Log file renamed to reflect final document count: {}", count);
                
            } catch (Exception e) {
                System.err.println("Failed to update log filename: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Warning: Logger context is null during document count update");
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