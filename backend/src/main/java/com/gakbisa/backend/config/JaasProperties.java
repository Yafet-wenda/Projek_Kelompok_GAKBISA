package com.gakbisa.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pengaturan Jitsi as a Service (JaaS, domain 8x8.vc). Kalau salah satu kosong,
 * meeting tidak ditanam di halaman dan tombolnya membuka Jitsi publik di tab baru.
 *
 * @param appId          AppID dari JaaS Developer Console, berawalan "vpaas-magic-cookie-"
 * @param keyId          ID API key. Boleh bagian setelah "/" saja, boleh juga kid lengkap
 * @param privateKeyFile path berkas private key (PEM, format PKCS#8) di mesin server
 */
@ConfigurationProperties(prefix = "gakbisa.jaas")
public record JaasProperties(String appId, String keyId, String privateKeyFile) {

    public boolean configured() {
        return filled(appId) && filled(keyId) && filled(privateKeyFile);
    }

    private static boolean filled(String s) {
        return s != null && !s.isBlank();
    }
}
