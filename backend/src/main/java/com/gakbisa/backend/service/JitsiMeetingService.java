package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.config.JaasProperties;
import com.gakbisa.backend.domain.BarterRequest;
import com.gakbisa.backend.domain.LearningSession;
import com.gakbisa.backend.domain.MeetingProvider;
import com.gakbisa.backend.domain.SessionStatus;
import com.gakbisa.backend.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Menyiapkan akses ke ruang meeting sebuah sesi belajar.
 *
 * - JaaS terkonfigurasi: meeting ditanam di halaman lewat iFrame API. JaaS mewajibkan
 *   JWT per peserta yang ditandatangani private key, jadi token dibuat di sini.
 * - JaaS belum terkonfigurasi: kembali ke tautan Jitsi publik / tautan eksternal.
 *   Kegagalan JaaS terkonfigurasi ditolak, tidak diturunkan ke ruang publik.
 *
 * Hanya peserta sesi yang boleh mengambil akses (dicek lewat SessionService.viewFor).
 * JWT dibuat tanpa library tambahan: JSON dirangkai manual, tanda tangan RS256 memakai
 * java.security, jadi pom.xml tidak perlu berubah.
 */
@Service
public class JitsiMeetingService {

    private static final Logger log = LoggerFactory.getLogger(JitsiMeetingService.class);

    private static final String PUBLIC_JITSI = "https://meet.jit.si/";
    private static final String JAAS_DOMAIN = "8x8.vc";
    private static final long TOKEN_TTL_SECONDS = 3 * 60 * 60;

    /** Respons ke browser. Kalau embedded = false, pakai joinUrl; kalau true, pakai sisanya. */
    public record MeetingAccess(boolean embedded, String domain, String scriptUrl,
                                String roomName, String jwt, String displayName, String joinUrl) {}

    private final SessionService sessions;
    private final JaasProperties jaas;
    private volatile PrivateKey cachedKey;

    public JitsiMeetingService(SessionService sessions, JaasProperties jaas) {
        this.sessions = sessions;
        this.jaas = jaas;
    }

    @Transactional(readOnly = true)
    public MeetingAccess accessFor(UUID actorId, UUID sessionId) {
        LearningSession s = sessions.viewFor(actorId, sessionId); // 403 kalau bukan peserta
        if (s.getStatus() != SessionStatus.SCHEDULED && s.getStatus() != SessionStatus.ONGOING) {
            throw new ValidationException("Sesi ini sudah tidak aktif, ruang meeting ditutup");
        }
        if (s.getMeetingProvider() == MeetingProvider.EXTERNAL_LINK) {
            return linkOnly(s.getMeetingUrl());
        }

        String publicLink = PUBLIC_JITSI + s.getMeetingRoom();
        if (!jaas.configured()) {
            return linkOnly(publicLink);
        }

        BarterRequest b = s.getBarterRequest();
        boolean moderator = b.getRequester().getId().equals(actorId);
        User me = moderator ? b.getRequester() : b.getTargetUser();
        try {
            String jwt = signJwt(me, s.getMeetingRoom(), moderator);
            return new MeetingAccess(
                true,
                JAAS_DOMAIN,
                "https://" + JAAS_DOMAIN + "/" + jaas.appId().trim() + "/external_api.js",
                jaas.appId().trim() + "/" + s.getMeetingRoom(),
                jwt,
                me.getFullName(),
                null);
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            log.warn("Gagal menyiapkan akses JaaS; akses meeting ditolak");
            throw new ValidationException("Akses meeting tidak tersedia saat ini. Silakan coba lagi nanti.");
        }
    }

    private static MeetingAccess linkOnly(String url) {
        return new MeetingAccess(false, null, null, null, null, null, url);
    }

    // ------------------------------------------------------------------ JWT

    private String signJwt(User u, String room, boolean moderator) throws GeneralSecurityException, IOException {
        long now = Instant.now().getEpochSecond();
        String appId = jaas.appId().trim();
        String keyId = jaas.keyId().trim();
        String kid = keyId.contains("/") ? keyId : appId + "/" + keyId;

        String header = "{\"alg\":\"RS256\",\"kid\":" + q(kid) + ",\"typ\":\"JWT\"}";
        String payload = "{"
            + "\"aud\":\"jitsi\","
            + "\"iss\":\"chat\","
            + "\"sub\":" + q(appId) + ","
            + "\"room\":" + q(room) + ","
            + "\"nbf\":" + (now - 10) + ","
            + "\"exp\":" + (now + TOKEN_TTL_SECONDS) + ","
            + "\"context\":{"
            +   "\"user\":{\"id\":" + q(u.getId().toString())
            +     ",\"name\":" + q(u.getFullName())
            +     ",\"email\":" + q(u.getEmail())
            +     ",\"moderator\":" + q(Boolean.toString(moderator)) + "},"
            +   "\"features\":{\"livestreaming\":\"false\",\"outbound-call\":\"false\","
            +     "\"transcription\":\"false\",\"recording\":\"false\"},"
            +   "\"room\":{\"regex\":false}"
            + "}}";

        String signingInput = b64(header.getBytes(StandardCharsets.UTF_8))
            + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey());
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64(sig.sign());
    }

    private PrivateKey privateKey() throws GeneralSecurityException, IOException {
        PrivateKey k = cachedKey;
        if (k != null) return k;
        String pem = Files.readString(Path.of(jaas.privateKeyFile().trim()));
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new InvalidKeySpecException(
                "Kunci berformat PKCS#1. Ubah ke PKCS#8: "
                    + "openssl pkcs8 -topk8 -nocrypt -in kunci.pem -out kunci-pkcs8.pem");
        }
        String body = pem.replaceAll("-----(BEGIN|END)[A-Z ]*-----", "").replaceAll("\\s", "");
        k = KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        cachedKey = k;
        return k;
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** String JSON dengan escape yang benar (nama pengguna bisa berisi tanda kutip dll). */
    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
