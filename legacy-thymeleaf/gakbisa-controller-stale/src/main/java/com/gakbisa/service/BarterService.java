package com.gakbisa.service;

import com.gakbisa.dto.Forms.BarterForm;
import com.gakbisa.dto.Views.BarterView;

import java.util.List;
import java.util.UUID;

public interface BarterService {
    UUID create(String email, BarterForm form);
    List<BarterView> incoming(String email);
    List<BarterView> outgoing(String email);
    BarterView detail(String email, UUID barterId);
    /** accept/reject: hanya penerima & hanya dari PENDING. */
    void accept(String email, UUID barterId);
    void reject(String email, UUID barterId);
    /** cancel: hanya pengirim & hanya dari PENDING. */
    void cancel(String email, UUID barterId);
    /** complete: hanya dari ACCEPTED, oleh salah satu pihak. */
    void complete(String email, UUID barterId);
}
