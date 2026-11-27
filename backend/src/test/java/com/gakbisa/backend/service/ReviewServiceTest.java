package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.ReviewCreateRequest;
import com.gakbisa.backend.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    private ReviewRepository reviews;
    private SessionService sessions;
    private UserService users;
    private NotificationService notifications;
    private ReviewService service;
    private User requester;
    private User target;
    private LearningSession completedSession;

    @BeforeEach
    void setup() {
        reviews = mock(ReviewRepository.class);
        sessions = mock(SessionService.class);
        users = mock(UserService.class);
        notifications = mock(NotificationService.class);
        service = new ReviewService(reviews, sessions, users, notifications);

        requester = user("Rina");
        target = user("Tono");
        BarterRequest barter = new BarterRequest();
        barter.setId(UUID.randomUUID());
        barter.setRequester(requester);
        barter.setTargetUser(target);

        completedSession = new LearningSession();
        completedSession.setId(UUID.randomUUID());
        completedSession.setBarterRequest(barter);
        completedSession.setStatus(SessionStatus.COMPLETED);
        when(sessions.require(completedSession.getId())).thenReturn(completedSession);
        when(users.require(requester.getId())).thenReturn(requester);
    }

    @Test
    void hanyaPesertaSesiYangBolehMemberiUlasan() {
        User stranger = user("Orang asing");
        ReviewCreateRequest request = request((short) 5);

        assertThrows(ForbiddenException.class, () -> service.create(stranger.getId(), request));

        verify(reviews, never()).save(any(Review.class));
    }

    @Test
    void ratingHarusBeradaPadaRentangSatuSampaiLima() {
        assertThrows(ValidationException.class, () -> service.create(requester.getId(), request((short) 0)));
        assertThrows(ValidationException.class, () -> service.create(requester.getId(), request((short) 6)));

        verify(reviews, never()).save(any(Review.class));
    }

    @Test
    void mencegahUlasanKeduaDariReviewerUntukSesiYangSama() {
        when(reviews.existsBySession_IdAndReviewerId(completedSession.getId(), requester.getId())).thenReturn(true);

        assertThrows(ValidationException.class, () -> service.create(requester.getId(), request((short) 4)));

        verify(reviews, never()).save(any(Review.class));
    }

    private ReviewCreateRequest request(short rating) {
        return new ReviewCreateRequest(completedSession.getId(), rating, "Sesi belajar yang baik");
    }

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setEmail(name.toLowerCase().replace(" ", "") + "@gakbisa.test");
        user.setPasswordHash("x");
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
