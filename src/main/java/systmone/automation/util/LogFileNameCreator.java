package systmone.automation.util;

public class LogFileNameCreator {
    private static String currentFileName;
    private static int documentCount = 0;

    public static void setDocumentCount(int count) {
        documentCount = count;
    }

    public static String getCurrentFileName() {
        if (currentFileName == null) {
            createNewFileName();
        }
        return currentFileName;
    }

    private static void createNewFileName() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String date = now.format(java.time.format.DateTimeFormatter.ofPattern("d.M.yy"));
        String time = now.format(java.time.format.DateTimeFormatter.ofPattern("HH.mm.ss"));
        currentFileName = String.format("%s - %s - %d Documents", date, time, documentCount);
    }
}