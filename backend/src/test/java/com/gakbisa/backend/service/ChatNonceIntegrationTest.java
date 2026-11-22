package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.*;
import com.gakbisa.backend.dto.ChatDtos.MessageEditRequest;
import com.gakbisa.backend.dto.ChatDtos.MessageRequest;
import com.gakbisa.backend.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(ChatNonceIntegrationTest.Config.class)
class ChatNonceIntegrationTest {

    private static final String IV_A = "AAAAAAAAAAAAAAAA";
    private static final String IV_B = "AQEBAQEBAQEBAQEB";
    private static final String CIPHERTEXT = "AAAAAAAAAAAAAAAAAAAAAA==";

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ChatRoomRepository.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:chat-nonce-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.gakbisa.backend.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        ChatService chatService(UserRepository users, UserKeyRepository userKeys, ChatRoomRepository rooms,
                                ChatMemberRepository members, ChatRoomKeyRepository roomKeys,
                                ChatMessageRepository messages, ChatMessageNonceRepository nonces) {
            return new ChatService(users, userKeys, rooms, members, roomKeys, messages, nonces);
        }
    }

    @Autowired private ChatService service;
    @Autowired private UserRepository users;
    @Autowired private ChatRoomRepository rooms;
    @Autowired private ChatMemberRepository members;
    @Autowired private ChatMessageNonceRepository nonces;
    @Autowired private PlatformTransactionManager transactions;

    private UUID actorId;
    private UUID roomId;
    private TransactionTemplate tx;

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            User actor = new User();
            actor.setFullName("Pengguna uji");
            actor.setEmail(UUID.randomUUID() + "@example.test");
            actor.setPasswordHash("not-a-login-credential");
            users.saveAndFlush(actor);
            actorId = actor.getId();
            ChatRoom room = new ChatRoom();
            room.setType(ChatRoomType.GROUP);
            room.setName("Ruang uji");
            room.setCreatedBy(actor);
            rooms.saveAndFlush(room);
            roomId = room.getId();
            ChatMember member = new ChatMember();
            member.setRoom(room);
            member.setUser(actor);
            member.setRole(ChatMemberRole.OWNER);
            members.saveAndFlush(member);
        });
    }

    @Test
    void editsAndDeletesNeverReleaseEarlierNonces() {
        var sent = service.sendMessage(actorId, roomId, request(1, IV_A));
        var edited = service.editMessage(actorId, roomId, sent.id(), new MessageEditRequest(CIPHERTEXT, IV_B));
        assertEquals(IV_B, edited.iv());
        assertThrows(ValidationException.class, () -> service.editMessage(actorId, roomId, sent.id(),
            new MessageEditRequest(CIPHERTEXT, IV_A)));
        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId, request(1, IV_A)));
        service.deleteMessage(actorId, roomId, sent.id());
        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId, request(1, IV_B)));
        assertEquals(1, service.history(actorId, roomId, null, null, 20).size());
        assertNull(service.history(actorId, roomId, null, null, 20).getFirst().ciphertext());
    }

    @Test
    void databaseRejectsDuplicateReservation() {
        service.sendMessage(actorId, roomId, request(1, IV_A));
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status ->
            nonces.saveAndFlush(new ChatMessageNonce(rooms.getReferenceById(roomId), 1, IV_A))));
    }

    @Test
    void rollbackRemovesBothReservationAndMessage() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            service.sendMessage(actorId, roomId, request(1, IV_A));
            throw new IllegalStateException("rollback uji");
        }));
        assertTrue(service.history(actorId, roomId, null, null, 20).isEmpty());
        assertDoesNotThrow(() -> service.sendMessage(actorId, roomId, request(1, IV_A)));
    }

    @Test
    void simultaneousSendsWithSameNonceAcceptOnlyOne() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> sendTogether(ready, start));
            var second = executor.submit(() -> sendTogether(ready, start));
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, service.history(actorId, roomId, null, null, 20).size());
    }

    private int sendTogether(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Permintaan uji tidak mulai");
        try {
            service.sendMessage(actorId, roomId, request(1, IV_A));
            return 1;
        } catch (ValidationException expected) {
            assertTrue(expected.getMessage().contains("IV sudah pernah"));
            return 0;
        }
    }

    @Test
    void retiredEpochBlocksWritesUntilRotationAndOldMessageEditsStayBlocked() {
        var old = service.sendMessage(actorId, roomId, request(1, IV_A));
        tx.executeWithoutResult(status -> rooms.findByIdForUpdate(roomId).orElseThrow().setMinMessageKeyVersion(2));
        assertThrows(ValidationException.class, () -> service.sendMessage(actorId, roomId, request(1, IV_B)));
        tx.executeWithoutResult(status -> rooms.findByIdForUpdate(roomId).orElseThrow().setCurrentKeyVersion(2));
        assertDoesNotThrow(() -> service.sendMessage(actorId, roomId, request(2, IV_A)));
        assertThrows(ValidationException.class, () -> service.editMessage(actorId, roomId, old.id(),
            new MessageEditRequest(CIPHERTEXT, IV_B)));
    }

    @Test
    void migrationPreservesNoncesAndRetiresOnlyIncompleteCurrentEpochs() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE chat_rooms (id UUID PRIMARY KEY, current_key_version INTEGER NOT NULL)");
            statement.execute("CREATE TABLE chat_messages (room_id UUID, key_version INTEGER, iv VARCHAR(16), edited_at TIMESTAMP WITH TIME ZONE, deleted_at TIMESTAMP WITH TIME ZONE)");
            List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            try (var insert = connection.prepareStatement("INSERT INTO chat_rooms VALUES (?, 3)")) {
                for (UUID id : ids) {
                    insert.setObject(1, id);
                    insert.executeUpdate();
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO chat_messages VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < ids.size(); i++) {
                    insert.setObject(1, ids.get(i));
                    insert.setInt(2, i == 3 ? 2 : 3);
                    insert.setString(3, i == 2 ? null : IV_A);
                    insert.setObject(4, i == 1 || i == 3 ? java.time.OffsetDateTime.now() : null);
                    insert.setObject(5, i == 2 ? java.time.OffsetDateTime.now() : null);
                    insert.executeUpdate();
                }
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__retain_chat_message_nonces.sql"));
            try (var query = connection.prepareStatement("SELECT min_message_key_version FROM chat_rooms WHERE id = ?")) {
                for (int i = 0; i < ids.size(); i++) {
                    query.setObject(1, ids.get(i));
                    try (var result = query.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals(i == 1 || i == 2 ? 4 : 1, result.getInt(1));
                    }
                }
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM chat_message_nonces")) {
                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM chat_messages")) {
                assertTrue(result.next());
                assertEquals(4, result.getInt(1));
            }
        }
    }

    private static MessageRequest request(int version, String iv) {
        return new MessageRequest(version, ChatMessageType.TEXT, CIPHERTEXT, iv, null, null);
    }
}
