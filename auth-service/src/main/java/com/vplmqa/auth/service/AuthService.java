package com.vplmqa.auth.service;

import com.vplmqa.auth.dto.AuthResponse;
import com.vplmqa.auth.dto.LoginRequest;
import com.vplmqa.auth.dto.RefreshRequest;
import com.vplmqa.auth.dto.RegisterRequest;
import com.vplmqa.auth.dto.ResetPasswordConfirmRequest;
import com.vplmqa.auth.dto.ResetPasswordRequest;
import com.vplmqa.auth.entity.RefreshToken;
import com.vplmqa.auth.entity.Role;
import com.vplmqa.auth.entity.User;
import com.vplmqa.auth.enumtype.RoleEnum;
import com.vplmqa.auth.repository.RefreshTokenRepository;
import com.vplmqa.auth.repository.RoleRepository;
import com.vplmqa.auth.repository.UserRepository;
import com.vplmqa.common.ApiException;
import com.vplmqa.common.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Service containing authentication business logic.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuditService auditService;
    private final PasswordResetService passwordResetService;

    /**
     * Creates the auth service.
     *
     * @param userRepository user repository
     * @param roleRepository role repository
     * @param refreshTokenRepository refresh token repository
     * @param passwordEncoder password encoder
     * @param jwtService JWT service
     * @param tokenBlacklistService blacklist service
     * @param auditService audit service
     * @param passwordResetService password reset service
     */
    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenBlacklistService tokenBlacklistService,
                       AuditService auditService,
                       PasswordResetService passwordResetService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.auditService = auditService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * Registers a new user account.
     *
     * @param request registration request
     * @param httpRequest HTTP request for audit
     * @return auth response
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        Role role = resolveDefaultRole();
        User user = buildUser(request, role);
        userRepository.save(user);
        AuthResponse response = issueTokens(user);
        auditService.log(user.getId(), "REGISTER", httpRequest, true);
        logger.info("Registered user {}", user.getEmail());
        return response;
    }

    /**
     * Authenticates a user and returns tokens.
     *
     * @param request login request
     * @param httpRequest HTTP request for audit
     * @return auth response
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user.getId(), "LOGIN", httpRequest, false);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        AuthResponse response = issueTokens(user);
        auditService.log(user.getId(), "LOGIN", httpRequest, true);
        return response;
    }

    /**
     * Refreshes tokens using a valid refresh token.
     *
     * @param request refresh request
     * @param httpRequest HTTP request for audit
     * @return auth response
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(request.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        AuthResponse response = issueTokens(token.getUser());
        auditService.log(token.getUser().getId(), "REFRESH", httpRequest, true);
        return response;
    }

    /**
     * Logs out a user by revoking tokens.
     *
     * @param request refresh request
     * @param httpRequest HTTP request for audit
     */
    @Transactional
    public void logout(RefreshRequest request, HttpServletRequest httpRequest) {
        RefreshToken token = refreshTokenRepository.findByTokenAndRevokedFalse(request.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        blacklistAccessToken(httpRequest);
        auditService.log(token.getUser().getId(), "LOGOUT", httpRequest, true);
    }

    /**
     * Initiates a password reset.
     *
     * @param request reset request
     * @param httpRequest HTTP request for audit
     */
    public void resetPasswordRequest(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = passwordResetService.createToken(user.getEmail());
            logger.info("Issued reset token {} for {}", token, user.getEmail());
            auditService.log(user.getId(), "RESET_REQUEST", httpRequest, true);
        });
    }

    /**
     * Confirms a password reset.
     *
     * @param request confirm request
     * @param httpRequest HTTP request for audit
     */
    @Transactional
    public void resetPasswordConfirm(ResetPasswordConfirmRequest request, HttpServletRequest httpRequest) {
        String email = passwordResetService.resolveEmail(request.token());
        if (email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid reset token");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        passwordResetService.deleteToken(request.token());
        auditService.log(user.getId(), "RESET_CONFIRM", httpRequest, true);
    }

    private Role resolveDefaultRole() {
        return roleRepository.findByName(RoleEnum.TESTER)
                .orElseThrow(() -> new EntityNotFoundException("Default role missing"));
    }

    private User buildUser(RegisterRequest request, Role role) {
        User user = new User();
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        RefreshToken entity = new RefreshToken();
        entity.setToken(refreshToken);
        entity.setUser(user);
        entity.setExpiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenTtlSeconds()));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenTtlSeconds(), user.isRequiresPasswordChange());
    }

    /**
     * Changes the password of an authenticated user.
     *
     * @param request change password request
     * @param userId authenticated user ID
     * @param httpRequest HTTP request for audit
     */
    @Transactional
    public void changePassword(com.vplmqa.auth.dto.ChangePasswordRequest request, String userId, HttpServletRequest httpRequest) {
        User user = userRepository.findById(java.util.UUID.fromString(userId))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid current password");
        }
        
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setRequiresPasswordChange(false);
        userRepository.save(user);
        
        auditService.log(user.getId(), "CHANGE_PASSWORD", httpRequest, true);
    }

    private void blacklistAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring("Bearer ".length());
        Duration ttl = Duration.ofSeconds(jwtService.getAccessTokenTtlSeconds());
        tokenBlacklistService.blacklist(token, ttl);
    }
}
