package com.customersupport.SupportHUB.auth;

import com.customersupport.SupportHUB.agent.SupportAgent;
import com.customersupport.SupportHUB.agent.SupportAgentRepository;
import com.customersupport.SupportHUB.common.BadRequestException;
import com.customersupport.SupportHUB.common.DuplicateResourceException;
import com.customersupport.SupportHUB.common.JwtUtils;
import com.customersupport.SupportHUB.common.PasswordResetToken;
import com.customersupport.SupportHUB.common.PasswordResetTokenRepository;
import com.customersupport.SupportHUB.common.ResourceNotFoundException;
import com.customersupport.SupportHUB.common.Role;
import com.customersupport.SupportHUB.common.User;
import com.customersupport.SupportHUB.common.UserPrincipal;
import com.customersupport.SupportHUB.common.UserRepository;
import com.customersupport.SupportHUB.customer.Customer;
import com.customersupport.SupportHUB.customer.CustomerRepository;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SupportAgentRepository agentRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    public AuthServiceImpl(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            SupportAgentRepository agentRepository,
            PasswordResetTokenRepository resetTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.agentRepository = agentRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
    }

    @Override
    @Transactional
    public void registerCustomer(CustomerRegistrationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER); // Explicitly assigned server-side
        user.setActive(true);
        user = userRepository.save(user);

        Customer customer = new Customer(
                user,
                request.getFullName().trim(),
                request.getPhone() != null ? request.getPhone().trim() : null,
                request.getAddress() != null ? request.getAddress().trim() : null
        );
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public JwtResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email is already registered: " + normalizedEmail);
        }

        Role role = request.getRole() != null ? request.getRole() : Role.CUSTOMER;

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setActive(true);
        user = userRepository.save(user);

        String fullName = request.getFullName();

        if (role == Role.CUSTOMER) {
            Customer customer = new Customer(user, request.getFullName(), request.getPhone(), request.getAddress());
            customerRepository.save(customer);
        } else if (role == Role.CUSTOMER_SERVICE_OFFICER || role == Role.OPERATIONS_SUPERVISOR || role == Role.QA_EXECUTIVE || role == Role.CUSTOMER_SUPPORT_MANAGER) {
            String employeeCode = (role == Role.CUSTOMER_SERVICE_OFFICER ? "AGT-" : "EMP-") + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            SupportAgent agent = new SupportAgent(user, request.getFullName(), request.getPhone(), employeeCode);
            agentRepository.save(agent);
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        return new JwtResponse(jwt, user.getId(), user.getEmail(), user.getRole(), fullName);
    }

    @Override
    @Transactional
    public JwtResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User userCheck = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (userCheck != null && !userCheck.isActive()) {
            throw new BadRequestException("Your account has been deactivated due to prolonged inactivity (over 6 months). Please contact Customer Support to reactivate your account.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Update Last Login Activity Timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String fullName = user.getEmail();
        if (user.getRole() == Role.CUSTOMER) {
            fullName = customerRepository.findByUserId(user.getId())
                    .map(Customer::getFullName).orElse(user.getEmail());
        } else if (user.getRole() == Role.CUSTOMER_SERVICE_OFFICER) {
            fullName = agentRepository.findByUserId(user.getId())
                    .map(SupportAgent::getFullName).orElse(user.getEmail());
        }

        return new JwtResponse(jwt, user.getId(), user.getEmail(), user.getRole(), fullName);
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email: " + request.getEmail()));

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user, LocalDateTime.now().plusHours(1));
        resetTokenRepository.save(resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token is expired or has already been used");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsed(true);
        resetTokenRepository.save(token);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());

        if (user.getRole() == Role.CUSTOMER) {
            customerRepository.findByUserId(user.getId()).ifPresent(c -> {
                dto.setFullName(c.getFullName());
                dto.setPhone(c.getPhone());
                dto.setEmployeeCode(c.getCustomerCode() != null ? c.getCustomerCode() : (user.getEmployeeCode() != null ? user.getEmployeeCode() : "CUS-" + user.getId()));
            });
        } else if (user.getRole() == Role.CUSTOMER_SERVICE_OFFICER) {
            agentRepository.findByUserId(user.getId()).ifPresent(a -> {
                dto.setFullName(a.getFullName());
                dto.setPhone(a.getPhone());
                dto.setEmployeeCode(a.getEmployeeCode());
                dto.setDepartment(a.getDepartment());
                dto.setDesignation(a.getDesignation());
            });
        }

        if (dto.getFullName() == null || dto.getFullName().isBlank()) {
            dto.setFullName(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Operations Supervisor");
        }
        if (dto.getPhone() == null || dto.getPhone().isBlank()) {
            dto.setPhone(user.getPhone() != null && !user.getPhone().isBlank() ? user.getPhone() : "+94 77 123 4567");
        }
        if (dto.getEmployeeCode() == null || dto.getEmployeeCode().isBlank()) {
            dto.setEmployeeCode(user.getEmployeeCode() != null && !user.getEmployeeCode().isBlank() ? user.getEmployeeCode() : "SUP-" + user.getId());
        }
        if (dto.getDepartment() == null || dto.getDepartment().isBlank()) {
            dto.setDepartment(user.getDepartment() != null && !user.getDepartment().isBlank() ? user.getDepartment() : "Support Operations");
        }
        if (dto.getDesignation() == null || dto.getDesignation().isBlank()) {
            dto.setDesignation(user.getDesignation() != null && !user.getDesignation().isBlank() ? user.getDesignation() : "Lead Operations Supervisor");
        }

        return dto;
    }

    @Override
    @Transactional
    public UserProfileDto updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
            user.setDepartment(request.getDepartment().trim());
        }
        if (request.getDesignation() != null && !request.getDesignation().isBlank()) {
            user.setDesignation(request.getDesignation().trim());
        }

        user = userRepository.save(user);

        if (user.getRole() == Role.CUSTOMER) {
            customerRepository.findByUserId(user.getId()).ifPresent(c -> {
                if (request.getFullName() != null && !request.getFullName().isBlank()) c.setFullName(request.getFullName().trim());
                if (request.getPhone() != null) c.setPhone(request.getPhone().trim());
                customerRepository.save(c);
            });
        } else if (user.getRole() == Role.CUSTOMER_SERVICE_OFFICER) {
            agentRepository.findByUserId(user.getId()).ifPresent(a -> {
                if (request.getFullName() != null && !request.getFullName().isBlank()) a.setFullName(request.getFullName().trim());
                if (request.getPhone() != null) a.setPhone(request.getPhone().trim());
                if (request.getDepartment() != null && !request.getDepartment().isBlank()) a.setDepartment(request.getDepartment().trim());
                if (request.getDesignation() != null && !request.getDesignation().isBlank()) a.setDesignation(request.getDesignation().trim());
                agentRepository.save(a);
            });
        }

        return getCurrentUserProfile(email);
    }
}
