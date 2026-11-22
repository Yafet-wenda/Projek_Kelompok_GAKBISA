package com.gakbisa.backend;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.controller.ReportAdminController.CreateReportRequest;
import com.gakbisa.backend.controller.WebController;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.BarterStatus;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import com.gakbisa.backend.domain.ReportTargetType;
import com.gakbisa.backend.domain.Review;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.dto.PortfolioCreateDto;
import com.gakbisa.backend.dto.ProfileUpdateDto;
import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.dto.UserSkillDto;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.gakbisa.backend.repository.NotificationRepository;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.SkillCategoryRepository;
import com.gakbisa.backend.repository.SkillRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.security.GakbisaUserDetails;
import com.gakbisa.backend.service.AdminService;
import com.gakbisa.backend.service.AuthService;
import com.gakbisa.backend.service.BarterService;
import com.gakbisa.backend.service.MatchingService;
import com.gakbisa.backend.service.NotificationService;
import com.gakbisa.backend.service.PortfolioService;
import com.gakbisa.backend.service.ReviewService;
import com.gakbisa.backend.service.SessionService;
import com.gakbisa.backend.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pure controller tests: no application context, database, storage, or server. */
class WebControllerTest {
    private static ValidatorFactory validation;
    private AuthService auth;
    private UserService users;
    private UserRepository userRepository;
    private BarterRequestRepository barterRepository;
    private PortfolioService portfolios;
    private SessionService sessions;
    private ReviewService reviews;
    private AdminService admin;
    private NotificationService notifications;
    private SkillCategoryRepository categories;
    private SkillRepository skills;
    private WebController controller;
    private User viewer;

