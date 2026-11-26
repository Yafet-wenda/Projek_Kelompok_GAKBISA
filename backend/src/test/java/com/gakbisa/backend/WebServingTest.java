package com.gakbisa.backend;

import com.gakbisa.backend.config.*;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.repository.*;
import com.gakbisa.backend.service.SupabaseStorageService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(WebServingTest.Config.class)
@WebAppConfiguration
@TestPropertySource(properties = {"gakbisa.security.jwt-secret=", "gakbisa.storage.base-url=",
    "gakbisa.storage.service-key=", "gakbisa.storage.max-file-size-bytes=52428800",
    "gakbisa.storage.allowed-mime-types=image/jpeg,image/png,image/webp,video/mp4,video/webm,application/pdf",
    "SUPABASE_URL=", "SUPABASE_ANON_KEY=", "SUPABASE_SERVICE_KEY="})
class WebServingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @ComponentScan(basePackages = {"com.gakbisa.backend.controller", "com.gakbisa.backend.service", "com.gakbisa.backend.security"},
        excludeFilters = {@ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SupabaseStorageService.class)})
    @Import({StorageConfig.class, JaasConfig.class, SupabaseConfig.class, GlobalExceptionHandler.class, WebExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:web-panels-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.gakbisa.backend.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean SupabaseStorageService storage() { return mock(SupabaseStorageService.class); }
        @Bean org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator() {
            return new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        }
        @Bean SpringTemplateEngine templateEngine(ApplicationContext context) {
            var resolver = new SpringResourceTemplateResolver();
            resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode("HTML");
            resolver.setCharacterEncoding("UTF-8");
            var engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            engine.addDialect(new SpringSecurityDialect());
            return engine;
        }
        @Bean ThymeleafViewResolver viewResolver(SpringTemplateEngine engine) {
            var resolver = new ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/css/**", "/js/**", "/images/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/", "classpath:/static/images/");
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PortfolioRepository portfolios;
    @Autowired SkillCategoryRepository categories;
    @Autowired SkillRepository skills;
    @Autowired UserSkillRepository userSkills;
    @Autowired BarterRequestRepository barters;
    @Autowired LearningSessionRepository sessions;
    @Autowired ReviewRepository reviews;
    @Autowired ReportRepository reports;
    @Autowired PasswordEncoder encoder;
    @Autowired SupabaseStorageService storage;
    MockMvc mvc;
    User student;
    User partner;
    User admin;
    String password;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        password = UUID.randomUUID().toString();
        String hash = encoder.encode(password);
        student = createUser("Mahasiswa Uji", UserRole.STUDENT, hash);
        partner = createUser("Partner Uji", UserRole.STUDENT, hash);
        admin = createUser("Admin Uji", UserRole.ADMIN, hash);
        reset(storage);
        when(storage.download(any(), any(), anyString()))
            .thenReturn(new SupabaseStorageService.FileContent("test-content".getBytes(), "application/pdf"));
    }

    User createUser(String name, UserRole role, String hash) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash(hash);
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return users.saveAndFlush(user);
    }

    MockHttpSession login(User user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("email", user.getEmail()).param("password", password))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"))
            .andReturn().getRequest().getSession(false);
    }

    @Test void rootAndLoginShowServerRenderedWeb() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/index.html")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"))
            .andExpect(content().string(matchesPattern("(?s).*<input[^>]*type=\"email\"[^>]*name=\"email\".*")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(not(containsString("js/main.js"))));
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("auth/register"));
    }

    @Test void catalogKeepsGuestAccessAndAuthenticatedNavigation() throws Exception {
        mvc.perform(get("/catalog")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"gkSidebar\""))));
        mvc.perform(get("/catalog").session(login(student))).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"gkSidebar\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/matching", "/barter", "/sessions", "/notifications", "/profile",
        "/profile/edit", "/profile/skills", "/portfolio", "/portfolio/upload", "/catalog"})
    void studentPagesRender(String path) throws Exception {
        mvc.perform(get(path).session(login(student))).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(not(containsString("/admin/users"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/dashboard", "/admin/users", "/admin/categories", "/admin/reports"})
    void adminPagesRenderAndRejectStudents(String path) throws Exception {
        mvc.perform(get(path).session(login(admin))).andExpect(status().isOk())
            .andExpect(content().string(containsString("/admin/users")));
        mvc.perform(get(path).session(login(student))).andExpect(status().isForbidden());
    }

    @Test void adminLandsOnAdminDashboard() throws Exception {
        mvc.perform(get("/dashboard").session(login(admin))).andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test void sessionDoesNotAuthenticateStatelessApi() throws Exception {
        MockHttpSession session = login(student);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/chat/rooms").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/web/api/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("id").value(student.getId().toString()));
        mvc.perform(get("/web/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
    }

    @Test void bearerLoginStillWorksWithoutCsrfOrSession() throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + student.getEmail() + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("accessToken").isString()).andReturn();
        assertNull(response.getRequest().getSession(false));
        String body = response.getResponse().getContentAsString();
        String token = body.split("\"accessToken\":\"")[1].split("\"")[0];
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/chat/rooms").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test void browserLoginLogoutAndWritesRequireCsrf() throws Exception {
        mvc.perform(post("/login").param("email", student.getEmail()).param("password", password))
            .andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("email", student.getEmail()).param("password", UUID.randomUUID().toString()))
            .andExpect(redirectedUrl("/login?error"));
        MockHttpSession session = login(student);
        mvc.perform(post("/profile/edit").session(session).param("fullName", "Tidak boleh"))
            .andExpect(status().isForbidden());
        assertEquals("Mahasiswa Uji", users.findById(student.getId()).orElseThrow().getFullName());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());
        mvc.perform(get("/dashboard")).andExpect(redirectedUrl("/login"));
    }

    @Test void registrationNeverCreatesAdmin() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/register").with(csrf()).param("fullName", "Akun Form")
                .param("email", email).param("password", password).param("campus", "Kampus Uji"))
            .andExpect(redirectedUrl("/login?registered"));
        assertEquals(UserRole.STUDENT, users.findByEmail(email).orElseThrow().getRole());
    }

    @Test void profileAndSkillFormsPersistData() throws Exception {
        MockHttpSession session = login(student);
        mvc.perform(post("/profile/edit").session(session).with(csrf()).param("fullName", "Nama Diperbarui")
                .param("campus", "Kampus Uji").param("bio", "Belajar bersama"))
            .andExpect(status().is3xxRedirection());
        assertEquals("Nama Diperbarui", users.findById(student.getId()).orElseThrow().getFullName());
        mvc.perform(post("/profile/skills").session(session).with(csrf()).param("skillName", "Java " + UUID.randomUUID())
                .param("type", "OFFER").param("level", "BEGINNER"))
            .andExpect(status().is3xxRedirection());
        assertEquals(1, userSkills.findByUserId(student.getId()).size());
        mvc.perform(get("/profile/skills").session(session)).andExpect(status().isOk());
    }

    @Test void suspendedAccountsAndChangedRolesTakeEffectInExistingSessions() throws Exception {
        MockHttpSession session = login(admin);
        admin.setRole(UserRole.STUDENT);
        users.saveAndFlush(admin);
        mvc.perform(get("/admin/users").session(session)).andExpect(status().isForbidden());
        admin.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(admin);
        mvc.perform(get("/dashboard").session(session)).andExpect(status().is3xxRedirection());
        assertTrue(session.isInvalid());
    }

    Portfolio portfolio(User owner, PortfolioVisibility visibility) {
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(owner);
        portfolio.setTitle("Karya " + visibility);
        portfolio.setVisibility(visibility);
        portfolio.setFileName("sample.pdf");
        portfolio.setFilePath("test/sample.pdf");
        portfolio.setMimeType("application/pdf");
        portfolio.setFileSize(12L);
        return portfolios.saveAndFlush(portfolio);
    }

    @Test void privatePortfolioDoesNotLeakThroughRestoredPagesOrMedia() throws Exception {
        Portfolio privateWork = portfolio(partner, PortfolioVisibility.PRIVATE);
        Portfolio publicWork = portfolio(partner, PortfolioVisibility.PUBLIC);
        MockHttpSession viewer = login(student);
        for (String path : new String[]{"/profile/" + partner.getId(), "/portfolio/user/" + partner.getId()}) {
            mvc.perform(get(path).session(viewer)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(privateWork.getTitle()))))
                .andExpect(content().string(not(containsString(partner.getEmail()))))
                .andExpect(model().attribute("portfolios", contains(hasProperty("id", is(publicWork.getId())))));
        }
        mvc.perform(get("/web/portfolios/" + privateWork.getId() + "/content").session(viewer))
            .andExpect(status().isForbidden());
        mvc.perform(get("/web/portfolios/" + privateWork.getId() + "/content"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/web/portfolios/" + publicWork.getId() + "/content"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/portfolio/" + privateWork.getId() + "/edit").session(login(partner)))
            .andExpect(status().isOk());
    }

    BarterRequest acceptedBarter() {
        SkillCategory category = new SkillCategory();
        category.setName("Kategori " + UUID.randomUUID());
        categories.saveAndFlush(category);
        Skill skill = new Skill();
        skill.setName("Keahlian " + UUID.randomUUID());
        skill.setCategory(category);
        skills.saveAndFlush(skill);
        UserSkill offer = new UserSkill();
        offer.setUser(student); offer.setSkill(skill); offer.setType(SkillType.OFFER); offer.setLevel(SkillLevel.BEGINNER);
        userSkills.saveAndFlush(offer);
        UserSkill want = new UserSkill();
        want.setUser(student); want.setSkill(skill); want.setType(SkillType.WANT); want.setLevel(SkillLevel.BEGINNER);
        userSkills.saveAndFlush(want);
        BarterRequest barter = new BarterRequest();
        barter.setRequester(student); barter.setTargetUser(partner);
        barter.setOfferedUserSkill(offer); barter.setWantedUserSkill(want); barter.setStatus(BarterStatus.ACCEPTED);
        return barters.saveAndFlush(barter);
    }

    @Test void populatedBarterAndSessionPagesRespectParticipantsAndLifecycle() throws Exception {
        BarterRequest barter = acceptedBarter();
        MockHttpSession owner = login(student);
        mvc.perform(get("/barter/" + barter.getId()).session(owner)).andExpect(status().isOk());
        mvc.perform(get("/barter/" + barter.getId()).session(login(admin))).andExpect(status().isForbidden());
        LearningSession learning = new LearningSession();
        learning.setBarterRequest(barter); learning.setScheduledAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        learning.setDurationMinutes(60); learning.setStatus(SessionStatus.SCHEDULED);
        learning.setMeetingProvider(MeetingProvider.JITSI); learning.setMeetingRoom("test-room");
        sessions.saveAndFlush(learning);
        mvc.perform(get("/sessions/" + learning.getId()).session(owner)).andExpect(status().isOk())
            .andExpect(content().string(containsString("gk-jitsi-start")));
        mvc.perform(get("/sessions").session(owner)).andExpect(status().isOk());
        mvc.perform(post("/sessions/" + learning.getId() + "/start").session(owner).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertEquals(SessionStatus.ONGOING, sessions.findById(learning.getId()).orElseThrow().getStatus());
        mvc.perform(post("/barter/" + barter.getId() + "/cancel").session(owner).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertEquals(SessionStatus.CANCELLED, sessions.findById(learning.getId()).orElseThrow().getStatus());
    }

    @Test void adminCategoryAndReportFormsUseExistingServices() throws Exception {
        MockHttpSession moderator = login(admin);
        String name = "Kategori Form " + UUID.randomUUID();
        mvc.perform(post("/admin/categories").session(moderator).with(csrf()).param("name", name))
            .andExpect(status().is3xxRedirection());
        assertTrue(categories.findByNameIgnoreCase(name).isPresent());
        mvc.perform(post("/reports").session(login(student)).with(csrf()).param("targetType", "USER")
                .param("targetId", partner.getId().toString()).param("reason", "Laporan uji moderasi"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/reports").session(moderator)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Laporan uji moderasi")));
    }

    @Test void scheduledSessionUsesBrowserTimezoneAndSupportsReview() throws Exception {
        BarterRequest barter = acceptedBarter();
        MockHttpSession owner = login(student);
        Instant at = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        String local = java.time.LocalDateTime.ofInstant(at, java.time.ZoneOffset.ofHours(9)).toString();
        mvc.perform(post("/sessions/create/" + barter.getId()).session(owner).with(csrf())
                .param("scheduledAt", local).param("timezoneOffset", "-540")
                .param("durationMinutes", "60").param("meetingProvider", "JITSI"))
            .andExpect(redirectedUrl("/sessions")).andExpect(flash().attributeExists("success"));
        LearningSession learning = sessions.findByBarterRequestId(barter.getId()).getFirst();
        assertEquals(at, learning.getScheduledAt());
        mvc.perform(get("/sessions/" + learning.getId()).session(owner)).andExpect(status().isOk())
            .andExpect(content().string(containsString("datetime=\"" + at + "\"")));
        mvc.perform(post("/sessions/" + learning.getId() + "/complete").session(owner).with(csrf()))
            .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/reviews/session/" + learning.getId()).session(owner).with(csrf())
                .param("rating", "5").param("comment", "Sesi bermanfaat"))
            .andExpect(flash().attributeExists("success"));
        mvc.perform(get("/sessions/" + learning.getId()).session(owner)).andExpect(status().isOk())
            .andExpect(model().attribute("hasReviewed", true));
        mvc.perform(get("/profile/" + partner.getId()).session(owner)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Sesi bermanfaat")));
    }

    @Test void portfolioUploadEditDeleteAndModerationEvidenceWork() throws Exception {
        MockHttpSession owner = login(partner);
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jY9kAAAAASUVORK5CYII=");
        mvc.perform(multipart("/portfolio/upload").file(new MockMultipartFile("file", "sample.png", "image/png", png))
                .session(owner).with(csrf()).param("title", "Karya uji unggah").param("visibility", "PUBLIC"))
            .andExpect(redirectedUrl("/portfolio"));
        Portfolio work = portfolios.findByUserId(partner.getId()).getFirst();
        verify(storage).upload(eq(partner.getId()), eq(work.getId()), anyString(), any(), eq("image/png"));
        mvc.perform(post("/reports").session(login(student)).with(csrf()).param("targetType", "PORTFOLIO")
                .param("targetId", work.getId().toString()).param("reason", "Periksa karya uji"))
            .andExpect(flash().attributeExists("success"));
        mvc.perform(get("/admin/reports").session(login(admin))).andExpect(status().isOk())
            .andExpect(content().string(containsString("/web/portfolios/" + work.getId() + "/content")));
        mvc.perform(post("/portfolio/" + work.getId() + "/edit").session(owner).with(csrf())
                .param("title", "Karya privat").param("visibility", "PRIVATE"))
            .andExpect(redirectedUrl("/portfolio"));
        assertEquals(PortfolioVisibility.PRIVATE, portfolios.findById(work.getId()).orElseThrow().getVisibility());
        mvc.perform(post("/portfolio/" + work.getId() + "/delete").session(owner).with(csrf()))
            .andExpect(flash().attributeExists("success"));
        assertFalse(portfolios.existsById(work.getId()));
    }

    @Test void invalidUploadReturnsUsableFormWithoutExternalStorageCall() throws Exception {
        var file = new MockMultipartFile("file", "unsafe.txt", "text/plain", "not media".getBytes());
        mvc.perform(multipart("/portfolio/upload").file(file).session(login(student)).with(csrf())
                .param("title", "Uji berkas").param("visibility", "PUBLIC"))
            .andExpect(status().isOk()).andExpect(view().name("portfolio/upload"));
        verify(storage, never()).upload(any(), any(), any(), any(), any());
    }
}
