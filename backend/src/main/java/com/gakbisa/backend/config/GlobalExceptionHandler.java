package com.gakbisa.backend.config;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

/**
 * Menerjemahkan exception layanan menjadi respons HTTP yang konsisten (RFC 7807).
 */
@Order(0)
@RestControllerAdvice(basePackages = "com.gakbisa.backend.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(AppExceptions.ValidationException.class)
    public ProblemDetail onValidation(AppExceptions.ValidationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Data tidak valid", e.getMessage());
    }

    @ExceptionHandler(AppExceptions.NotFoundException.class)
    public ProblemDetail onNotFound(AppExceptions.NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Tidak ditemukan", e.getMessage());
    }

    @ExceptionHandler(AppExceptions.ForbiddenException.class)
    public ProblemDetail onForbidden(AppExceptions.ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "Akses ditolak", e.getMessage());
    }

    @ExceptionHandler(AppExceptions.UnauthorizedException.class)
    public ProblemDetail onUnauthorized(AppExceptions.UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Belum login", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Data permintaan tidak valid");
        return problem(HttpStatus.BAD_REQUEST, "Data tidak valid", detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onConstraintViolation(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Data tidak valid", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Data tidak valid", "Nilai parameter tidak dikenali");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onDataIntegrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Data konflik", "Data bertabrakan dengan data yang sudah ada");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
