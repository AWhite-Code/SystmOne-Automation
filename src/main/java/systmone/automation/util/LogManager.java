package systmone.automation.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {
    private static final String LOG_BASE_PATH = "logs";
    private static boolean initialized = false;

    public static synchronized void initializeLogging(int documentCount) {
        // Prevent multiple initializations
        if (initialized) {
            return;
        }

        try {
            // Set document count for file naming
            LogFileNameCreator.setDocumentCount(documentCount);

            // Create monthly directory structure
            String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM_yyyy"));
            Path monthlyPath = Paths.get(LOG_BASE_PATH, monthYear);
            Files.createDirectories(monthlyPath);

            // Create full log file path
            Path logFilePath = monthlyPath.resolve(LogFileNameCreator.getCurrentFileName() + ".log");

            // Get Logback context
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // Create and configure the pattern layout
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%date{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n");
            encoder.start();

            // Configure File Appender
            FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setContext(context);
            fileAppender.setFile(logFilePath.toString());
            fileAppender.setEncoder(encoder);
            fileAppender.start();

            // Configure Console Appender
            ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
            consoleAppender.setContext(context);
            consoleAppender.setEncoder(encoder);
            consoleAppender.start();

            // Get root logger and configure it
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(Level.INFO);
            rootLogger.addAppender(fileAppender);
            rootLogger.addAppender(consoleAppender);

            // Mark as initialized
            initialized = true;

            // Log successful initialization
            rootLogger.info("Logging initialized successfully. Log file: {}", logFilePath);
            
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }
    }
}