package systmone.automation.config;

import java.awt.Color;
import java.time.format.DateTimeFormatter;

public class ApplicationConfig {
    // Application constants
    public static final String APP_TITLE = "SystmOne GP:";
    public static final String IMAGE_DIR_PATH = "src/main/resources/images";
    public static final String OUTPUT_BASE_PATH = "C:\\Users\\Alexwh\\Dev Environs\\SystmOne_Automation_Output";
    
    // Date formatting
    public static final DateTimeFormatter FOLDER_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd-MM-yyyy - HH-mm-ss");
    
    // Location-specific settings
    public enum Location {
        DENTON,
        WOOTTON
    }
    
    // Timing configurations
    public static final int FOCUS_DELAY_MS = 1000;
    public static final int NAVIGATION_DELAY_MS = 200;
    public static final int CONTEXT_MENU_DELAY_MS = 500;
    public static final int PRINT_DIALOG_DELAY_MS = 2000;
    public static final int SAVE_DIALOG_DELAY_MS = 3000;
    public static final double DIALOG_TIMEOUT = 10.0; // in seconds
    public static final double MENU_TIMEOUT = 5.0;    // in seconds
    
    // Pattern matching settings
    public static final double DENTON_SIMILARITY = 0.8;
    public static final double WOOTTON_SIMILARITY = 0.8;
    public static final double LOCATION_SIMILARITY = 0.7;
    
    // UI State Handler configurations
    public static final int REQUIRED_STABILITY_COUNT = 3;
    public static final int POLL_INTERVAL_MS = 100;
    public static final int MOVEMENT_GRACE_PERIOD_MS = 500;
    public static final int MIN_DOCUMENTS_FOR_SCROLLBAR = 4;
    
    // Scrollbar detection settings
    public static final Color SCROLLBAR_DEFAULT = new Color(205, 205, 205);  // #CDCDCD
    public static final Color SCROLLBAR_HOVER = new Color(166, 166, 166);    // #A6A6A6
    public static final Color SCROLLBAR_SELECTED = new Color(96, 96, 96);    // 60,60,60
    public static final int COLOR_TOLERANCE = 3;
    public static final int SCROLLBAR_SEARCH_OFFSET_X = 250;  // Offset from selection border
    public static final int SCROLLBAR_WIDTH = 20;             // Width of scrollbar
}