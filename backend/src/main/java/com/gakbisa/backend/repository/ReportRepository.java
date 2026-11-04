package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.Report;
import com.gakbisa.backend.domain.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /**
     * Laporan dengan nama pelapor ikut termuat. Join fetch diperlukan karena
     * open-in-view dimatikan: halaman admin membaca reporter di luar transaksi.
     */
    @Query("select r from Report r join fetch r.reporter where r.status = :status order by r.createdAt desc")
    List<Report> findByStatusWithReporter(@Param("status") ReportStatus status);

    long countByStatus(ReportStatus status);
}
