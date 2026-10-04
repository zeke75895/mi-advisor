package com.miadvisor.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {

        /** BCrypt accepts at most 72 bytes; emoji and accented characters take several bytes each. */
        @JsonIgnore
        @AssertTrue(message = "password is too long; use fewer special characters or emoji")
        public boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserResponse(Long id, String email, Instant createdAt) {}

    public record AuthResponse(String token, String tokenType, long expiresIn, UserResponse user) {}
}
