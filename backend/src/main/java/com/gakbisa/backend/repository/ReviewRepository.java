package com.gakbisa.backend.repository;

import com.gakbisa.backend.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByRevieweeIdOrderByCreatedAtDesc(UUID revieweeId);

    List<Review> findByReviewerIdOrderByCreatedAtDesc(UUID reviewerId);

    boolean existsBySession_IdAndReviewerId(UUID sessionId, UUID reviewerId);

    @Query("select avg(r.rating) from Review r where r.reviewee.id = :userId")
    Double averageRatingFor(@Param("userId") UUID userId);

    long countByReviewerIdOrRevieweeId(UUID a, UUID b);
}
