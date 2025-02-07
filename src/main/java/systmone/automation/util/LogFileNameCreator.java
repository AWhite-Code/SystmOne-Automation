package systmone.automation.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogFileNameCreator {
    private static String currentFileName;
    private static int documentCount = 0;
    private static final String IN_PROGRESS_MARKER = "In Progress";
    private static LocalDateTime startTime;
    private static final Object lock = new Object();

    static {
        startTime = LocalDateTime.now();
        createNewFileName();
    }

    public static void setDocumentCount(int count) {
        synchronized (lock) {
            System.out.println("Setting document count to: " + count);
            documentCount = count;
            createNewFileName();
        }
    }

    public static String getCurrentFileName() {
        synchronized (lock) {
            if (currentFileName == null) {
                System.out.println("Warning: Current filename was null, creating new filename");
                createNewFileName();
            }
            return currentFileName;
        }
    }

    private static void createNewFileName() {
        synchronized (lock) {
            String date = startTime.format(DateTimeFormatter.ofPattern("d.M.yy"));
            String time = startTime.format(DateTimeFormatter.ofPattern("HH.mm.ss"));
            
            String newFileName;
            if (documentCount > 0) {
                newFileName = String.format("%s - %s - %d Documents", date, time, documentCount);
            } else {
                newFileName = String.format("%s - %s - %s", date, time, IN_PROGRESS_MARKER);
            }
            
            System.out.println("Created new filename: " + newFileName);
            currentFileName = newFileName;
            
            // Create a test file to verify write permissions
            try {
                Path testPath = Paths.get("logs", "test.txt");
                java.nio.file.Files.writeString(testPath, "Test file creation at " + LocalDateTime.now());
                System.out.println("Successfully created test file at: " + testPath.toAbsolutePath());
                java.nio.file.Files.delete(testPath);
            } catch (Exception e) {
                System.err.println("Warning: Could not create test file: " + e.getMessage());
            }
        }
    }
}