package systmone.automation;

/**
 * Represents the outcome of a system initialization attempt.
 * Instead of using null to indicate failure, this class explicitly tracks
 * success/failure state and provides context about what went wrong.
 */
public class InitialisationResult {
    private final boolean success;
    private final String errorMessage;
    private final SystemComponents components;

    private InitialisationResult(boolean success, String errorMessage, SystemComponents components) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.components = components;
    }

    /**
     * Creates a successful result containing the initialized components.
     */
    public static InitialisationResult succeeded(SystemComponents components) {
        if (components == null) {
            throw new IllegalArgumentException("Components cannot be null for a successful result");
        }
        return new InitialisationResult(true, null, components);
    }

    /**
     * Creates a failed result with an explanation of what went wrong.
     */
    public static InitialisationResult failed(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty for a failed result");
        }
        return new InitialisationResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public SystemComponents getComponents() {
        if (!success) {
            throw new IllegalStateException("Cannot get components from a failed result");
        }
        return components;
    }
}