package com.customersupport.SupportHUB.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${app.supervisor.initial-password:}")
    private String configuredPassword;

    @Autowired(required = false)
    private Environment environment;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        validateProductionConfiguration();

        // Ensure Default OPERATIONS_SUPERVISOR Account exists without resetting credentials on restart
        if (userRepository.findByEmail("supervisor@demo.com").isEmpty()) {
            String initialPassword = resolveAndProvisionInitialPassword();
            User supervisorUser = new User("supervisor@demo.com", passwordEncoder.encode(initialPassword), Role.OPERATIONS_SUPERVISOR);
            supervisorUser.setFullName("Operations Supervisor");
            supervisorUser.setPhone("+94 77 123 4567");
            supervisorUser.setEmployeeCode("SUP-1001");
            supervisorUser.setDepartment("Support Operations");
            supervisorUser.setDesignation("Lead Operations Supervisor");
            userRepository.save(supervisorUser);
            logger.info(">>> SupportHUB Startup: Seeded default Supervisor account (supervisor@demo.com).");
        } else {
            // If already exists, populate metadata if missing, but NEVER overwrite the password hash
            User supervisorUser = userRepository.findByEmail("supervisor@demo.com").get();
            boolean modified = false;
            if (supervisorUser.getFullName() == null || supervisorUser.getFullName().isBlank()) {
                supervisorUser.setFullName("Operations Supervisor");
                supervisorUser.setPhone("+94 77 123 4567");
                supervisorUser.setEmployeeCode("SUP-1001");
                supervisorUser.setDepartment("Support Operations");
                supervisorUser.setDesignation("Lead Operations Supervisor");
                modified = true;
            }
            if (modified) {
                userRepository.save(supervisorUser);
            }
            logger.info(">>> SupportHUB Startup: Verified existing Supervisor account. Password hash preserved.");
        }
    }

    private void validateProductionConfiguration() {
        boolean isProduction = SecurityUtils.isProductionEnvironment(environment, activeProfile);
        if (!isProduction) {
            return;
        }

        if (environment != null) {
            String dbPass = environment.getProperty("spring.datasource.password");
            if (dbPass == null || dbPass.isBlank()) {
                String envDbPass = System.getenv("DB_PASSWORD");
                if (envDbPass == null || envDbPass.isBlank()) {
                    throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Database password (DB_PASSWORD / spring.datasource.password) must be configured in production environments. Hardcoded default passwords have been eliminated.");
                }
            }

            String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
            if (ddlAuto != null && (ddlAuto.equalsIgnoreCase("update") || ddlAuto.equalsIgnoreCase("create") || ddlAuto.equalsIgnoreCase("create-drop"))) {
                throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Production environments must use 'validate' or 'none' for spring.jpa.hibernate.ddl-auto. Found unsafe setting: " + ddlAuto);
            }
        }
    }

    private String resolveAndProvisionInitialPassword() {
        String envPass = System.getenv("SUPERVISOR_INITIAL_PASSWORD");
        if (envPass != null && !envPass.isBlank()) {
            logger.info(">>> Provisioning supervisor account using SUPERVISOR_INITIAL_PASSWORD environment configuration.");
            return envPass.trim();
        }
        if (configuredPassword != null && !configuredPassword.isBlank()) {
            logger.info(">>> Provisioning supervisor account using app.supervisor.initial-password configuration.");
            return configuredPassword.trim();
        }

        boolean isProduction = SecurityUtils.isProductionEnvironment(environment, activeProfile);

        if (isProduction) {
            String generated = generateSecurePassword();
            provisionToSecureFile(generated);
            logger.info(">>> Initial supervisor account seeded (supervisor@demo.com). One-time credential provisioned to secure local credentials store. Plaintext password is NEVER written to logs.");
            return generated;
        }

        logger.info(">>> Seeded default development Supervisor account (supervisor@demo.com). For production environments, configure SUPERVISOR_INITIAL_PASSWORD.");
        return "Supervisor123!";
    }

    public void provisionToSecureFile(String password) {
        Path dir = Paths.get("./.credentials");
        try {
            if (Files.isSymbolicLink(dir)) {
                throw new IllegalStateException("Credentials directory cannot be a symbolic link.");
            }
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            if (Files.isSymbolicLink(dir)) {
                throw new IllegalStateException("Credentials directory resolved to a symbolic link.");
            }

            Path credFile = dir.resolve("supervisor-initial-credentials.txt");
            if (Files.isSymbolicLink(credFile)) {
                throw new IllegalStateException("Credentials file path cannot be a symbolic link.");
            }
            if (Files.exists(credFile)) {
                throw new IllegalStateException("Credentials file already exists: " + credFile + ". Existing credential files are never overwritten.");
            }

            String content = "Account: supervisor@demo.com\nInitial-Password: " + password + "\nGenerated-At: " + java.time.LocalDateTime.now() + "\nNotice: Delete this file after initial login and password change.\n";

            // Use CREATE_NEW to guarantee atomic creation without overwriting
            Files.writeString(credFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            boolean permissionsVerified = false;
            try {
                Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(credFile, ownerOnly);
                Set<PosixFilePermission> current = Files.getPosixFilePermissions(credFile);
                if (current.equals(ownerOnly)) {
                    permissionsVerified = true;
                }
            } catch (UnsupportedOperationException e) {
                // Non-POSIX / Windows: Apply owner-only ACLs or File access restrictions and verify
                File f = credFile.toFile();
                f.setReadable(false, false);
                boolean r1 = f.setReadable(true, true);
                f.setWritable(false, false);
                boolean w1 = f.setWritable(true, true);
                f.setExecutable(false, false);

                try {
                    AclFileAttributeView aclView = Files.getFileAttributeView(credFile, AclFileAttributeView.class);
                    if (aclView != null) {
                        UserPrincipal owner = Files.getOwner(credFile);
                        AclEntry entry = AclEntry.newBuilder()
                                .setType(AclEntryType.ALLOW)
                                .setPrincipal(owner)
                                .setPermissions(
                                        AclEntryPermission.READ_DATA,
                                        AclEntryPermission.WRITE_DATA,
                                        AclEntryPermission.APPEND_DATA,
                                        AclEntryPermission.READ_ATTRIBUTES,
                                        AclEntryPermission.WRITE_ATTRIBUTES,
                                        AclEntryPermission.READ_ACL,
                                        AclEntryPermission.WRITE_ACL,
                                        AclEntryPermission.DELETE
                                )
                                .build();
                        aclView.setAcl(List.of(entry));
                        permissionsVerified = true;
                    } else {
                        permissionsVerified = r1 && w1;
                    }
                } catch (Exception ex) {
                    permissionsVerified = r1 && w1;
                }
            }

            if (!permissionsVerified || !credFile.toFile().canRead() || !credFile.toFile().canWrite()) {
                throw new IllegalStateException("Verification of secure file permissions failed on: " + credFile);
            }
        } catch (Exception e) {
            logger.error("CRITICAL: Failed to safely provision supervisor credentials file: {}", e.getMessage());
            throw new IllegalStateException("CRITICAL: Failed to safely provision supervisor credentials file (.credentials/supervisor-initial-credentials.txt). Aborting startup to prevent inaccessible administrator account: " + e.getMessage(), e);
        }
    }

    private String generateSecurePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
