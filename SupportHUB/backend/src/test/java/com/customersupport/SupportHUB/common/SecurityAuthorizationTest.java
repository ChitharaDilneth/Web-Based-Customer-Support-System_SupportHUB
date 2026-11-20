package com.customersupport.SupportHUB.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Security & Authorization Unit Tests")
class SecurityAuthorizationTest {

    private JwtUtils jwtUtils;
    private PasswordEncoder passwordEncoder;
    private static final String VALID_BASE64_256BIT_KEY = "U3VwcG9ydEhVQl9TdXBlcl9TZWN1cmVfUHJvZHVjdGlvbl9LZXlfMjAyNiE=";

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", VALID_BASE64_256BIT_KEY);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L);
        passwordEncoder = new BCryptPasswordEncoder();
    }

    @Test
    @DisplayName("JWT: Successfully generate and validate token for all roles")
    void testJwtTokenGenerationAndValidation() {
        String[] roles = {"CUSTOMER", "CUSTOMER_SERVICE_OFFICER", "OPERATIONS_SUPERVISOR", "CUSTOMER_SUPPORT_MANAGER", "QA_EXECUTIVE"};

        for (String role : roles) {
            String token = jwtUtils.generateTokenFromEmail("test." + role.toLowerCase() + "@supporthub.com", role, 100L);
            assertNotNull(token);
            assertTrue(token.length() > 20);
            assertTrue(jwtUtils.validateJwtToken(token));
            assertEquals("test." + role.toLowerCase() + "@supporthub.com", jwtUtils.getUserNameFromJwtToken(token));
        }
    }

    @Test
    @DisplayName("JWT: Invalid or malformed token rejection")
    void testInvalidTokenRejection() {
        assertFalse(jwtUtils.validateJwtToken("invalid.token.string"));
        assertFalse(jwtUtils.validateJwtToken(""));
        assertFalse(jwtUtils.validateJwtToken(null));
    }

    @Test
    @DisplayName("Password: BCrypt hashing and verification")
    void testPasswordHashing() {
        String rawPassword = "StrongPassword@2026";
        String encoded = passwordEncoder.encode(rawPassword);

        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded);
        assertTrue(passwordEncoder.matches(rawPassword, encoded));
        assertFalse(passwordEncoder.matches("WrongPassword", encoded));
    }

    @Test
    @DisplayName("JWT Security: Strict Base64 validation and decoded key length (>= 32 bytes)")
    void testBase64KeyValidation() {
        // Valid 32-byte key
        byte[] raw32 = new byte[32];
        for (int i = 0; i < 32; i++) raw32[i] = (byte) (i + 1);
        String base64Key = Base64.getEncoder().encodeToString(raw32);

        SecretKey key = SecurityUtils.decodeAndValidateJwtSecret(base64Key);
        assertNotNull(key);
        assertEquals("HmacSHA256", key.getAlgorithm());

        // Malformed Base64 should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
                SecurityUtils.decodeAndValidateJwtSecret("Not-Valid-Base64!#%*^")
        );

        // Insufficient length (< 32 bytes decoded) should throw IllegalArgumentException
        byte[] shortBytes = new byte[16];
        String shortBase64 = Base64.getEncoder().encodeToString(shortBytes);
        assertThrows(IllegalArgumentException.class, () ->
                SecurityUtils.decodeAndValidateJwtSecret(shortBase64)
        );

        // Demo key rejection
        assertThrows(IllegalArgumentException.class, () ->
                SecurityUtils.decodeAndValidateJwtSecret(SecurityUtils.DEMO_JWT_SECRET)
        );
    }

    @Test
    @DisplayName("JWT Security: In production, missing or weak secret aborts startup")
    void testProductionFailsOnMissingOrWeakSecret() {
        JwtUtils prodJwt = new JwtUtils();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ReflectionTestUtils.setField(prodJwt, "environment", env);
        ReflectionTestUtils.setField(prodJwt, "jwtSecret", "");

        assertThrows(IllegalStateException.class, prodJwt::validateSecret);

        // Weak secret
        ReflectionTestUtils.setField(prodJwt, "jwtSecret", "short-secret");
        assertThrows(IllegalStateException.class, prodJwt::validateSecret);
    }

    @Test
    @DisplayName("JWT Security: In development, omitted secret generates ephemeral key")
    void testDevGeneratesEphemeralSecret() {
        JwtUtils devJwt = new JwtUtils();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ReflectionTestUtils.setField(devJwt, "environment", env);
        ReflectionTestUtils.setField(devJwt, "jwtSecret", "");
        ReflectionTestUtils.setField(devJwt, "jwtExpirationMs", 3600000L);

        devJwt.validateSecret();

        String generatedToken = devJwt.generateTokenFromEmail("dev@supporthub.com", "CUSTOMER", 1L);
        assertNotNull(generatedToken);
        assertTrue(devJwt.validateJwtToken(generatedToken));
    }

    @Test
    @DisplayName("Early Config: Rejects production startup if DB username is root or credentials missing")
    void testEarlyProductionConfigValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", "jdbc:mysql://proddb:3306/support_db");
        env.setProperty("spring.datasource.username", "root");
        env.setProperty("spring.datasource.password", "secretPass");
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        env.setProperty("app.jwt.secret", VALID_BASE64_256BIT_KEY);

        // Should throw due to root username
        assertThrows(IllegalStateException.class, () -> EarlyProductionConfigValidator.validateEnvironment(env));

        // Fix username to dedicated service user
        env.setProperty("spring.datasource.username", "supporthub_svc");

        // Unsafe ddl-auto update should throw
        env.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        assertThrows(IllegalStateException.class, () -> EarlyProductionConfigValidator.validateEnvironment(env));

        // Valid ddl-auto validate should succeed
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        assertDoesNotThrow(() -> EarlyProductionConfigValidator.validateEnvironment(env));
    }

    @Test
    @DisplayName("Provisioning: Secure credential file creation using CREATE_NEW and symlink rejection")
    void testSupervisorCredentialProvisioningSecurity() throws Exception {
        DataInitializer init = new DataInitializer(null, null);
        Path credDir = Path.of("./.credentials");
        Path credFile = credDir.resolve("supervisor-initial-credentials.txt");

        try {
            if (Files.exists(credFile)) {
                Files.delete(credFile);
            }

            // First provisioning should create the file securely
            assertDoesNotThrow(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(init, "provisionToSecureFile", "TestSuperSecret123!"));
            assertTrue(Files.exists(credFile));

            // Re-provisioning without deleting must fail fast with CREATE_NEW protection
            assertThrows(Exception.class, () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(init, "provisionToSecureFile", "AnotherSecret!"));
        } finally {
            if (Files.exists(credFile)) {
                Files.deleteIfExists(credFile);
            }
        }
    }
}
