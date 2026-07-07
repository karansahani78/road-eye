package com.roadeye.auth.controller;

import com.roadeye.auth.dto.RegisterRequest;
import com.roadeye.auth.dto.UserResponse;
import com.roadeye.auth.entity.Role;
import com.roadeye.auth.entity.User;
import com.roadeye.auth.repository.UserRepository;
import com.roadeye.auth.service.AuthService;
import com.roadeye.shared.ApiResponse;
import com.roadeye.shared.PagedResponse;
import com.roadeye.shared.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SYSTEM_ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, AuthService authService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findAll(pageable);

        List<User> users = userPage.getContent();
        if (role != null) {
            users = users.stream().filter(u -> u.getRole() == role).collect(Collectors.toList());
        }
        if (active != null) {
            users = users.stream().filter(u -> u.isActive() == active).collect(Collectors.toList());
        }
        if (search != null && !search.isBlank()) {
            String s = search.toLowerCase();
            users = users.stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(s)
                            || u.getEmail().toLowerCase().contains(s)
                            || (u.getFullName() != null && u.getFullName().toLowerCase().contains(s)))
                    .collect(Collectors.toList());
        }

        PagedResponse<UserResponse> pagedResponse = PagedResponse.<UserResponse>builder()
                .content(users.stream().map(this::mapToUserResponse).collect(Collectors.toList()))
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        UserResponse response = authService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@PathVariable UUID id,
                                                                @Valid @RequestBody RegisterRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(mapToUserResponse(user)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(@PathVariable UUID id,
                                                                      @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(active);
        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(mapToUserResponse(user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(false);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(null, "User deactivated successfully"));
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
