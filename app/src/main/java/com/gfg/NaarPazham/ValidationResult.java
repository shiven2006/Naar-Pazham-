package com.gfg.NaarPazham;

// NEW: ValidationResult helper class matching GameController's approach
public  class ValidationResult {
    private final boolean success;
    private final String message;

    public ValidationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}