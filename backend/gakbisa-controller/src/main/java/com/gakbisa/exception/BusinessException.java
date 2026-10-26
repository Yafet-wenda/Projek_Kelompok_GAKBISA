package com.gakbisa.exception;

/** Pelanggaran aturan bisnis (input salah, status tidak valid, bukan peserta sesi, dst). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
