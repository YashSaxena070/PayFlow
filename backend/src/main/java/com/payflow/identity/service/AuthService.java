package com.payflow.identity.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.identity.domain.MerchantUser;
import com.payflow.identity.domain.User;
import com.payflow.identity.domain.UserRole;
import com.payflow.identity.repository.MerchantUserRepository;
import com.payflow.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserRepository userRepository,
                       MerchantUserRepository merchantUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public record LoginResult(
            String token,
            UUID userId,
            String email,
            UUID merchantId,
            String merchantName,
            String role
    ) {}

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password, UUID targetMerchantId) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new PayflowException(ErrorCode.FORBIDDEN, "User account is suspended");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        List<MerchantUser> memberships = merchantUserRepository.findAllByUserIdWithMerchant(user.getId());
        if (memberships.isEmpty()) {
            throw new PayflowException(ErrorCode.FORBIDDEN, "User has no merchant access configured");
        }

        MerchantUser activeMembership;
        if (targetMerchantId != null) {
            activeMembership = memberships.stream()
                    .filter(m -> m.getMerchant().getId().equals(targetMerchantId))
                    .findFirst()
                    .orElseThrow(() -> new PayflowException(ErrorCode.FORBIDDEN, "Access denied to requested merchant"));
        } else {
            activeMembership = memberships.get(0);
        }

        UUID merchantId = activeMembership.getMerchant().getId();
        String merchantName = activeMembership.getMerchant().getName();
        String roleStr = activeMembership.getRole().name();

        String token = jwtTokenService.generateToken(user.getId(), merchantId, user.getEmail(), roleStr);

        return new LoginResult(
                token,
                user.getId(),
                user.getEmail(),
                merchantId,
                merchantName,
                roleStr
        );
    }
}

