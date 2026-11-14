package com.gakbisa.exception;

/** Data yang dicari tidak ditemukan. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
