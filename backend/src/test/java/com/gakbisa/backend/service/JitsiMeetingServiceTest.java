package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.config.JaasProperties;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.MeetingProvider;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JitsiMeetingServiceTest {

    private static final String APP_ID = "vpaas-magic-cookie-test";
    private static final String KEY_ID = "test-key";
    private static KeyPair signingKey;

    @TempDir
    Path tempDir;

    private SessionService sessions;
    private LearningSession session;
    private User requester;
    private User target;

    @BeforeAll
    static void generateTestKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair();
    }

    @BeforeEach
    void setup() {
        requester = user("Requester \"Host\"", UserRole.STUDENT);
        target = user("Other participant", UserRole.ADMIN);
        BarterRequest barter = new BarterRequest();
        barter.setRequester(requester);
        barter.setTargetUser(target);
        session = new LearningSession();
        session.setId(UUID.randomUUID());
        session.setBarterRequest(barter);
        session.setMeetingProvider(MeetingProvider.JITSI);
        session.setMeetingRoom("gakbisa-ExactRoom-A7");
        session.setStatus(SessionStatus.SCHEDULED);

        LearningSessionRepository repository = mock(LearningSessionRepository.class);
        when(repository.findById(session.getId())).thenReturn(Optional.of(session));
        // Exercise the real participant check without Spring, a database or network access.
        sessions = new SessionService(repository, mock(BarterRequestRepository.class),
            mock(BarterService.class), mock(NotificationService.class));
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"SCHEDULED", "ONGOING"})
    void requesterGetsVerifiedRoomBoundModeratorToken(SessionStatus status) throws Exception {
        session.setStatus(status);
        JitsiMeetingService service = new JitsiMeetingService(sessions, configured());

        assertSignedAccess(service.accessFor(requester.getId(), session.getId()), requester, "true");
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"SCHEDULED", "ONGOING"})
    void otherParticipantIsNotModeratorEvenWhenAdmin(SessionStatus status) throws Exception {
        session.setStatus(status);
        JitsiMeetingService service = new JitsiMeetingService(sessions, configured());

        assertSignedAccess(service.accessFor(target.getId(), session.getId()), target, "false");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nonparticipantIsRejectedBeforeTokenOrPublicLink(boolean configured) {
        JitsiMeetingService service = new JitsiMeetingService(sessions,
            configured ? properties(tempDir.resolve("missing.pem")) : unconfigured());

        assertThrows(ForbiddenException.class,
            () -> service.accessFor(UUID.randomUUID(), session.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
    void inactiveSessionIsRejectedBeforeTokenOrPublicLink(SessionStatus status) {
        session.setStatus(status);
        for (JaasProperties props : List.of(unconfigured(), properties(tempDir.resolve("missing.pem")))) {
            JitsiMeetingService service = new JitsiMeetingService(sessions, props);
            var error = assertThrows(ValidationException.class,
                () -> service.accessFor(requester.getId(), session.getId()));
            assertEquals("Sesi ini sudah tidak aktif, ruang meeting ditutup", error.getMessage());
        }
    }

    @Test
    void unconfiguredJaasKeepsPublicFallback() {
        for (JaasProperties props : List.of(unconfigured(),
                new JaasProperties(APP_ID, KEY_ID, " "))) {
            JitsiMeetingService service = new JitsiMeetingService(sessions, props);
            assertLinkOnly(service.accessFor(target.getId(), session.getId()),
                "https://meet.jit.si/" + session.getMeetingRoom());
        }
    }

    @Test
    void externalMeetingLinkIsPreservedWithoutSigning() {
        session.setMeetingProvider(MeetingProvider.EXTERNAL_LINK);
        session.setMeetingUrl("https://example.test/meeting");
        JitsiMeetingService service = new JitsiMeetingService(sessions,
            properties(tempDir.resolve("missing.pem")));

        assertLinkOnly(service.accessFor(requester.getId(), session.getId()), session.getMeetingUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "malformed", "invalid-rsa"})
    void configuredBadKeyFailsClosedWithoutExposingDetails(String failure) throws Exception {
        Path keyFile = tempDir.resolve("private-test-key.pem");
        if (failure.equals("malformed")) {
            Files.writeString(keyFile, "not-a-real-key-DO-NOT-EXPOSE");
        } else if (failure.equals("invalid-rsa")) {
            Files.writeString(keyFile, pem("not an RSA key".getBytes(StandardCharsets.UTF_8)));
        }
        JitsiMeetingService service = new JitsiMeetingService(sessions, properties(keyFile));

        assertUnavailable(service);
    }

    @Test
    void signingFailureDoesNotReturnPublicFallback() throws Exception {
        JitsiMeetingService service = new JitsiMeetingService(sessions, configured());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        // Inject a generated incompatible cached key to exercise Signature.initSign failure.
        ReflectionTestUtils.setField(service, "cachedKey", generator.generateKeyPair().getPrivate());

        assertUnavailable(service);
    }

    private void assertUnavailable(JitsiMeetingService service) {
        var error = assertThrows(ValidationException.class,
            () -> service.accessFor(requester.getId(), session.getId()));
        assertEquals("Akses meeting tidak tersedia saat ini. Silakan coba lagi nanti.", error.getMessage());
        assertNull(error.getCause(), "Do not expose key contents, paths or signing exception details");
    }

    private void assertSignedAccess(JitsiMeetingService.MeetingAccess access, User actor,
                                    String moderator) throws Exception {
        assertTrue(access.embedded());
        assertEquals("8x8.vc", access.domain());
        assertEquals("https://8x8.vc/" + APP_ID + "/external_api.js", access.scriptUrl());
        assertEquals(APP_ID + "/" + session.getMeetingRoom(), access.roomName());
        assertEquals(actor.getFullName(), access.displayName());
        assertNull(access.joinUrl());

        SignedJWT jwt = SignedJWT.parse(access.jwt());
        assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
        assertEquals(JOSEObjectType.JWT, jwt.getHeader().getType());
        assertEquals(APP_ID + "/" + KEY_ID, jwt.getHeader().getKeyID());
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) signingKey.getPublic())),
            "Verify the RS256 signature, not just decode the JWT");
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertEquals(List.of("jitsi"), claims.getAudience());
        assertEquals("chat", claims.getIssuer());
        assertEquals(APP_ID, claims.getSubject());
        assertEquals(session.getMeetingRoom(), claims.getStringClaim("room"));
        Map<String, Object> context = claims.getJSONObjectClaim("context");
        assertEquals(Map.of("regex", false), context.get("room"));
        assertEquals(Map.of("id", actor.getId().toString(), "name", actor.getFullName(),
            "email", actor.getEmail(), "moderator", moderator), context.get("user"));
        assertTrue(claims.getNotBeforeTime().toInstant().isBefore(Instant.now()));
        assertTrue(claims.getExpirationTime().toInstant().isAfter(Instant.now()));

        String differentRoomPayload = new JWTClaimsSet.Builder(claims)
            .claim("room", "gakbisa-another-room").build().toString();
        String[] parts = access.jwt().split("\\.");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(differentRoomPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertFalse(SignedJWT.parse(tampered).verify(new RSASSAVerifier((RSAPublicKey) signingKey.getPublic())),
            "The signature must protect the authorized room claim");
    }

    private static void assertLinkOnly(JitsiMeetingService.MeetingAccess access, String url) {
        assertFalse(access.embedded());
        assertEquals(url, access.joinUrl());
        assertNull(access.jwt());
        assertNull(access.roomName());
        assertNull(access.domain());
        assertNull(access.scriptUrl());
        assertNull(access.displayName());
    }

    private JaasProperties configured() throws Exception {
        Path keyFile = tempDir.resolve("generated-rsa.pem");
        Files.writeString(keyFile, pem(signingKey.getPrivate().getEncoded()));
        return properties(keyFile);
    }

    private static String pem(byte[] encoded) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(encoded)
            + "\n-----END PRIVATE KEY-----\n";
    }

    private static JaasProperties properties(Path keyFile) {
        return new JaasProperties(APP_ID, KEY_ID, keyFile.toString());
    }

    private static JaasProperties unconfigured() {
        return new JaasProperties(null, null, null);
    }

    private static User user(String name, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setEmail(user.getId() + "@example.test");
        user.setRole(role);
        return user;
    }
}
