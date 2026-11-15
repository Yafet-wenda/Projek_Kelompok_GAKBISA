package com.gakbisa.service;

import com.gakbisa.dto.Forms.RegisterForm;

public interface AuthService {
    /** Simpan user baru (role STUDENT, password di-hash BCrypt). Lempar BusinessException jika email sudah terdaftar. */
    void register(RegisterForm form);
}
