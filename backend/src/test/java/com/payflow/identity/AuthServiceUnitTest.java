package com.payflow.identity;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.identity.domain.MerchantUser;
import com.payflow.identity.domain.User;
import com.payflow.identity.domain.UserRole;
import com.payflow.identity.repository.MerchantUserRepository;
import com.payflow.identity.repository.UserRepository;
import com.payflow.identity.service.AuthService;
import com.payflow.identity.service.JwtTokenService;
import com.payflow.merchant.domain.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceUnitTest {

    private FakeUserRepository userRepository;
    private FakeMerchantUserRepository merchantUserRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService jwtTokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        merchantUserRepository = new FakeMerchantUserRepository();
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtTokenService = new JwtTokenService(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
                3600000
        );
        authService = new AuthService(userRepository, merchantUserRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void testSuccessfulLogin() {
        String email = "admin@acme.com";
        String password = "password123";
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode(password), "ACTIVE");
        userRepository.save(user);

        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme Corp", "ACTIVE");
        MerchantUser mu = new MerchantUser(UUID.randomUUID(), merchant, user, UserRole.MERCHANT_ADMIN);
        merchantUserRepository.save(mu);

        AuthService.LoginResult result = authService.login(email, password, null);

        assertNotNull(result);
        assertNotNull(result.token());
        assertEquals(user.getId(), result.userId());
        assertEquals(email, result.email());
        assertEquals(merchant.getId(), result.merchantId());
        assertEquals("MERCHANT_ADMIN", result.role());
    }

    @Test
    @DisplayName("Should reject login with incorrect password")
    void testIncorrectPassword() {
        String email = "admin@acme.com";
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("correctPassword"), "ACTIVE");
        userRepository.save(user);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                authService.login(email, "wrongPassword", null));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should reject login for unknown user")
    void testUnknownUser() {
        PayflowException ex = assertThrows(PayflowException.class, () ->
                authService.login("nonexistent@acme.com", "anyPassword", null));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should reject login for suspended user")
    void testSuspendedUser() {
        String email = "banned@acme.com";
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "SUSPENDED");
        userRepository.save(user);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                authService.login(email, "password123", null));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // Fakes
    static class FakeUserRepository implements UserRepository {
        private final Map<UUID, User> db = new HashMap<>();

        @Override public Optional<User> findByEmailIgnoreCase(String email) {
            return db.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
        }
        @Override public <S extends User> S save(S entity) { db.put(entity.getId(), entity); return entity; }
        @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(db.get(id)); }
        @Override public boolean existsById(UUID id) { return db.containsKey(id); }
        @Override public long count() { return db.size(); }
        @Override public List<User> findAll() { return new ArrayList<>(db.values()); }
        @Override public void deleteById(UUID id) { db.remove(id); }
        @Override public void delete(User entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll(Iterable<? extends User> entities) {}
        @Override public void deleteAll() { db.clear(); }
        @Override public List<User> findAllById(Iterable<UUID> ids) { return Collections.emptyList(); }
        @Override public <S extends User> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void flush() {}
        @Override public <S extends User> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void deleteAllInBatch(Iterable<User> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public User getOne(UUID id) { return db.get(id); }
        @Override public User getById(UUID id) { return db.get(id); }
        @Override public User getReferenceById(UUID id) { return db.get(id); }
        @Override public <S extends User> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends User> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends User> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends User> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends User> long count(Example<S> example) { return 0; }
        @Override public <S extends User> boolean exists(Example<S> example) { return false; }
        @Override public <S extends User, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<User> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<User> findAll(Pageable pageable) { return Page.empty(); }
    }

    static class FakeMerchantUserRepository implements MerchantUserRepository {
        private final Map<UUID, MerchantUser> db = new HashMap<>();

        @Override public Optional<MerchantUser> findByMerchantIdAndUserId(UUID merchantId, UUID userId) {
            return db.values().stream().filter(mu -> mu.getMerchant().getId().equals(merchantId) && mu.getUser().getId().equals(userId)).findFirst();
        }
        @Override public List<MerchantUser> findAllByUserIdWithMerchant(UUID userId) {
            return db.values().stream().filter(mu -> mu.getUser().getId().equals(userId)).toList();
        }
        @Override public List<MerchantUser> findAllByMerchantId(UUID merchantId) {
            return db.values().stream().filter(mu -> mu.getMerchant().getId().equals(merchantId)).toList();
        }
        @Override public <S extends MerchantUser> S save(S entity) { db.put(entity.getId(), entity); return entity; }
        @Override public Optional<MerchantUser> findById(UUID id) { return Optional.ofNullable(db.get(id)); }
        @Override public boolean existsById(UUID id) { return db.containsKey(id); }
        @Override public long count() { return db.size(); }
        @Override public List<MerchantUser> findAll() { return new ArrayList<>(db.values()); }
        @Override public void deleteById(UUID id) { db.remove(id); }
        @Override public void delete(MerchantUser entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll(Iterable<? extends MerchantUser> entities) {}
        @Override public void deleteAll() { db.clear(); }
        @Override public List<MerchantUser> findAllById(Iterable<UUID> ids) { return Collections.emptyList(); }
        @Override public <S extends MerchantUser> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void flush() {}
        @Override public <S extends MerchantUser> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends MerchantUser> List<S> saveAllAndFlush(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void deleteAllInBatch(Iterable<MerchantUser> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public MerchantUser getOne(UUID id) { return db.get(id); }
        @Override public MerchantUser getById(UUID id) { return db.get(id); }
        @Override public MerchantUser getReferenceById(UUID id) { return db.get(id); }
        @Override public <S extends MerchantUser> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends MerchantUser> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends MerchantUser> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends MerchantUser> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends MerchantUser> long count(Example<S> example) { return 0; }
        @Override public <S extends MerchantUser> boolean exists(Example<S> example) { return false; }
        @Override public <S extends MerchantUser, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<MerchantUser> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<MerchantUser> findAll(Pageable pageable) { return Page.empty(); }
    }
}

