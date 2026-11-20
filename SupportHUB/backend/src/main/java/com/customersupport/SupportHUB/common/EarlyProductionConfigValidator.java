package com.customersupport.SupportHUB.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Early Production Configuration Validator.
 * Executes prior to Spring Data JPA, DataSource, and EntityManagerFactory initialization.
 * Enforces production security invariants:
 * 1. DB_URL is explicitly set.
 * 2. DB_USERNAME is set and non-root (least-privilege).
 * 3. DB_PASSWORD is set and non-empty.
 * 4. Hibernate DDL-auto is 'validate' or 'none' to prevent automatic schema modification.
 * 5. JWT_SECRET is configured with at least 32 decoded bytes (256 bits).
 */
public class EarlyProductionConfigValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(EarlyProductionConfigValidator.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        validateEnvironment(env);
    }

    public static void validateEnvironment(ConfigurableEnvironment env) {
        if (env == null) {
            return;
        }

        String activeProfiles = env.getProperty("spring.profiles.active");
        boolean isProduction = SecurityUtils.isProductionEnvironment(env, activeProfiles);

        if (!isProduction) {
            return;
        }

        logger.info(">>> [EARLY VALIDATION] Verifying production database and security configuration prior to JPA/Hibernate initialization...");

        // 1. Require DB_URL
        String dbUrl = env.getProperty("spring.datasource.url");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getenv("DB_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Production requires DB_URL / spring.datasource.url to be configured.");
        }

        // 2. Require DB_USERNAME (and forbid root)
        String dbUsername = env.getProperty("spring.datasource.username");
        if (dbUsername == null || dbUsername.isBlank()) {
            dbUsername = System.getenv("DB_USERNAME");
        }
        if (dbUsername == null || dbUsername.isBlank()) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Production requires DB_USERNAME / spring.datasource.username to be configured.");
        }
        if ("root".equalsIgnoreCase(dbUsername.trim())) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: Production database cannot run under 'root' superuser. Use a least-privilege dedicated service user.");
        }

        // 3. Require DB_PASSWORD
        String dbPassword = env.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            dbPassword = System.getenv("DB_PASSWORD");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Production requires DB_PASSWORD / spring.datasource.password to be configured.");
        }

        // 4. Require Hibernate ddl-auto=validate or none
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto == null || ddlAuto.isBlank()) {
            ddlAuto = System.getenv("HIBERNATE_DDL_AUTO");
        }
        if (ddlAuto == null || ddlAuto.isBlank()) {
            ddlAuto = "validate";
        }
        if (!"validate".equalsIgnoreCase(ddlAuto.trim()) && !"none".equalsIgnoreCase(ddlAuto.trim())) {
            throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Production environments must use 'validate' or 'none' for spring.jpa.hibernate.ddl-auto to prevent accidental schema corruption. Found unsafe setting: " + ddlAuto);
        }

        // 5. Require JWT_SECRET
        String jwtSecret = env.getProperty("app.jwt.secret");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = System.getenv("JWT_SECRET");
        }
        if (jwtSecret == null || jwtSecret.isBlank() || SecurityUtils.isWeakOrDemoSecret(jwtSecret)) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: Production requires a secure, Base64-encoded JWT_SECRET of at least 32 bytes (256 bits). Default or weak demo secrets are strictly forbidden.");
        }

        logger.info(">>> [EARLY VALIDATION SUCCESS] Production database and security configuration verified. Proceeding with Spring Data JPA and Hibernate startup.");
    }
}
