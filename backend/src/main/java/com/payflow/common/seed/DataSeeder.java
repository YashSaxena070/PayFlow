package com.payflow.common.seed;

import com.payflow.identity.domain.ApiCredential;
import com.payflow.identity.domain.MerchantUser;
import com.payflow.identity.domain.User;
import com.payflow.identity.domain.UserRole;
import com.payflow.identity.repository.ApiCredentialRepository;
import com.payflow.identity.repository.MerchantUserRepository;
import com.payflow.identity.repository.UserRepository;
import com.payflow.identity.service.ApiKeyService;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.provider.domain.MerchantProviderConfig;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.repository.MerchantProviderConfigRepository;
import com.payflow.provider.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile({"local", "test", "docker"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final UUID MERCHANT_ACME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID MERCHANT_BETA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final String ACME_ADMIN_EMAIL = "admin@acme.com";
    public static final String ACME_OPERATOR_EMAIL = "operator@acme.com";
    public static final String ACME_ANALYST_EMAIL = "analyst@acme.com";
    public static final String BETA_ADMIN_EMAIL = "admin@beta.com";
    public static final String DEFAULT_PASSWORD = "password123";

    public static final String ACME_API_KEY = "pf_live_acme_secret_key_1234567890abcdef";
    public static final String BETA_API_KEY = "pf_live_beta_secret_key_1234567890abcdef";

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final ApiCredentialRepository apiCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProviderRepository providerRepository;
    private final MerchantProviderConfigRepository merchantProviderConfigRepository;

    public DataSeeder(MerchantRepository merchantRepository,
                      UserRepository userRepository,
                      MerchantUserRepository merchantUserRepository,
                      ApiCredentialRepository apiCredentialRepository,
                      PasswordEncoder passwordEncoder,
                      ProviderRepository providerRepository,
                      MerchantProviderConfigRepository merchantProviderConfigRepository) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.apiCredentialRepository = apiCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.providerRepository = providerRepository;
        this.merchantProviderConfigRepository = merchantProviderConfigRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (merchantRepository.count() == 0) {
            log.info("Seeding initial demo merchants, users, and API keys...");

            // 1. Seed Merchants
            Merchant acme = new Merchant(MERCHANT_ACME_ID, "Acme Global Corp", "ACTIVE");
            Merchant beta = new Merchant(MERCHANT_BETA_ID, "Beta Retail Ltd", "ACTIVE");
            merchantRepository.save(acme);
            merchantRepository.save(beta);

            // 2. Seed Users
            String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

            User acmeAdmin = new User(UUID.randomUUID(), ACME_ADMIN_EMAIL, encodedPassword, "ACTIVE");
            User acmeOperator = new User(UUID.randomUUID(), ACME_OPERATOR_EMAIL, encodedPassword, "ACTIVE");
            User acmeAnalyst = new User(UUID.randomUUID(), ACME_ANALYST_EMAIL, encodedPassword, "ACTIVE");
            User betaAdmin = new User(UUID.randomUUID(), BETA_ADMIN_EMAIL, encodedPassword, "ACTIVE");

            userRepository.save(acmeAdmin);
            userRepository.save(acmeOperator);
            userRepository.save(acmeAnalyst);
            userRepository.save(betaAdmin);

            // 3. Link Merchant Users
            merchantUserRepository.save(new MerchantUser(UUID.randomUUID(), acme, acmeAdmin, UserRole.MERCHANT_ADMIN));
            merchantUserRepository.save(new MerchantUser(UUID.randomUUID(), acme, acmeOperator, UserRole.MERCHANT_OPERATOR));
            merchantUserRepository.save(new MerchantUser(UUID.randomUUID(), acme, acmeAnalyst, UserRole.READ_ONLY_ANALYST));
            merchantUserRepository.save(new MerchantUser(UUID.randomUUID(), beta, betaAdmin, UserRole.MERCHANT_ADMIN));

            // 4. Seed Known API Keys for automated tests and curl demos
            ApiCredential acmeKey = new ApiCredential(
                    UUID.randomUUID(),
                    acme,
                    "pf_live_acme...",
                    ApiKeyService.hashKey(ACME_API_KEY),
                    "payments:read,payments:write,refunds:write,routing:manage",
                    null
            );
            ApiCredential betaKey = new ApiCredential(
                    UUID.randomUUID(),
                    beta,
                    "pf_live_beta...",
                    ApiKeyService.hashKey(BETA_API_KEY),
                    "payments:read,payments:write,refunds:write",
                    null
            );
            apiCredentialRepository.save(acmeKey);
            apiCredentialRepository.save(betaKey);

            log.info("Seeded demo merchant Acme (admin: {}, pass: {}) and Beta (admin: {})",
                    ACME_ADMIN_EMAIL, DEFAULT_PASSWORD, BETA_ADMIN_EMAIL);
        }

        // 5. Ensure Demo Merchants have enabled provider configurations
        List<Merchant> merchants = merchantRepository.findAll();
        List<Provider> providers = providerRepository.findAll();
        for (Merchant m : merchants) {
            for (Provider p : providers) {
                if (merchantProviderConfigRepository.findByMerchantIdAndProviderId(m.getId(), p.getId()).isEmpty()) {
                    int priority = switch (p.getCode().toLowerCase()) {
                        case "stripe" -> 1;
                        case "razorpay" -> 2;
                        case "cashfree" -> 3;
                        case "payu" -> 4;
                        default -> 5;
                    };
                    merchantProviderConfigRepository.save(new MerchantProviderConfig(
                            UUID.randomUUID(), m, p, true, priority, 100
                    ));
                    log.info("Auto-provisioned default provider config: merchant={}, provider={}", m.getName(), p.getCode());
                }
            }
        }
    }
}

