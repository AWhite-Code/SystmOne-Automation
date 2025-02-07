package systmone.automation.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogFileNameCreator {
    private static String currentFileName;
    private static int documentCount = 0;
    private static final String IN_PROGRESS_MARKER = "In Progress";
    private static LocalDateTime startTime;

    static {
        startTime = LocalDateTime.now();
        createNewFileName();
    }

    public static void setDocumentCount(int count) {
        documentCount = count;
        createNewFileName();
    }

    public static String getCurrentFileName() {
        return currentFileName;
    }

    private static void createNewFileName() {
        String date = startTime.format(DateTimeFormatter.ofPattern("d.M.yy"));
        String time = startTime.format(DateTimeFormatter.ofPattern("HH.mm.ss"));
        
        if (documentCount > 0) {
            currentFileName = String.format("%s - %s - %d Documents", date, time, documentCount);
        } else {
            currentFileName = String.format("%s - %s - %s", date, time, IN_PROGRESS_MARKER);
        }
    }
}