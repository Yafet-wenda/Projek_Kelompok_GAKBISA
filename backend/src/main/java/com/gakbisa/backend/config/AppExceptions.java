package com.gakbisa.backend.config;

/**
 * Type exception layanan agar ditangani terpusat oleh GlobalExceptionHandler:
 * - ValidationException -> 400
 * - NotFoundException -> 404
 * - ForbiddenException -> 403
 */
public final class AppExceptions {

    private AppExceptions() {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }
}
