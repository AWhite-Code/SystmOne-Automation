package systmone.automation.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
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
            if (count >= 0) {
                documentCount = count;
                createNewFileName();
            }
        }
    }

    public static String getCurrentFileName() {
        synchronized (lock) {
            if (currentFileName == null) {
                createNewFileName();
            }
            return currentFileName;
        }
    }

    public static void finalizeLog(String status) {
        synchronized (lock) {
            String date = startTime.format(DateTimeFormatter.ofPattern("d.M.yy"));
            String time = startTime.format(DateTimeFormatter.ofPattern("HH.mm.ss"));
            
            String newFileName = String.format("%s - %s - %d Documents%s", 
                date, 
                time,
                Math.max(0, documentCount),             // Ensure non-negative
                status != null ? " - " + status : ""
            );
            
            currentFileName = newFileName;
            LogManager.updateLogName(newFileName);
        }
    }
    
    private static void createNewFileName() {
        synchronized (lock) {
            String date = startTime.format(DateTimeFormatter.ofPattern("d.M.yy"));
            String time = startTime.format(DateTimeFormatter.ofPattern("HH.mm.ss"));
            
            currentFileName = String.format("%s - %s - %s", 
                date, 
                time, 
                documentCount > 0 ? documentCount + " Documents" : IN_PROGRESS_MARKER
            );
            
            verifyWritePermissions();
        }
    }

    private static void verifyWritePermissions() {
        try {
            Path testPath = Paths.get("logs", "test.txt");
            Files.writeString(testPath, "Test file creation at " + LocalDateTime.now());
            Files.delete(testPath);
        } catch (Exception e) {
            System.err.println("Warning: Could not create test file: " + e.getMessage());
        }
    }
}