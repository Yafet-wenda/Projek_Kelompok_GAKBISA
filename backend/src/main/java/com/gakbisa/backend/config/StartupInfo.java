package com.gakbisa.backend.config;

import com.gakbisa.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Ringkasan start yang cukup untuk operator: alamat, basis data yang dituju, dan isi akun.
 * Tidak mencetak password atau secret.
 */
@Component
public class StartupInfo implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupInfo.class);

    private final Environment env;
    private final UserRepository users;

    public StartupInfo(Environment env, UserRepository users) {
        this.env = env;
        this.users = users;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateProductionConfiguration();
        String basis = env.getProperty("spring.datasource.url", "")
            .replaceFirst("^jdbc:postgresql://", "").replaceFirst("\\?.*$", "");
        long akun = users.count();
        double detik = (System.currentTimeMillis() - event.getApplicationContext().getStartupDate()) / 1000.0;
        String kosong = akun == 0
            ? ", belum ada akun. Buka /register untuk membuat akun STUDENT."
            : ".";
        log.info("""

            GAKBISA siap dalam {} detik
              Web        : http://localhost:{}
              Basis data : {}
              Akun       : {}{}""",
            String.format("%.1f", detik), env.getProperty("server.port", "8080"), basis, akun, kosong);
    }

    private void validateProductionConfiguration() {
        boolean production = java.util.Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (!production) return;

        require(env.getProperty("spring.datasource.password"), "DB_PASSWORD");
        require(env.getProperty("gakbisa.storage.base-url"), "SUPABASE_URL");
        require(env.getProperty("gakbisa.storage.service-key"), "SUPABASE_SERVICE_KEY");

        String dbUrl = env.getProperty("spring.datasource.url", "");
        if (!dbUrl.contains("sslmode=require")) {
            throw new IllegalStateException("Koneksi database produksi wajib memakai sslmode=require");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " wajib diisi saat profile prod aktif");
        }
        if (value.contains("GANTI_DENGAN") || value.contains("<")) {
            throw new IllegalStateException(name + " masih memakai nilai placeholder");
        }
    }
}
