package com.gakbisa.service;

import com.gakbisa.dto.Forms.ReviewForm;

import java.util.UUID;

public interface ReviewService {
    /** Tolak bila sesi belum COMPLETED, bukan peserta, atau sudah pernah mengulas. */
    void submit(String email, UUID sessionId, ReviewForm form);
}
