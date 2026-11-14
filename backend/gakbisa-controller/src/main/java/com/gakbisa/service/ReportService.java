package com.gakbisa.service;

import com.gakbisa.dto.Forms.ReportForm;

public interface ReportService {
    /** Validasi target ada; simpan dengan status OPEN. */
    void create(String reporterEmail, ReportForm form);
}
