package com.roadeye.auth.service;

import com.roadeye.auth.dto.LoginRequest;
import com.roadeye.auth.dto.LoginResponse;
import com.roadeye.auth.dto.RefreshTokenRequest;
import com.roadeye.auth.dto.RegisterRequest;
import com.roadeye.auth.dto.UserResponse;
import com.roadeye.auth.entity.RefreshToken;
import com.roadeye.auth.entity.Role;
import com.roadeye.auth.entity.User;
import com.roadeye.auth.repository.RefreshTokenRepository;
import com.roadeye.auth.repository.UserRepository;
import com.roadeye.config.JwtTokenProvider;
import com.roadeye.shared.BadRequestException;
import com.roadeye.shared.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private static final long REFRESH_TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        List<String> roles = List.of(user.getRole().name());
        String accessToken = jwtTokenProvider.generateToken(username, roles);
        String refreshToken = createRefreshToken(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs)
                .build();
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRole(Role.SURVEILLANCE_OPERATOR);
        user.setActive(true);

        user = userRepository.save(user);

        return mapToUserResponse(user);
    }

    public LoginResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            throw new BadRequestException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Refresh token has expired");
        }

        User user = storedToken.getUser();

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        List<String> roles = List.of(user.getRole().name());
        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), roles);
        String newRefreshToken = createRefreshToken(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs)
                .build();
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return mapToUserResponse(user);
    }

    private String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plusMillis(REFRESH_TOKEN_EXPIRY_MS));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
