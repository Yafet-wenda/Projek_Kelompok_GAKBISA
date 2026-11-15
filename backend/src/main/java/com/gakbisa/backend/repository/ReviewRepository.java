package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * Ulasan yang diterima seseorang. Penulisnya ikut dimuat dengan join fetch karena
     * open-in-view dimatikan: halaman profil dan REST butuh nama pengulas di luar transaksi.
     */
    @Query("select r from Review r join fetch r.reviewer where r.reviewee.id = :userId order by r.createdAt desc")
    List<Review> findReceivedBy(@Param("userId") UUID userId);

    /** Ulasan yang ditulis seseorang, dengan nama yang dinilai ikut termuat. */
    @Query("select r from Review r join fetch r.reviewee where r.reviewer.id = :userId order by r.createdAt desc")
    List<Review> findWrittenBy(@Param("userId") UUID userId);

    boolean existsBySession_IdAndReviewerId(UUID sessionId, UUID reviewerId);

    @Query("select avg(r.rating) from Review r where r.reviewee.id = :userId")
    Double averageRatingFor(@Param("userId") UUID userId);

    long countByRevieweeId(UUID revieweeId);
}
