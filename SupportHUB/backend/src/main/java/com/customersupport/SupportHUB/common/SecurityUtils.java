package com.customersupport.SupportHUB.common;

import org.springframework.core.env.Environment;

import java.util.Set;

public final class SecurityUtils {

    public static final String DEMO_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final Set<String> PROD_PROFILES = Set.of("prod", "production", "live", "release");

    private SecurityUtils() {
    }

    /**
     * Determines whether the application is running in a production or strictly secured environment.
     * Correctly handles multiple active profiles (e.g., "prod,metrics", "dev,prod"), Environment beans,
     * and STRICT_SECURITY / SPRING_PROFILES_ACTIVE environment variables.
     */
    public static boolean isProductionEnvironment(Environment env, String activeProfilesProperty) {
        if (Boolean.parseBoolean(System.getenv("STRICT_SECURITY"))) {
            return true;
        }

        if (env != null) {
            String[] activeProfiles = env.getActiveProfiles();
            if (activeProfiles != null && activeProfiles.length > 0) {
                for (String p : activeProfiles) {
                    if (isProdProfileName(p)) {
                        return true;
                    }
                }
            }
        }

        if (activeProfilesProperty != null && !activeProfilesProperty.isBlank()) {
            String[] tokens = activeProfilesProperty.split("[,;\\s]+");
            for (String t : tokens) {
                if (isProdProfileName(t)) {
                    return true;
                }
            }
        }

        String envSpringProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (envSpringProfiles != null && !envSpringProfiles.isBlank()) {
            String[] tokens = envSpringProfiles.split("[,;\\s]+");
            for (String t : tokens) {
                if (isProdProfileName(t)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isProdProfileName(String name) {
        if (name == null) {
            return false;
        }
        return PROD_PROFILES.contains(name.trim().toLowerCase());
    }

    /**
     * Checks if a JWT secret is missing, too short for HMAC-SHA256 (< 32 bytes), or is the known public demo secret.
     */
    public static boolean isWeakOrDemoSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        String trimmed = secret.trim();
        if (DEMO_JWT_SECRET.equalsIgnoreCase(trimmed)) {
            return true;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(trimmed);
            return decoded.length < 32;
        } catch (IllegalArgumentException e) {
            // If it's not valid Base64, check raw string byte length as fallback
            return trimmed.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32;
        }
    }

    /**
     * Decodes and strictly validates a Base64-encoded JWT secret key for HMAC-SHA256.
     * Enforces:
     * 1. Non-null, non-blank input.
     * 2. Valid Base64 encoding (rejects malformed Base64).
     * 3. At least 32 decoded bytes (256 bits) for cryptographic security.
     * 4. Rejection of known demo/default secrets.
     *
     * @param secret Base64-encoded secret key
     * @return Validated javax.crypto.SecretKey
     * @throws IllegalArgumentException if secret is invalid, malformed, or insecure
     */
    public static javax.crypto.SecretKey decodeAndValidateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret cannot be null or empty.");
        }
        String trimmed = secret.trim();
        if (DEMO_JWT_SECRET.equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Known public demo secret is strictly prohibited.");
        }

        byte[] keyBytes;
        try {
            keyBytes = java.util.Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT secret is not valid Base64: " + e.getMessage(), e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("Decoded JWT secret must be at least 32 bytes (256 bits) for HS256. Found: " + keyBytes.length + " bytes.");
        }

        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Derives a safe, sanitized MIME type server-side from a validated file extension.
     * Prevents reliance on client-controlled Content-Type headers.
     */
    public static String deriveSafeMimeType(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }
        return switch (extension.toLowerCase()) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".zip" -> "application/zip";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".txt" -> "text/plain";
            case ".csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }
}
