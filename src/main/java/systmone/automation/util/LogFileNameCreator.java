package systmone.automation.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogFileNameCreator {
    private static String currentFileName;
    private static int documentCount = 0;
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("d.M.yy");
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("HH.mm.ss");

    public static void setDocumentCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Document count cannot be negative");
        }
        documentCount = count;
        currentFileName = null; // Reset filename when count changes
    }

    public static String getCurrentFileName() {
        if (currentFileName == null) {
            createNewFileName();
        }
        return currentFileName;
    }

    private static void createNewFileName() {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DATE_FORMATTER);
        String time = now.format(TIME_FORMATTER);
        currentFileName = String.format("%s - %s - %d Documents", date, time, documentCount);
    }
}