package com.gfg.NaarPazham;

class ValidationResult {
    private boolean success;
    private String message;

    ValidationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    boolean isSuccess() {  // package-private method
        return success;
    }

    String getMessage() {  // package-private method
        return message;
    }

}