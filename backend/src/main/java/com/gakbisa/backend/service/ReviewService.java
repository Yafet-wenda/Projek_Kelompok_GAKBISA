package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.Review;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.dto.ReviewCreateRequest;
import com.gakbisa.backend.repository.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ulasan hanya untuk sesi COMPLETED, satu ulasan per peserta per sesi
 * (skenario uji §8.2 no.9). Reputasi dihitung on-demand dari rata-rata rating.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final SessionService sessions;
    private final UserService userService;
    private final NotificationService notifications;

    public ReviewService(ReviewRepository reviews, SessionService sessions, UserService userService,
                         NotificationService notifications) {
        this.reviews = reviews;
        this.sessions = sessions;
        this.userService = userService;
        this.notifications = notifications;
    }

    @Transactional
    public Review create(UUID reviewerId, ReviewCreateRequest req) {
        LearningSession s = sessions.require(req.learningSessionId());
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw new ValidationException("Ulasan hanya dapat diberikan setelah sesi selesai");
        }
        var b = s.getBarterRequest();
        User reviewee;
        if (b.getRequester().getId().equals(reviewerId)) {
            reviewee = b.getTargetUser();
        } else if (b.getTargetUser().getId().equals(reviewerId)) {
            reviewee = b.getRequester();
        } else {
            throw new ForbiddenException("Anda bukan peserta sesi ini");
        }
        if (reviews.existsBySession_IdAndReviewerId(s.getId(), reviewerId)) {
            throw new ValidationException("Anda sudah memberikan ulasan untuk sesi ini");
        }

        Review r = new Review();
        r.setSession(s);
        r.setReviewer(userService.require(reviewerId));
        r.setReviewee(reviewee);
        r.setRating(req.rating());
        r.setComment(req.comment());
        try {
            Review saved = reviews.save(r);
            notifications.push(reviewee, NotificationService.TYPE_REVIEW, "Ulasan baru",
                "Kamu mendapat rating " + req.rating() + "/5 dari partner sesi belajar.");
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ValidationException("Ulasan duplikat untuk sesi ini");
        }
    }

    @Transactional(readOnly = true)
    public List<Review> forUser(UUID userId) {
        return reviews.findByRevieweeIdOrderByCreatedAtDesc(userId);
    }

    /** Daftar ulasan yang sudah ditulis actor — dipakai dasbor agar tidak meminta ulasan dua kali. */
    @Transactional(readOnly = true)
    public List<Review> writtenBy(UUID reviewerId) {
        return reviews.findByReviewerIdOrderByCreatedAtDesc(reviewerId);
    }
}
