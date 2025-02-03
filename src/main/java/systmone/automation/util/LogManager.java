package systmone.automation.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {
    private static final String LOG_BASE_PATH = "logs";
    private static boolean initialized = false;
    private static Path currentLogFile = null;
    private static FileAppender<ILoggingEvent> currentFileAppender = null;
    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM_yyyy");

    // This method is called at application start, before we know the document count
    public static synchronized void initializeLogging() {
        // If already initialized, don't do it again
        if (initialized) {
            return;
        }

        try {
            // Get Logback context and reset existing configuration
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.reset();

            // Create monthly directory structure
            String monthYear = LocalDateTime.now().format(MONTH_YEAR_FORMATTER);
            Path monthlyPath = Paths.get(LOG_BASE_PATH, monthYear);
            Files.createDirectories(monthlyPath);

            // Create a temporary log file name without document count
            String tempFileName = createTemporaryFileName();
            Path logFile = monthlyPath.resolve(tempFileName + ".log");
            currentLogFile = logFile;

            // Set up basic logging configuration
            setupLoggingConfiguration(context, logFile.toString());
            
            initialized = true;

            // Log initialization success
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.info("Initial logging system configured");
            rootLogger.info("Temporary log file location: {}", logFile);

        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // This method is called once we know the document count
    public static synchronized void updateLoggingWithDocumentCount(int documentCount) {
        if (!initialized) {
            // If not initialized yet, do full initialization with document count
            initializeLogging();
        }

        try {
            // Get current log content before reconfiguring
            String existingLogs = "";
            if (currentLogFile != null && Files.exists(currentLogFile)) {
                existingLogs = Files.readString(currentLogFile);
            }

            // Set up new log file with document count
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            String monthYear = LocalDateTime.now().format(MONTH_YEAR_FORMATTER);
            Path monthlyPath = Paths.get(LOG_BASE_PATH, monthYear);
            
            // Create the proper filename with document count
            LogFileNameCreator.setDocumentCount(documentCount);
            Path newLogFile = monthlyPath.resolve(LogFileNameCreator.getCurrentFileName() + ".log");

            // Reconfigure logging to use new file
            setupLoggingConfiguration(context, newLogFile.toString());

            // Copy existing logs to new file
            if (!existingLogs.isEmpty()) {
                Files.writeString(newLogFile, existingLogs);
            }

            // Clean up old log file
            if (currentLogFile != null && !currentLogFile.equals(newLogFile)) {
                Files.deleteIfExists(currentLogFile);
            }

            currentLogFile = newLogFile;

            // Log the update
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.info("Logging system updated with document count: {}", documentCount);
            rootLogger.info("Final log file location: {}", newLogFile);

        } catch (Exception e) {
            System.err.println("Failed to update logging configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String createTemporaryFileName() {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("d.M.yy"));
        String time = now.format(DateTimeFormatter.ofPattern("HH.mm.ss"));
        return String.format("%s - %s - Initializing", date, time);
    }

    private static void setupLoggingConfiguration(LoggerContext context, String logFilePath) {
        // Create and configure the pattern layout
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%date{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        // Configure file appender
        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setFile(logFilePath);
        fileAppender.setEncoder(encoder);
        fileAppender.setAppend(true);
        fileAppender.setImmediateFlush(true);
        fileAppender.start();

        // Configure console appender
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        // Update root logger
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.detachAndStopAllAppenders();
        rootLogger.addAppender(fileAppender);
        rootLogger.addAppender(consoleAppender);

        // Store current file appender for later cleanup
        if (currentFileAppender != null) {
            currentFileAppender.stop();
        }
        currentFileAppender = fileAppender;
    }
}