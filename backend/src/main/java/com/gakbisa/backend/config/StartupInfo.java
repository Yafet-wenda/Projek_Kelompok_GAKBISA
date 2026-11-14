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
        String basis = env.getProperty("spring.datasource.url", "")
            .replaceFirst("^jdbc:postgresql://", "").replaceFirst("\\?.*$", "");
        long akun = users.count();
        double detik = (System.currentTimeMillis() - event.getApplicationContext().getStartupDate()) / 1000.0;
        String kosong = akun == 0
            ? ", belum ada akun. Daftar di /register dan pendaftar pertama jadi admin."
            : ".";
        log.info("""

            GAKBISA siap dalam {} detik
              Alamat     : http://localhost:{}
              Basis data : {}
              Akun       : {}{}""",
            String.format("%.1f", detik), env.getProperty("server.port", "8080"), basis, akun, kosong);
    }
}
