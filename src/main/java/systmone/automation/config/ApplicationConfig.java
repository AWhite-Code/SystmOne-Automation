package systmone.automation.config;

import java.awt.Color;
import java.io.File;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

public class ApplicationConfig {
    // Application constants
    public static final String APP_TITLE = "SystmOne GP:";
    public static final String IMAGE_DIR_PATH = "src/main/resources/images";
    public static final boolean AUTO_CONFIGURE_PDF_PRINTER = true; 

        // Path configuration
    private static final String PROJECT_ROOT = System.getProperty("user.dir");  // Gets the current working directory
    private static final String PARENT_DIR = new File(PROJECT_ROOT).getParent();  // Gets parent of current directory
    
    // Current default: Creates 'SystmOne_Automation_Output' in parent directory of project root
    public static final String OUTPUT_BASE_PATH = Paths.get(PARENT_DIR, "SystmOne_Automation_Output").toString();

   
    // TODO: Make output path configurable via settings file

    // Date formatting
    public static final DateTimeFormatter FOLDER_DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd-MM-yyyy - HH-mm-ss");
    
    // Timing configurations
    public static final int FOCUS_DELAY_MS = 1000;
    public static final int NAVIGATION_DELAY_MS = 100;
    public static final int VERIFICATION_DELAY_MS = 100;      // Delay between verification attempts
    public static final int CONTEXT_MENU_DELAY_MS = 500;
    public static final int PRINT_DIALOG_DELAY_MS = 2000;
    public static final int SAVE_DIALOG_DELAY_MS = 3000;
    public static final double DIALOG_TIMEOUT = 10.0; // in seconds
    public static final double MENU_TIMEOUT = 5.0;    // in seconds

    // Operation retry limits
    public static final int MAX_PRINT_MENU_ATTEMPTS = 3;     // Maximum attempts for print menu operation
    public static final int MAX_SAVE_ATTEMPTS = 3;           // Maximum attempts for save operation
    public static final int MAX_NAVIGATION_ATTEMPTS = 3;     // Maximum attempts for navigation
    public static final int MAX_VERIFICATION_ATTEMPTS = 3;    // Maximum attempts to verify document loaded
    
    // Popup handling delays
    public static final int POPUP_CLEANUP_DELAY_MS = 200;    // Delay after dismissing popup
    public static final int MENU_CLEANUP_DELAY_MS = 300;     // Delay after closing stuck menu
    public static final int POST_CLEANUP_DELAY_MS = 500;     // Delay after full cleanup sequence

    // Navigation timing configurations
    public static final int NAVIGATION_VERIFY_TIMEOUT = 5;    // Timeout for navigation verification (seconds)
    public static final int SCROLLBAR_INIT_TIMEOUT = 3;       // Timeout for initializing scrollbar tracking
    
    // UI State Handler configurations
    public static final int REQUIRED_STABILITY_COUNT = 2;
    public static final int POLL_INTERVAL_MS = 10;
    public static final int MIN_DOCUMENTS_FOR_SCROLLBAR = 2;
    
    // Scrollbar detection settings
    public static final Color SCROLLBAR_DEFAULT = new Color(205, 205, 205);  // #CDCDCD
    public static final Color SCROLLBAR_HOVER = new Color(166, 166, 166);    // #A6A6A6
    public static final Color SCROLLBAR_SELECTED = new Color(96, 96, 96);    // 60,60,60
    public static final int COLOR_TOLERANCE = 3;
    public static final int SCROLLBAR_SEARCH_OFFSET_X = 250;  // Offset from selection border
    public static final int SCROLLBAR_WIDTH = 18;             // Width of scrollbar
    public static final int SCROLLBAR_OFFSET_X = 935;      // X offset from selection to scrollbar
    public static final int SEARCH_RANGE_ABOVE = 100;      // Pixels to search above selection
    public static final int SEARCH_RANGE_BELOW = 300;      // Pixels to search below selection
    public static final int MIN_THUMB_MOVEMENT = 3;        // Minimum pixels to consider real movement
    public static final int MAX_NO_MOVEMENT_COUNT = 10;    // Maximum checks before assuming no movemen
    public static final int UPWARD_PADDING = 15;
    public static final int DOWNWARD_PADDING = 150;

    // Popup detection settings
    public static final float POPUP_SIMILARITY_THRESHOLD = 0.9f;
    public static final float BUTTON_SIMILARITY_THRESHOLD = 0.9f;
    public static final int POPUP_POSITION_TOLERANCE = 150;  // pixels
    public static final int POPUP_DISMISS_DELAY_MS = 500;   // milliseconds
    public static final int MAX_POPUP_HANDLES = 3;  // Limit popup retry attempts
    public static final double DEFAULT_SIMILARITY = 0.8;  // Adjust this value based on testing

    // Debug
    public static final boolean DEBUG_SCREENSHOTS = true;
}