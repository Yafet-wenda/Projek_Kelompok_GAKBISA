package com.gakbisa.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Nama wajib diisi")
    @Size(max = 120, message = "Nama maksimal 120 karakter")
    String fullName,

    @NotBlank(message = "Email wajib diisi")
    @Email(message = "Format email tidak valid")
    @Size(max = 254)
    String email,

    @NotBlank(message = "Password wajib diisi")
    @Size(min = 8, max = 72, message = "Password 8-72 karakter")
    String password,

    @Size(max = 150)
    String campus
) {
    /** Email dinormalisasi huruf kecil sesuai aturan tabel users. */
    public String normalizedEmail() {
        return email == null ? null : email.toLowerCase().trim();
    }
}