    @BeforeAll
    static void validator() {
        validation = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validation.close();
    }

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        users = mock(UserService.class);
        userRepository = mock(UserRepository.class);
        barterRepository = mock(BarterRequestRepository.class);
        portfolios = mock(PortfolioService.class);
        sessions = mock(SessionService.class);
        reviews = mock(ReviewService.class);
        admin = mock(AdminService.class);
        notifications = mock(NotificationService.class);
        categories = mock(SkillCategoryRepository.class);
        skills = mock(SkillRepository.class);
        controller = new WebController(auth, users, userRepository, categories, skills,
            mock(MatchingService.class), mock(BarterService.class), barterRepository, portfolios,
            mock(PortfolioRepository.class), sessions, mock(LearningSessionRepository.class),
            notifications, mock(NotificationRepository.class), reviews, admin, validation.getValidator(),
            mock(com.gakbisa.backend.service.ProfileMediaService.class));
        viewer = user("Viewer");
        signIn(viewer);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private User user(String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName(name);
        u.setRole(UserRole.STUDENT);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private void signIn(User user) {
        var principal = new GakbisaUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private BarterRequest barter(User requester, User receiver, BarterStatus status) {
        BarterRequest b = new BarterRequest();
        b.setId(UUID.randomUUID());
        b.setRequester(requester);
        b.setTargetUser(receiver);
        b.setStatus(status);
        when(barterRepository.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private LearningSession session(BarterRequest barter, SessionStatus status) {
        LearningSession s = new LearningSession();
        s.setId(UUID.randomUUID());
        s.setBarterRequest(barter);
        s.setStatus(status);
        s.setScheduledAt(Instant.now().plusSeconds(600));
        when(sessions.viewFor(viewer.getId(), s.getId())).thenReturn(s);
        return s;
    }

    @Test
    void landingAndLoginUseSessionAuthenticationAndAdminDashboardRedirects() {
        assertThat(controller.home()).isEqualTo("redirect:/dashboard");
        assertThat(controller.login()).isEqualTo("redirect:/dashboard");
        assertThat(controller.index()).isEqualTo("redirect:/");
        viewer.setRole(UserRole.ADMIN);
        assertThat(controller.dashboard(new ExtendedModelMap())).isEqualTo("redirect:/admin/dashboard");
        SecurityContextHolder.clearContext();
        assertThat(controller.home()).isEqualTo("redirect:/login");
        assertThat(controller.login()).isEqualTo("auth/login");
    }

    @Test
    void barterDetailRejectsOutsidersWithoutRenderingPrivateData() {
        BarterRequest b = barter(user("Requester"), user("Receiver"), BarterStatus.PENDING);
        var model = new ExtendedModelMap();
        assertThatThrownBy(() -> controller.barterDetail(b.getId(), model)).isInstanceOf(ForbiddenException.class);
        assertThat(model).doesNotContainKey("barter");
    }

    @Test
    void barterFlagsReflectParticipantAndStatus() {
        User requester = user("Requester");
        BarterRequest b = barter(requester, viewer, BarterStatus.PENDING);
        var model = new ExtendedModelMap();
        controller.barterDetail(b.getId(), model);
        assertThat(model).containsEntry("canAccept", true).containsEntry("canCancel", false);
        b.setStatus(BarterStatus.ACCEPTED);
        controller.barterDetail(b.getId(), model);
        assertThat(model).containsEntry("canAccept", false).containsEntry("canCancel", false);
        signIn(requester);
        controller.barterDetail(b.getId(), model);
        assertThat(model).containsEntry("canAccept", false).containsEntry("canCancel", true);
    }

    @Test
    void missingProfileOwnerIsNotRenderedAsNull() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> controller.viewUserProfile(missing, new ExtendedModelMap()))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> controller.userPortfolio(missing, new ExtendedModelMap()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void portfolioListsAndProfileCountsOnlyIncludeVisibleWorks() {
        User owner = user("Owner");
        Portfolio publicWork = new Portfolio();
        publicWork.setVisibility(PortfolioVisibility.PUBLIC);
        Portfolio privateWork = new Portfolio();
        privateWork.setVisibility(PortfolioVisibility.PRIVATE);
        privateWork.setTitle("Private title must not reach model");
        when(portfolios.forUser(owner.getId())).thenReturn(List.of(publicWork, privateWork));
        when(users.reputation(owner.getId())).thenReturn(new UserService.ReputationDto(null, 0, 2));
        var model = new ExtendedModelMap();
        controller.userPortfolio(owner.getId(), model);
        assertThat(model.get("portfolios")).isEqualTo(List.of(publicWork));
        controller.viewUserProfile(owner.getId(), model);
        assertThat(model).containsEntry("isSelf", false);
        assertThat(model.get("portfolios")).isEqualTo(List.of(publicWork));
        assertThat(model).doesNotContainKey("portfolioCount");
        signIn(owner);
        controller.viewUserProfile(owner.getId(), model);
        assertThat(model).containsEntry("isSelf", true);
        assertThat(model.get("portfolios")).isEqualTo(List.of(publicWork, privateWork));
        viewer.setRole(UserRole.ADMIN);
        signIn(viewer);
        controller.userPortfolio(owner.getId(), model);
        assertThat(model.get("portfolios")).isEqualTo(List.of(publicWork, privateWork));
    }

    @Test
    void scheduleUsesBrowserOffsetAndJitsiDiscardsExternalUrl() {
        UUID barterId = UUID.randomUUID();
        var flash = new RedirectAttributesModelMap();
        assertThat(controller.scheduleSession(barterId, "2099-01-01T10:30", 240, -420,
            "JITSI", "https://ignored.example", flash)).isEqualTo("redirect:/sessions");
        var request = ArgumentCaptor.forClass(SessionCreateRequest.class);
        verify(sessions).schedule(eq(viewer.getId()), request.capture());
        assertThat(request.getValue().scheduledAt()).isEqualTo(Instant.parse("2099-01-01T03:30:00Z"));
        assertThat(request.getValue().durationMinutes()).isEqualTo(240);
        assertThat(request.getValue().externalMeetingUrl()).isNull();
        assertThat(flash.getFlashAttributes()).containsKey("success").doesNotContainKey("error");
    }

    @Test
    void scheduleRejectsInvalidDurationDatetimeOffsetAndExternalUrls() {
        UUID barterId = UUID.randomUUID();
        assertScheduleFails(barterId, "2099-01-01T10:30", 241, 0, "JITSI", null);
        assertScheduleFails(barterId, "2099-01-01T10:30", 14, 0, "JITSI", null);
        assertScheduleFails(barterId, "not-a-time", 60, 0, "JITSI", null);
        assertScheduleFails(barterId, "2099-01-01T10:30", 60, Integer.MAX_VALUE, "JITSI", null);
        assertScheduleFails(barterId, "2099-01-01T10:30", 60, 0, "EXTERNAL", " ");
        assertScheduleFails(barterId, "2099-01-01T10:30", 60, 0, "EXTERNAL", "javascript:alert(1)");
        assertScheduleFails(barterId, "2099-01-01T10:30", 60, 0, "EXTERNAL", "https://user:pass@example.org/room");
        assertScheduleFails(barterId, "2099-01-01T10:30", 60, 0, "UNKNOWN", "https://example.org/room");
        verifyNoInteractions(sessions);
    }

    private void assertScheduleFails(UUID barterId, String time, int duration, int offset, String provider, String url) {
        var flash = new RedirectAttributesModelMap();
        assertThat(controller.scheduleSession(barterId, time, duration, offset, provider, url, flash))
            .isEqualTo("redirect:/barter/" + barterId);
        assertThat(flash.getFlashAttributes()).containsKey("error").doesNotContainKey("success");
    }

    @Test
    void schedulePassesTrimmedExternalLinkToExistingService() {
        controller.scheduleSession(UUID.randomUUID(), "2099-01-01T10:30", 60, 300,
            "EXTERNAL", " https://example.org/meeting ", new RedirectAttributesModelMap());
        var request = ArgumentCaptor.forClass(SessionCreateRequest.class);
        verify(sessions).schedule(eq(viewer.getId()), request.capture());
        assertThat(request.getValue().scheduledAt()).isEqualTo(Instant.parse("2099-01-01T15:30:00Z"));
        assertThat(request.getValue().externalMeetingUrl()).isEqualTo("https://example.org/meeting");
    }

    @Test
    void rescheduleValidatesDurationAndConvertsOffsetBeforeCallingService() {
        LearningSession s = session(barter(viewer, user("Partner"), BarterStatus.ACCEPTED), SessionStatus.SCHEDULED);
        var invalid = new RedirectAttributesModelMap();
        controller.rescheduleSession(s.getId(), "2099-01-01T10:30", 241, -420, invalid);
        verify(sessions, never()).reschedule(any(), any(), any(), anyInt());
        assertThat(invalid.getFlashAttributes()).containsKey("error");
        controller.rescheduleSession(s.getId(), "2099-01-01T10:30", 30, -420, new RedirectAttributesModelMap());
        verify(sessions).reschedule(viewer.getId(), s.getId(), Instant.parse("2099-01-01T03:30:00Z"), 30);
    }

    @Test
    void sessionFlagsPartnerAndExistingReviewAreRelativeToViewer() {
        LearningSession s = session(barter(user("Partner"), viewer, BarterStatus.ACCEPTED), SessionStatus.SCHEDULED);
        Review review = new Review();
        review.setSession(s);
        when(reviews.writtenBy(viewer.getId())).thenReturn(List.of(review));
        var model = new ExtendedModelMap();
        assertThat(controller.sessionDetail(s.getId(), model)).isEqualTo("session/detail");
        assertThat(model).containsEntry("partnerName", "Partner").containsEntry("hasReviewed", true)
            .containsEntry("canStart", true).containsEntry("canComplete", true)
            .containsEntry("canCancel", true).containsEntry("canReschedule", true);
        s.getBarterRequest().setStatus(BarterStatus.CANCELLED);
        controller.sessionDetail(s.getId(), model);
        assertThat(model).containsEntry("canStart", false).containsEntry("canComplete", false)
            .containsEntry("canCancel", false).containsEntry("canReschedule", false);
    }

    @Test
    void startUsesServiceSecurityAndNeverTurnsFailureIntoSuccess() {
        UUID id = UUID.randomUUID();
        when(sessions.setStatus(viewer.getId(), id, SessionStatus.ONGOING))
            .thenThrow(new ForbiddenException("internal-details-not-for-the-browser"));
        var flash = new RedirectAttributesModelMap();
        controller.startSession(id, flash);
        assertThat(flash.getFlashAttributes()).containsKey("error").doesNotContainKey("success");
        assertThat(flash.getFlashAttributes().get("error").toString()).doesNotContain("internal-details");
    }

    @Test
    void reviewCannotWrapAnOutOfRangeIntegerIntoAValidShort() {
        var flash = new RedirectAttributesModelMap();
        controller.submitReview(UUID.randomUUID(), 65537, "comment", flash);
        verifyNoInteractions(reviews);
        assertThat(flash.getFlashAttributes()).containsKey("error").doesNotContainKey("success");
    }

    @Test
    void invalidRegisterProfileSkillsAndUploadFormsDoNotCallServices() {
        var model = new ExtendedModelMap();
        var flash = new RedirectAttributesModelMap();
        RegisterRequest registration = new RegisterRequest("", "bad", "short", "");
        var registerErrors = new BeanPropertyBindingResult(registration, "registerForm");
        registerErrors.reject("invalid", "Invalid registration");
        model.addAttribute("registerForm", registration);
        assertThat(controller.register(registration, registerErrors, model)).isEqualTo("auth/register");
        assertThat(model.get("registerForm")).isSameAs(registration);
        verifyNoInteractions(auth);

        ProfileUpdateDto profile = new ProfileUpdateDto("", "bio", "campus");
        var profileErrors = new BeanPropertyBindingResult(profile, "profileUpdateDto");
        model.addAttribute("profileUpdateDto", profile);
        assertThat(controller.updateProfile(profile, profileErrors, model)).isEqualTo("profile/edit");
        assertThat(profileErrors.hasFieldErrors("fullName")).isTrue();
        assertThat(model.get("profileUpdateDto")).isSameAs(profile);

        UserSkillDto skill = new UserSkillDto();
        var skillErrors = new BeanPropertyBindingResult(skill, "userSkillDto");
        skillErrors.rejectValue("skillId", "typeMismatch", "Invalid selection");
        model.addAttribute("userSkillDto", skill);
        assertThat(controller.addSkill(skill, skillErrors, model, flash)).isEqualTo("profile/skills");
        assertThat(model).containsKeys("offerSkills", "wantSkills", "categories", "skills");
        assertThat(model.get("userSkillDto")).isSameAs(skill);
        verify(users, never()).updateProfile(any(), any(), any(), any(), any());
        verify(users, never()).addSkill(any(), any(), any(), any(), any(), any());

        PortfolioCreateDto upload = new PortfolioCreateDto("Title", "Description", "PRIVATE");
        var uploadErrors = new BeanPropertyBindingResult(upload, "portfolioDto");
        uploadErrors.reject("invalid", "Invalid upload");
        model.addAttribute("portfolioDto", upload);
        var file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});
        assertThat(controller.portfolioUploadPost(file, upload, uploadErrors, model, flash)).isEqualTo("portfolio/upload");
        assertThat(model.get("portfolioDto")).isSameAs(upload);
        verifyNoInteractions(portfolios);
    }

    @Test
    void reportRedirectIsDerivedOnlyFromTypedTarget() {
        User target = user("Reported user");
        var report = new CreateReportRequest(ReportTargetType.USER, target.getId(), "Reason");
        var errors = new BeanPropertyBindingResult(report, "reportForm");
        var flash = new RedirectAttributesModelMap();
        assertThat(controller.createReport(report, errors, flash)).isEqualTo("redirect:/profile/" + target.getId());
        verify(admin).createReport(viewer.getId(), ReportTargetType.USER, target.getId(), "Reason");
        assertThat(flash.getFlashAttributes()).containsKey("success");
        var portfolioReport = new CreateReportRequest(ReportTargetType.PORTFOLIO, UUID.randomUUID(), "");
        var invalid = new BeanPropertyBindingResult(portfolioReport, "reportForm");
        invalid.rejectValue("reason", "required", "Reason required");
        var failure = new RedirectAttributesModelMap();
        assertThat(controller.createReport(portfolioReport, invalid, failure)).isEqualTo("redirect:/portfolio");
        assertThat(failure.getFlashAttributes()).containsKey("error").doesNotContainKey("success");
    }

    @Test
    void expectedNotificationFailuresAreVisibleButUnexpectedFailuresPropagate() {
        UUID id = UUID.randomUUID();
        doThrow(new ValidationException("internal-details-not-for-the-browser"))
            .when(notifications).markRead(viewer.getId(), id);
        var flash = new RedirectAttributesModelMap();
        controller.markNotificationRead(id, flash);
        assertThat(flash.getFlashAttributes()).containsKey("error").doesNotContainKey("success");
        assertThat(flash.getFlashAttributes().get("error").toString()).doesNotContain("internal-details");
        doThrow(new IllegalStateException("unexpected")).when(notifications).markRead(viewer.getId(), id);
        assertThatThrownBy(() -> controller.markNotificationRead(id, new RedirectAttributesModelMap()))
            .isInstanceOf(IllegalStateException.class);
    }
}
