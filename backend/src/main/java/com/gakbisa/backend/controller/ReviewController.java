package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.ReviewCreateRequest;
import com.gakbisa.backend.service.ReviewService;
import com.gakbisa.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviews;
    private final UserService userService;

    public ReviewController(ReviewService reviews, UserService userService) {
        this.reviews = reviews;
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody ReviewCreateRequest req) {
        var r = reviews.create(CurrentUser.id(), req);
        return Map.of("id", r.getId(), "rating", r.getRating(), "sessionId", r.getSession().getId());
    }

    /** Ulasan yang sudah saya tulis, lengkap dengan id sesinya (untuk menandai daftar "perlu dinilai"). */
    @GetMapping("/mine")
    public List<Map<String, Object>> mine() {
        return reviews.writtenBy(CurrentUser.id()).stream()
            .map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "sessionId", r.getSession().getId(),
                "revieweeName", r.getReviewee().getFullName(),
                "rating", r.getRating(),
                "comment", r.getComment() == null ? "" : r.getComment(),
                "createdAt", r.getCreatedAt()))
            .toList();
    }

    @GetMapping("/user/{userId}")
    public List<Map<String, Object>> forUser(@PathVariable UUID userId) {
        return reviews.forUser(userId).stream()
            .map(r -> Map.<String, Object>of(
                "rating", r.getRating(),
                "comment", r.getComment() == null ? "" : r.getComment(),
                "createdAt", r.getCreatedAt()))
            .toList();
    }

    @GetMapping("/user/{userId}/reputation")
    public UserService.ReputationDto reputation(@PathVariable UUID userId) {
        return userService.reputation(userId);
    }
}
