package systmone.automation.config;

import java.time.format.DateTimeFormatter;

public class ApplicationConfig {
    // Application constants
    public static final String APP_TITLE = "SystmOne GP:";
    public static final String IMAGE_DIR_PATH = "src/main/resources/images";
    public static final String OUTPUT_BASE_PATH = "C:\\Users\\Alexwh\\Dev Environs\\SystmOne_Automation_Output";
    public static final DateTimeFormatter FOLDER_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd-MM-yyyy - HH-mm-ss");
    
    // Pattern matching settings
    public static final double LOCATION_SIMILARITY = 0.95;
    public static final double DENTON_SIMILARITY = 0.85;
    public static final double WOOTTON_SIMILARITY = 0.85;
    
    // Timing and Delays
    public static final long NAVIGATION_DELAY_MS = 200;
    public static final int FOCUS_DELAY_MS = 200;
    public static final int UI_STABILITY_DELAY_MS = 1000;
    public static final double MENU_TIMEOUT = 5.0;
    public static final double DIALOG_TIMEOUT = 10.0;
    
    // Location enum
    public enum Location {
        DENTON, WOOTTON
    }
}
