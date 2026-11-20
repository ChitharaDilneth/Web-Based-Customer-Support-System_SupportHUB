package com.customersupport.SupportHUB.common;

import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Autowired(required = false)
    private Environment environment;

    private javax.crypto.SecretKey cachedSecretKey;

    @jakarta.annotation.PostConstruct
    public void validateSecret() {
        boolean isProduction = SecurityUtils.isProductionEnvironment(environment, activeProfile);
        boolean isWeakOrDemoSecret = SecurityUtils.isWeakOrDemoSecret(jwtSecret);

        if (isProduction) {
            if (isWeakOrDemoSecret) {
                throw new IllegalStateException("CRITICAL SECURITY ERROR: SupportHUB cannot start in production without a secure JWT_SECRET environment variable. Default or weak secrets are strictly forbidden.");
            }
            try {
                this.cachedSecretKey = SecurityUtils.decodeAndValidateJwtSecret(jwtSecret);
            } catch (Exception e) {
                throw new IllegalStateException("CRITICAL SECURITY ERROR: Production JWT secret validation failed: " + e.getMessage(), e);
            }
        } else {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                // Generate a secure ephemeral random 256-bit key in-memory for non-production development/testing
                byte[] ephemeralBytes = new byte[32];
                new SecureRandom().nextBytes(ephemeralBytes);
                this.jwtSecret = Base64.getEncoder().encodeToString(ephemeralBytes);
                this.cachedSecretKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(ephemeralBytes);
                logger.warn("SECURITY NOTICE: No app.jwt.secret configured. Generated ephemeral in-memory secret for development/testing session. For production, set JWT_SECRET.");
            } else {
                try {
                    this.cachedSecretKey = SecurityUtils.decodeAndValidateJwtSecret(jwtSecret);
                    if (SecurityUtils.DEMO_JWT_SECRET.equalsIgnoreCase(jwtSecret.trim())) {
                        logger.warn("SECURITY WARNING: Using deprecated demo JWT secret in non-production environment. Configure JWT_SECRET before deploying to production.");
                    }
                } catch (Exception e) {
                    logger.warn("Non-production JWT secret failed strict Base64 validation: {}. Falling back to ephemeral key for safety.", e.getMessage());
                    byte[] ephemeralBytes = new byte[32];
                    new SecureRandom().nextBytes(ephemeralBytes);
                    this.jwtSecret = Base64.getEncoder().encodeToString(ephemeralBytes);
                    this.cachedSecretKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(ephemeralBytes);
                }
            }
        }
    }

    private Key key() {
        if (cachedSecretKey == null) {
            validateSecret();
        }
        return cachedSecretKey;
    }

    public String generateJwtToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .claim("id", userPrincipal.getId())
                .claim("role", userPrincipal.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateTokenFromEmail(String email, String role, Long userId) {
        return Jwts.builder()
                .setSubject(email)
                .claim("id", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
