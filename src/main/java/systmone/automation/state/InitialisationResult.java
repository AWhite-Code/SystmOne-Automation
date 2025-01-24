package systmone.automation.state;

import systmone.automation.core.SystemComponents;

/**
 * Represents the outcome of a system initialization attempt using the Result pattern.
 * This class provides a type-safe alternative to null checks by encapsulating both
 * successful and failed initialization states with appropriate context.
 * 
 * Key Features:
 * - Immutable result state prevents modification after creation
 * - Factory methods enforce valid state combinations
 * - Type-safe access to components prevents null pointer exceptions
 * - Clear distinction between success and failure states
 * 
 * The class supports two distinct states:
 * 1. Success: Contains validated SystemComponents
 * 2. Failure: Contains a descriptive error message
 * 
 * This implementation ensures that calling code must explicitly handle both
 * success and failure cases, promoting robust error handling.
 */
public class InitialisationResult {
    // Core state fields
    private final boolean success;
    private final String errorMessage;
    private final SystemComponents components;

    /**
     * Private constructor enforces use of factory methods to create instances.
     * This ensures all instances are in a valid state.
     * 
     * @param success Whether initialization succeeded
     * @param errorMessage Description of failure (null if successful)
     * @param components Initialized components (null if failed)
     */
    private InitialisationResult(boolean success, String errorMessage, SystemComponents components) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.components = components;
    }

    /**
     * Creates a successful initialization result with validated components.
     * 
     * @param components The successfully initialized system components
     * @return A successful initialization result
     * @throws IllegalArgumentException if components is null
     */
    public static InitialisationResult succeeded(SystemComponents components) {
        if (components == null) {
            throw new IllegalArgumentException("Components cannot be null for a successful result");
        }
        return new InitialisationResult(true, null, components);
    }

    /**
     * Creates a failed initialization result with an explanation.
     * 
     * @param message Description of what caused initialization to fail
     * @return A failed initialization result
     * @throws IllegalArgumentException if message is null or empty
     */
    public static InitialisationResult failed(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty for a failed result");
        }
        return new InitialisationResult(false, message, null);
    }

    /**
     * Indicates whether initialization was successful.
     * 
     * @return true if initialization succeeded, false if it failed
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the error message from a failed initialization.
     * May be null if initialization was successful.
     * 
     * @return Description of what caused initialization to fail
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the initialized system components from a successful initialization.
     * 
     * @return The initialized system components
     * @throws IllegalStateException if called on a failed result
     */
    public SystemComponents getComponents() {
        if (!success) {
            throw new IllegalStateException("Cannot get components from a failed result");
        }
        return components;
    }
}