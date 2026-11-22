package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.BarterDto;
import com.gakbisa.backend.dto.SessionCreateRequest;
import com.gakbisa.backend.repository.BarterRequestRepository;
import com.gakbisa.backend.repository.LearningSessionRepository;
import com.gakbisa.backend.repository.UserRepository;
import com.gakbisa.backend.repository.UserSkillRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BarterSessionConcurrencyTest {

    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManager em;
    private JpaTransactionManager transactionManager;
    private TransactionTemplate transaction;
    private BarterRequestRepository barter;
    private LearningSessionRepository sessions;
    private BarterService barterService;
    private SessionService sessionService;
    private ExecutorService executor;
    private final ThreadLocal<CountDownLatch> lockAttempt = new ThreadLocal<>();
    private final List<String> lockingSql = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setup() {
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(
            "jdbc:h2:mem:barter-session-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", ""));
        factory.setManagedTypes(PersistenceManagedTypes.of(
            User.class.getName(), SkillCategory.class.getName(), Skill.class.getName(),
            UserSkill.class.getName(), BarterRequest.class.getName(), LearningSession.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
            "hibernate.hbm2ddl.auto", "create-drop",
            "hibernate.hbm2ddl.halt_on_error", "true",
            "hibernate.session_factory.statement_inspector", (StatementInspector) sql -> {
                if (sql.toLowerCase(Locale.ROOT).contains("for update")) lockingSql.add(sql);
                return sql;
            }));
        factory.afterPropertiesSet();
        var emf = factory.getObject();
        assertNotNull(emf);
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        transactionManager = new JpaTransactionManager(emf);
        transaction = new TransactionTemplate(transactionManager);
        var repositories = new JpaRepositoryFactory(em);
        var realBarter = repositories.getRepository(BarterRequestRepository.class);
        barter = mock(BarterRequestRepository.class, delegatesTo(realBarter));
        doAnswer(inv -> {
            CountDownLatch attempt = lockAttempt.get();
            if (attempt != null) attempt.countDown();
            return realBarter.findByIdForUpdate(inv.getArgument(0));
        }).when(barter).findByIdForUpdate(any());
        sessions = mock(LearningSessionRepository.class,
            delegatesTo(repositories.getRepository(LearningSessionRepository.class)));
        var notifications = mock(NotificationService.class);
        barterService = transactional(new BarterService(barter, mock(UserRepository.class),
            mock(UserSkillRepository.class), notifications, sessions));
        sessionService = transactional(new SessionService(sessions, barter, barterService, notifications));
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        try {
            if (executor != null) {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        } finally {
            if (factory != null) factory.destroy();
        }
    }

    @Test
    void cancelBeforeScheduleRejectsNewSession() throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED);
        race(() -> barterService.cancel(f.requesterId(), f.barterId()),
            () -> assertThrows(ValidationException.class, () -> sessionService.schedule(f.targetId(), request(f))));
        assertState(f, BarterStatus.CANCELLED);
    }

    @Test
    void scheduleBeforeCancelCascadesNewSession() throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED);
        race(() -> sessionService.schedule(f.targetId(), request(f)),
            () -> barterService.cancel(f.requesterId(), f.barterId()));
        transaction.executeWithoutResult(tx -> {
            assertEquals(BarterStatus.CANCELLED, em.find(BarterRequest.class, f.barterId()).getStatus());
            List<LearningSession> children = sessions.findByBarterRequestId(f.barterId());
            assertEquals(1, children.size());
            assertEquals(SessionStatus.CANCELLED, children.getFirst().getStatus());
        });
    }

    @Test
    void cancelBeforeRescheduleRejectsStaleSession() throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.SCHEDULED);
        Instant originalTime = transaction.execute(tx -> em.find(LearningSession.class, f.sessionId(0)).getScheduledAt());
        race(() -> barterService.cancel(f.requesterId(), f.barterId()),
            () -> assertThrows(ValidationException.class, () -> sessionService.reschedule(
                f.targetId(), f.sessionId(0), Instant.now().plusSeconds(7200), 30)));
        assertState(f, BarterStatus.CANCELLED, SessionStatus.CANCELLED);
        transaction.executeWithoutResult(tx -> {
            LearningSession s = em.find(LearningSession.class, f.sessionId(0));
            assertEquals(originalTime, s.getScheduledAt());
            assertEquals(60, s.getDurationMinutes());
        });
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"ONGOING", "COMPLETED"})
    void cancelBeforeStatusChangeCannotResumeSession(SessionStatus next) throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.SCHEDULED);
        race(() -> barterService.cancel(f.requesterId(), f.barterId()),
            () -> assertThrows(ValidationException.class,
                () -> sessionService.setStatus(f.targetId(), f.sessionId(0), next)));
        assertState(f, BarterStatus.CANCELLED, SessionStatus.CANCELLED);
        transaction.executeWithoutResult(tx -> assertNull(em.find(LearningSession.class, f.sessionId(0)).getCompletedAt()));
    }

    @Test
    void completeAThenCancelBReconcilesAfterWaitingForParent() throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.ONGOING, SessionStatus.SCHEDULED);
        race(() -> sessionService.setStatus(f.requesterId(), f.sessionId(0), SessionStatus.COMPLETED),
            () -> sessionService.setStatus(f.targetId(), f.sessionId(1), SessionStatus.CANCELLED));
        assertState(f, BarterStatus.COMPLETED, SessionStatus.COMPLETED, SessionStatus.CANCELLED);
    }

    @Test
    void cancelBThenCompleteAAlsoReconciles() throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.ONGOING, SessionStatus.SCHEDULED);
        race(() -> sessionService.setStatus(f.targetId(), f.sessionId(1), SessionStatus.CANCELLED),
            () -> sessionService.setStatus(f.requesterId(), f.sessionId(0), SessionStatus.COMPLETED));
        assertState(f, BarterStatus.COMPLETED, SessionStatus.COMPLETED, SessionStatus.CANCELLED);
    }

    @Test
    void completingLastSessionBeforeBarterCancelPreservesCompletedAggregate() throws Exception {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.ONGOING);
        race(() -> sessionService.setStatus(f.targetId(), f.sessionId(0), SessionStatus.COMPLETED),
            () -> assertThrows(ValidationException.class,
                () -> barterService.cancel(f.requesterId(), f.barterId())));
        assertState(f, BarterStatus.COMPLETED, SessionStatus.COMPLETED);
    }

    @Test
    void cancelBeforeRespondCannotReopenBarter() throws Exception {
        Fixture f = seed(BarterStatus.PENDING);
        race(() -> barterService.cancel(f.requesterId(), f.barterId()),
            () -> assertThrows(ValidationException.class,
                () -> barterService.respond(f.targetId(), f.barterId(), true)));
        assertState(f, BarterStatus.CANCELLED);
    }

    @Test
    void failedCascadeRollsBackParentAndChildren() {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.SCHEDULED, SessionStatus.ONGOING, SessionStatus.COMPLETED);
        doThrow(new IllegalStateException("save failed")).when(sessions).saveAll(any());

        assertThrows(IllegalStateException.class, () -> barterService.cancel(f.requesterId(), f.barterId()));

        assertState(f, BarterStatus.ACCEPTED, SessionStatus.SCHEDULED, SessionStatus.ONGOING, SessionStatus.COMPLETED);
    }

    @Test
    void cancellationPreservesHistoryAndLocksOnlyParentWithoutGraphJoins() {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.SCHEDULED, SessionStatus.ONGOING,
            SessionStatus.COMPLETED, SessionStatus.CANCELLED);
        Instant completedAt = transaction.execute(tx -> em.find(LearningSession.class, f.sessionId(2)).getCompletedAt());

        BarterRequest cancelled = barterService.cancel(f.requesterId(), f.barterId());

        assertDoesNotThrow(() -> BarterDto.from(cancelled));
        assertState(f, BarterStatus.CANCELLED, SessionStatus.CANCELLED, SessionStatus.CANCELLED,
            SessionStatus.COMPLETED, SessionStatus.CANCELLED);
        transaction.executeWithoutResult(tx ->
            assertEquals(completedAt, em.find(LearningSession.class, f.sessionId(2)).getCompletedAt()));
        assertFalse(lockingSql.isEmpty());
        for (String sql : lockingSql) {
            String normalized = sql.toLowerCase(Locale.ROOT);
            assertTrue(normalized.contains("barter_requests"), sql);
            assertFalse(normalized.contains(" join "), sql);
            assertFalse(normalized.contains("learning_sessions"), sql);
        }
    }

    @Test
    void cancellingEverySessionAllowsAnotherSchedule() {
        Fixture f = seed(BarterStatus.ACCEPTED, SessionStatus.SCHEDULED, SessionStatus.ONGOING);
        sessionService.setStatus(f.requesterId(), f.sessionId(0), SessionStatus.CANCELLED);
        sessionService.setStatus(f.targetId(), f.sessionId(1), SessionStatus.CANCELLED);
        assertState(f, BarterStatus.ACCEPTED, SessionStatus.CANCELLED, SessionStatus.CANCELLED);
        assertEquals(SessionStatus.SCHEDULED, sessionService.schedule(f.targetId(), request(f)).getStatus());
    }

    private void race(Runnable first, Runnable second) throws Exception {
        CountDownLatch firstChanged = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLockAttempt = new CountDownLatch(1);
        var firstResult = executor.submit(() -> transaction.executeWithoutResult(tx -> {
            first.run();
            firstChanged.countDown();
            await(releaseFirst);
        }));
        try {
            await(firstChanged);
            var secondResult = executor.submit(() -> {
                lockAttempt.set(secondLockAttempt);
                try {
                    second.run();
                } finally {
                    lockAttempt.remove();
                }
            });
            await(secondLockAttempt);
            assertThrows(TimeoutException.class, () -> secondResult.get(150, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            firstResult.get(10, TimeUnit.SECONDS);
            secondResult.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for concurrent transaction");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (T) proxy.getProxy();
    }

    private Fixture seed(BarterStatus status, SessionStatus... sessionStatuses) {
        return transaction.execute(tx -> {
            User requester = user("Requester");
            User target = user("Target");
            SkillCategory category = new SkillCategory();
            category.setName("Development");
            em.persist(category);
            Skill skill = new Skill();
            skill.setCategory(category);
            skill.setName("Java");
            em.persist(skill);
            UserSkill offered = userSkill(requester, skill, SkillType.OFFER);
            UserSkill wanted = userSkill(requester, skill, SkillType.WANT);
            BarterRequest b = new BarterRequest();
            b.setRequester(requester);
            b.setTargetUser(target);
            b.setOfferedUserSkill(offered);
            b.setWantedUserSkill(wanted);
            b.setStatus(status);
            em.persist(b);
            List<UUID> sessionIds = new ArrayList<>();
            for (SessionStatus sessionStatus : sessionStatuses) {
                LearningSession s = new LearningSession();
                s.setBarterRequest(b);
                s.setStatus(sessionStatus);
                s.setScheduledAt(Instant.now().plusSeconds(60));
                s.setDurationMinutes(60);
                s.setMeetingRoom("gakbisa-" + UUID.randomUUID());
                if (sessionStatus == SessionStatus.COMPLETED) s.setCompletedAt(Instant.now().minusSeconds(60));
                em.persist(s);
                sessionIds.add(s.getId());
            }
            return new Fixture(b.getId(), requester.getId(), target.getId(), sessionIds);
        });
    }

    private User user(String name) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(name.toLowerCase(Locale.ROOT) + "@gakbisa.test");
        user.setPasswordHash("unused-test-hash");
        em.persist(user);
        return user;
    }

    private UserSkill userSkill(User user, Skill skill, SkillType type) {
        UserSkill userSkill = new UserSkill();
        userSkill.setUser(user);
        userSkill.setSkill(skill);
        userSkill.setType(type);
        em.persist(userSkill);
        return userSkill;
    }

    private SessionCreateRequest request(Fixture f) {
        return new SessionCreateRequest(f.barterId(), Instant.now().plusSeconds(120), 60, null);
    }

    private void assertState(Fixture f, BarterStatus parentStatus, SessionStatus... statuses) {
        transaction.executeWithoutResult(tx -> {
            assertEquals(parentStatus, em.find(BarterRequest.class, f.barterId()).getStatus());
            assertEquals(statuses.length, sessions.findByBarterRequestId(f.barterId()).size());
            for (int i = 0; i < statuses.length; i++) {
                assertEquals(statuses[i], em.find(LearningSession.class, f.sessionId(i)).getStatus());
            }
        });
    }

    private record Fixture(UUID barterId, UUID requesterId, UUID targetId, List<UUID> sessionIds) {
        UUID sessionId(int index) {
            return sessionIds.get(index);
        }
    }
}
