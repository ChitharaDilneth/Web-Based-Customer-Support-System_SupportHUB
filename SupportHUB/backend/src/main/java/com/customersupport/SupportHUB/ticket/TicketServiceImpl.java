package com.customersupport.SupportHUB.ticket;

import com.customersupport.SupportHUB.agent.AgentStatus;
import com.customersupport.SupportHUB.agent.SupportAgent;
import com.customersupport.SupportHUB.agent.SupportAgentRepository;
import com.customersupport.SupportHUB.category.TicketCategory;
import com.customersupport.SupportHUB.category.TicketCategoryRepository;
import com.customersupport.SupportHUB.common.AccessDeniedException;
import com.customersupport.SupportHUB.common.Attachment;
import com.customersupport.SupportHUB.common.AttachmentRepository;
import com.customersupport.SupportHUB.common.BadRequestException;
import com.customersupport.SupportHUB.common.FileUploadException;
import com.customersupport.SupportHUB.common.ResourceNotFoundException;
import com.customersupport.SupportHUB.common.Role;
import com.customersupport.SupportHUB.common.SecurityUtils;
import com.customersupport.SupportHUB.common.UnauthorizedException;
import com.customersupport.SupportHUB.common.User;
import com.customersupport.SupportHUB.common.UserRepository;
import com.customersupport.SupportHUB.customer.Customer;
import com.customersupport.SupportHUB.customer.CustomerRepository;
import com.customersupport.SupportHUB.feedback.FeedbackRepository;
import com.customersupport.SupportHUB.notification.Notification;
import com.customersupport.SupportHUB.notification.NotificationFactory;
import com.customersupport.SupportHUB.notification.NotificationService;
import com.customersupport.SupportHUB.notification.NotificationType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketServiceImpl.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".pdf", ".txt", ".csv", ".doc", ".docx", ".xls", ".xlsx", ".zip"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_ZIP_ENTRIES = 500;
    private static final long MAX_SINGLE_FILE_SIZE = 20 * 1024 * 1024L; // 20 MB individual expanded
    private static final long MAX_TOTAL_EXPANDED_SIZE = 50 * 1024 * 1024L; // 50 MB total archive expanded
    private static final int MAX_COMPRESSION_RATIO = 20; // 20:1 max compression threshold

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final SupportAgentRepository agentRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketReplyRepository replyRepository;
    private final TicketHistoryRepository historyRepository;
    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final NotificationService notificationService;
    private final NotificationFactory notificationFactory;
    private final TicketSequenceService ticketSequenceService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Autowired
    public TicketServiceImpl(
            TicketRepository ticketRepository,
            CustomerRepository customerRepository,
            SupportAgentRepository agentRepository,
            TicketCategoryRepository categoryRepository,
            TicketReplyRepository replyRepository,
            TicketHistoryRepository historyRepository,
            AttachmentRepository attachmentRepository,
            UserRepository userRepository,
            FeedbackRepository feedbackRepository,
            NotificationService notificationService,
            NotificationFactory notificationFactory,
            TicketSequenceService ticketSequenceService) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.agentRepository = agentRepository;
        this.categoryRepository = categoryRepository;
        this.replyRepository = replyRepository;
        this.historyRepository = historyRepository;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.notificationService = notificationService;
        this.notificationFactory = notificationFactory;
        this.ticketSequenceService = ticketSequenceService;
    }

    public TicketServiceImpl(
            TicketRepository ticketRepository,
            CustomerRepository customerRepository,
            SupportAgentRepository agentRepository,
            TicketCategoryRepository categoryRepository,
            TicketReplyRepository replyRepository,
            TicketHistoryRepository historyRepository,
            AttachmentRepository attachmentRepository,
            UserRepository userRepository,
            FeedbackRepository feedbackRepository,
            NotificationService notificationService,
            NotificationFactory notificationFactory) {
        this(ticketRepository, customerRepository, agentRepository, categoryRepository,
             replyRepository, historyRepository, attachmentRepository, userRepository,
             feedbackRepository, notificationService, notificationFactory, null);
    }

    @Override
    @Transactional
    public TicketDto createTicket(String customerEmail, CreateTicketRequest request, MultipartFile attachment) {
        Customer customer = customerRepository.findByUserEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found for email: " + customerEmail));

        TicketCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket category not found with id: " + request.getCategoryId()));

        if (!category.isActive()) {
            throw new BadRequestException("Selected category is currently inactive");
        }

        String ticketNumber = generateTicketNumber();

        TicketPriority priority = request.getPriority();
        if (priority == null || priority == TicketPriority.MEDIUM) {
            TicketPriority evaluated = evaluateAutomaticPriority(request.getSubject(), request.getDescription());
            if (evaluated != null && evaluated != TicketPriority.MEDIUM) {
                priority = evaluated;
            } else if (priority == null) {
                priority = TicketPriority.MEDIUM;
            }
        }

        Ticket ticket = new Ticket(
                ticketNumber,
                customer,
                category,
                request.getSubject(),
                request.getDescription(),
                priority
        );

        // E-Commerce Fields
        ticket.setOrderNumber(request.getOrderNumber());
        ticket.setTrackingNumber(request.getTrackingNumber());
        ticket.setRefundStatus(request.getRefundStatus() != null ? request.getRefundStatus() : RefundStatus.NONE);

        // Dynamic Enterprise SLA Calculation
        LocalDateTime slaBase = LocalDateTime.now();
        switch (priority) {
            case URGENT -> ticket.setSlaDueAt(slaBase.plusHours(4));
            case HIGH -> ticket.setSlaDueAt(slaBase.plusHours(12));
            case MEDIUM -> ticket.setSlaDueAt(slaBase.plusHours(24));
            case LOW -> ticket.setSlaDueAt(slaBase.plusHours(48));
        }

        // Automatic Ticket Assignment to AVAILABLE officer in matching category
        List<SupportAgent> availableAgents = agentRepository.findByCategoryId(category.getId()).stream()
                .filter(a -> a.getStatus() == AgentStatus.AVAILABLE && a.getUser().isActive())
                .collect(Collectors.toList());

        if (!availableAgents.isEmpty()) {
            SupportAgent selectedAgent = availableAgents.stream()
                    .min(Comparator.comparingLong(a -> ticketRepository.countActiveTicketsForAgent(a.getId())))
                    .orElse(availableAgents.get(0));

            ticket.setAssignedAgent(selectedAgent);
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        Ticket savedTicket = null;
        int saveAttempts = 0;
        while (savedTicket == null && saveAttempts < 5) {
            try {
                savedTicket = ticketRepository.save(ticket);
            } catch (DataIntegrityViolationException ex) {
                saveAttempts++;
                if (saveAttempts >= 5) {
                    throw ex;
                }
                ticket.setTicketNumber(generateTicketNumber());
            }
        }

        if (savedTicket.getAssignedAgent() != null) {
            recordHistory(savedTicket, customer.getUser(), "TICKET_AUTO_ASSIGNED", "Unassigned", savedTicket.getAssignedAgent().getFullName());
            Notification agentNotif = notificationFactory.createTicketNotification(
                    savedTicket.getAssignedAgent().getUser(), NotificationType.TICKET_ASSIGNED, savedTicket);
            notificationService.sendNotification(agentNotif);
        } else {
            recordHistory(savedTicket, customer.getUser(), "TICKET_CREATED", null, "Status: OPEN, Priority: " + priority.name());
        }

        // File Attachment
        if (attachment != null && !attachment.isEmpty()) {
            saveAttachmentFile(savedTicket, null, attachment);
        }

        // Notification
        Notification notification = notificationFactory.createTicketNotification(
                customer.getUser(), NotificationType.TICKET_CREATED, savedTicket);
        notificationService.sendNotification(notification);

        return mapToDto(savedTicket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDto getTicketById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + id));

        validateTicketAccess(ticket);
        return mapToDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDto getTicketByNumber(String ticketNumber) {
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with number: " + ticketNumber));

        validateTicketAccess(ticket);
        return mapToDto(ticket);
    }

    /**
     * Unified Enterprise Authorization Policy for Support Officers and Customers.
     * Enforces strict caseload and category boundaries across all ticket operations.
     */
    private void validateTicketAccess(Ticket ticket, User user) {
        if (user == null) {
            throw new UnauthorizedException("User authentication required");
        }

        if (user.getRole() == Role.CUSTOMER) {
            Long ownerUserId = ticket.getCustomer() != null && ticket.getCustomer().getUser() != null
                    ? ticket.getCustomer().getUser().getId() : null;
            if (ownerUserId == null || !ownerUserId.equals(user.getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to access ticket " + ticket.getTicketNumber());
            }
            return;
        }

        if (user.getRole() == Role.CUSTOMER_SERVICE_OFFICER) {
            SupportAgent agent = agentRepository.findByUser(user)
                    .orElseThrow(() -> new AccessDeniedException("Active support agent record not found for officer account: " + user.getEmail()));

            boolean isAssigned = ticket.getAssignedAgent() != null && ticket.getAssignedAgent().getId().equals(agent.getId());
            boolean handlesCategory = ticket.getCategory() != null && agent.getAssignedCategories() != null &&
                    agent.getAssignedCategories().stream().anyMatch(c -> c.getId().equals(ticket.getCategory().getId()));

            if (!isAssigned && !handlesCategory) {
                throw new AccessDeniedException("Access denied: You are not assigned to ticket " + ticket.getTicketNumber() +
                        " and do not handle category '" + (ticket.getCategory() != null ? ticket.getCategory().getName() : "Unassigned") + "'.");
            }
        }
    }

    private void validateTicketAccess(Ticket ticket) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new UnauthorizedException("Authenticated user record not found: " + auth.getName()));
            validateTicketAccess(ticket, user);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketDto> getCustomerTickets(String customerEmail, Pageable pageable) {
        Customer customer = customerRepository.findByUserEmail(customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found"));
        return ticketRepository.findByCustomerId(customer.getId(), pageable).map(this::mapToSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketDto> getAssignedOfficerTickets(String officerEmail, Pageable pageable) {
        SupportAgent agent = agentRepository.findByUser(userRepository.findByEmail(officerEmail).orElseThrow())
                .orElseThrow(() -> new ResourceNotFoundException("Support agent record not found"));
        return ticketRepository.findByAssignedAgentId(agent.getId(), pageable).map(this::mapToSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketDto> filterTickets(Long customerId, Long agentId, Long categoryId, TicketStatus status, TicketPriority priority, String keyword, Pageable pageable) {
        // Defensive scoping: enforce role-based boundary on ticket listings
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
                User user = userRepository.findByEmail(auth.getName()).orElse(null);
                if (user != null) {
                    Customer customer = customerRepository.findByUser(user).orElse(null);
                    if (customer != null) {
                        customerId = customer.getId();
                    } else {
                        return Page.empty(pageable);
                    }
                }
            } else if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER_SERVICE_OFFICER"))) {
                User user = userRepository.findByEmail(auth.getName())
                        .orElseThrow(() -> new AccessDeniedException("User account not found: " + auth.getName()));
                SupportAgent agent = agentRepository.findByUser(user)
                        .orElseThrow(() -> new AccessDeniedException("Active support agent record not found for officer account: " + auth.getName()));
                agentId = agent.getId();
            }
        }

        return ticketRepository.findAll(TicketSpecification.filterTickets(customerId, agentId, categoryId, status, priority, keyword), pageable)
                .map(this::mapToSummaryDto);
    }

    @Override
    @Transactional
    public TicketDto assignAgent(Long ticketId, Long agentId, String performedByEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        SupportAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Support agent not found with id: " + agentId));

        // Category Restriction Check
        boolean handlesCategory = agent.getAssignedCategories().stream()
                .anyMatch(c -> c.getId().equals(ticket.getCategory().getId()));
        if (!handlesCategory) {
            throw new BadRequestException("Agent '" + agent.getFullName() + "' is not assigned to handle category '" + ticket.getCategory().getName() + "'");
        }

        User performedBy = userRepository.findByEmail(performedByEmail).orElse(null);
        String oldAgent = ticket.getAssignedAgent() != null ? ticket.getAssignedAgent().getFullName() : "Unassigned";

        ticket.setAssignedAgent(agent);
        if (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.ESCALATED) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticket.setEscalated(false);
        }

        Ticket saved = ticketRepository.save(ticket);

        recordHistory(saved, performedBy, "AGENT_ASSIGNED", oldAgent, agent.getFullName());

        // Notifications to Agent and Customer
        Notification agentNotif = notificationFactory.createTicketNotification(
                agent.getUser(), NotificationType.TICKET_ASSIGNED, saved);
        notificationService.sendNotification(agentNotif);

        Notification customerNotif = notificationFactory.createTicketNotification(
                ticket.getCustomer().getUser(), NotificationType.TICKET_ASSIGNED, saved);
        notificationService.sendNotification(customerNotif);

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public TicketDto updateTicketStatus(Long ticketId, UpdateTicketStatusRequest request, String performedByEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User performedBy = userRepository.findByEmail(performedByEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + performedByEmail));

        TicketStatus oldStatus = ticket.getStatus();
        TicketStatus newStatus = request.getStatus();

        // Enforce unified enterprise authorization policy
        validateTicketAccess(ticket, performedBy);

        validateStatusTransition(oldStatus, newStatus, performedBy.getRole());

        ticket.setStatus(newStatus);

        if (newStatus == TicketStatus.ESCALATED) {
            ticket.setEscalated(true);
        } else if (newStatus == TicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if (newStatus == TicketStatus.CLOSED && ticket.getClosedAt() == null) {
            ticket.setClosedAt(LocalDateTime.now());
        }

        Ticket saved = ticketRepository.save(ticket);

        String note = request.getNote() != null ? request.getNote() : "";
        recordHistory(saved, performedBy, "STATUS_CHANGED", oldStatus.name(), newStatus.name() + (note.isEmpty() ? "" : " Note: " + note));

        // When ticket is escalated by officer, notify operations supervisors
        if (newStatus == TicketStatus.ESCALATED) {
            String actorName = performedBy.getEmail();
            SupportAgent optAgent = agentRepository.findByUser(performedBy).orElse(null);
            if (optAgent != null && optAgent.getFullName() != null) {
                actorName = optAgent.getFullName();
            }
            List<User> supervisors = userRepository.findByRole(Role.OPERATIONS_SUPERVISOR);
            for (User sup : supervisors) {
                Notification supNotif = notificationFactory.createCustomNotification(
                        sup,
                        "Escalation Request: " + saved.getTicketNumber(),
                        "Officer " + actorName + " escalated ticket '" + saved.getSubject() + "': " + (note.isEmpty() ? "Supervisor assistance requested." : note),
                        NotificationType.TICKET_STATUS_CHANGED,
                        saved.getId()
                );
                notificationService.sendNotification(supNotif);
            }
        }

        // Notifications
        NotificationType notifType = switch (newStatus) {
            case RESOLVED -> NotificationType.TICKET_RESOLVED;
            case CLOSED -> NotificationType.TICKET_CLOSED;
            default -> NotificationType.TICKET_STATUS_CHANGED;
        };

        Notification customerNotif = notificationFactory.createTicketNotification(
                ticket.getCustomer().getUser(), notifType, saved);
        notificationService.sendNotification(customerNotif);

        // If customer reopened ticket, alert assigned agent
        if (performedBy.getRole() == Role.CUSTOMER && newStatus == TicketStatus.IN_PROGRESS && saved.getAssignedAgent() != null && saved.getAssignedAgent().getUser() != null) {
            Notification agentNotif = notificationFactory.createCustomNotification(
                    saved.getAssignedAgent().getUser(),
                    "Ticket Reopened: " + saved.getTicketNumber(),
                    "Customer requested further assistance on ticket '" + saved.getSubject() + "'.",
                    NotificationType.TICKET_STATUS_CHANGED,
                    saved.getId()
            );
            notificationService.sendNotification(agentNotif);
        }

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public TicketReplyDto addReply(Long ticketId, CreateReplyRequest request, String userEmail, MultipartFile attachment) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        if (ticket.getStatus() == TicketStatus.CLOSED || ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new BadRequestException("Cannot reply to a " + ticket.getStatus() + " ticket. The conversation thread is locked.");
        }

        // Enforce unified enterprise authorization policy
        validateTicketAccess(ticket, user);

        // Enforce: Customers cannot post internal notes
        boolean isInternal = (user.getRole() != Role.CUSTOMER) && request.isInternal();

        TicketReply reply = new TicketReply(ticket, user, request.getMessage(), isInternal);
        TicketReply savedReply = replyRepository.save(reply);

        // Status updates on reply
        if (user.getRole() == Role.CUSTOMER && ticket.getStatus() == TicketStatus.WAITING_FOR_CUSTOMER) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            recordHistory(ticket, user, "STATUS_CHANGED", "WAITING_FOR_CUSTOMER", "IN_PROGRESS (Customer replied)");
        }

        if (attachment != null && !attachment.isEmpty()) {
            saveAttachmentFile(ticket, savedReply, attachment);
        }

        recordHistory(ticket, user, isInternal ? "INTERNAL_NOTE_ADDED" : "REPLY_POSTED", null, "By " + user.getEmail());

        // Notify customer or agent if not internal
        if (!isInternal) {
            User recipient = user.getRole() == Role.CUSTOMER && ticket.getAssignedAgent() != null
                    ? ticket.getAssignedAgent().getUser() : ticket.getCustomer().getUser();

            Notification replyNotif = notificationFactory.createTicketNotification(
                    recipient, NotificationType.TICKET_REPLIED, ticket);
            notificationService.sendNotification(replyNotif);
        }

        return mapReplyToDto(savedReply);
    }

    @Override
    @Transactional
    public AttachmentDto uploadAttachment(Long ticketId, Long replyId, MultipartFile file) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        validateTicketAccess(ticket);

        TicketReply reply = null;
        if (replyId != null) {
            reply = replyRepository.findById(replyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reply not found with id: " + replyId));
            if (!reply.getTicket().getId().equals(ticketId)) {
                throw new BadRequestException("Reply #" + replyId + " does not belong to ticket #" + ticketId);
            }
        }

        Attachment attachment = saveAttachmentFile(ticket, reply, file);
        return mapAttachmentToDto(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadAttachment(Long ticketId, Long attachmentId, String userEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id: " + attachmentId));

        if (!attachment.getTicket().getId().equals(ticketId)) {
            throw new BadRequestException("Attachment #" + attachmentId + " does not belong to ticket #" + ticketId);
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        // Enforce unified enterprise authorization policy
        validateTicketAccess(ticket, user);

        try {
            Path filePath = Paths.get(attachment.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Attachment file not found or unreadable on disk: " + attachment.getOriginalFileName());
            }

            String originalName = attachment.getOriginalFileName();
            String safeFilename = originalName != null ? Paths.get(originalName).getFileName().toString().replaceAll("[\r\n\"]", "_") : "attachment.bin";

            String contentType = attachment.getFileType();
            if (contentType == null || contentType.isBlank() || contentType.equalsIgnoreCase("text/html") || contentType.equalsIgnoreCase("image/svg+xml")) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(safeFilename, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new BadRequestException("Invalid attachment path: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketHistoryDto> getTicketHistory(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        validateTicketAccess(ticket);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isCustomer = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));

        return historyRepository.findByTicketIdOrderByTimestampDesc(ticketId)
                .stream()
                .filter(h -> !isCustomer || !"INTERNAL_NOTE_ADDED".equals(h.getAction()))
                .map(this::mapHistoryToDto).toList();
    }

    private void validateStatusTransition(TicketStatus current, TicketStatus target, Role role) {
        if (current == target) return;

        if (current == TicketStatus.CLOSED) {
            throw new BadRequestException("Closed tickets cannot be modified further.");
        }

        if (role == Role.CUSTOMER) {
            if (current == TicketStatus.RESOLVED && target == TicketStatus.IN_PROGRESS) {
                return;
            }
            throw new AccessDeniedException("Customers can only reopen resolved tickets (from RESOLVED to IN_PROGRESS). Status transition from " + current + " to " + target + " is forbidden.");
        }

        if (target == TicketStatus.CLOSED && role != Role.CUSTOMER_SERVICE_OFFICER && role != Role.OPERATIONS_SUPERVISOR && role != Role.CUSTOMER_SUPPORT_MANAGER) {
            throw new AccessDeniedException("Only support officers or supervisors can close tickets.");
        }

        if (target == TicketStatus.ESCALATED && role != Role.CUSTOMER_SERVICE_OFFICER && role != Role.OPERATIONS_SUPERVISOR) {
            throw new AccessDeniedException("Only assigned officers or supervisors can escalate tickets.");
        }
    }

    private Attachment saveAttachmentFile(Ticket ticket, TicketReply reply, MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileUploadException("Failed to store empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds maximum permitted limit of 10MB");
        }

        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment.bin";
            // Sanitize filename to prevent directory traversal attacks
            originalName = Paths.get(originalName).getFileName().toString();
            String fileExtension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")).toLowerCase() : "";

            // Security: Enforce explicit allowlist of permitted attachment formats
            if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                throw new BadRequestException("File type not permitted for security reasons: " + fileExtension);
            }

            // Security: Enforce file header signature / magic bytes verification
            validateMagicBytes(file, fileExtension);

            String storedName = UUID.randomUUID().toString() + fileExtension;
            Path destination = root.resolve(storedName).normalize();

            if (!destination.startsWith(root)) {
                throw new BadRequestException("Invalid file destination path detected");
            }

            Files.copy(file.getInputStream(), destination);

            // Derive safe MIME type server-side, eliminating client-controlled Content-Type
            String safeMimeType = SecurityUtils.deriveSafeMimeType(fileExtension);

            Attachment attachment = new Attachment(
                    ticket,
                    reply,
                    originalName,
                    storedName,
                    safeMimeType,
                    file.getSize(),
                    destination.toString()
            );

            return attachmentRepository.save(attachment);
        } catch (IOException e) {
            throw new FileUploadException("Could not store file: " + e.getMessage());
        }
    }

    private void validateMagicBytes(MultipartFile file, String extension) throws IOException {
        byte[] header = new byte[16];
        int read;
        try (InputStream is = file.getInputStream()) {
            read = is.read(header);
            if (read < 4) {
                if (!extension.equals(".txt") && !extension.equals(".csv")) {
                    throw new BadRequestException("File is corrupted or too small to be a valid document.");
                }
                return;
            }
        }

        // 1. Proactive Executable / Binary Exploit Prevention
        if (header[0] == 0x4D && header[1] == 0x5A) { // "MZ" DOS/PE executable / DLL
            throw new BadRequestException("Executable binary files (PE/EXE/DLL) are strictly prohibited.");
        }
        if (header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46) { // ELF executable
            throw new BadRequestException("Executable binary files (ELF) are strictly prohibited.");
        }
        if ((header[0] & 0xFF) == 0xCA && (header[1] & 0xFF) == 0xFE && (header[2] & 0xFF) == 0xBA && (header[3] & 0xFF) == 0xBE) { // Java class / Mach-O binary
            throw new BadRequestException("Compiled bytecode and Mach-O binaries are strictly prohibited.");
        }
        if (header[0] == '#' && header[1] == '!') { // Shell script shebang
            throw new BadRequestException("Executable shell scripts are strictly prohibited.");
        }

        // 2. Strict Magic Signature Verification Per Supported Extension
        switch (extension.toLowerCase()) {
            case ".pdf" -> {
                if (!(header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F')) {
                    throw new BadRequestException("File signature mismatch: file is not a valid PDF document.");
                }
                byte[] allBytes = file.getBytes();
                String tail = new String(allBytes, Math.max(0, allBytes.length - 2048), Math.min(allBytes.length, 2048), StandardCharsets.ISO_8859_1);
                if (!tail.contains("%%EOF")) {
                    throw new BadRequestException("Corrupted or incomplete PDF document: missing %%EOF trailer marker.");
                }
            }
            case ".png" -> {
                if (!((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G')) {
                    throw new BadRequestException("File signature mismatch: file is not a valid PNG image.");
                }
                validateImageParsability(file);
            }
            case ".jpg", ".jpeg" -> {
                if (!((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF)) {
                    throw new BadRequestException("File signature mismatch: file is not a valid JPEG image.");
                }
                validateImageParsability(file);
            }
            case ".gif" -> {
                if (!(header[0] == 'G' && header[1] == 'I' && header[2] == 'F')) {
                    throw new BadRequestException("File signature mismatch: file is not a valid GIF image.");
                }
                validateImageParsability(file);
            }
            case ".zip" -> {
                if (!(header[0] == 0x50 && header[1] == 0x4B)) { // 'P' 'K'
                    throw new BadRequestException("File signature mismatch: file is not a valid ZIP package.");
                }
                validateZipStructure(file);
            }
            case ".docx", ".xlsx" -> {
                if (!(header[0] == 0x50 && header[1] == 0x4B)) { // 'P' 'K'
                    throw new BadRequestException("File signature mismatch: file is not a valid Office package.");
                }
                validateOfficeDocument(file, extension);
            }
            case ".doc", ".xls" -> {
                if (!((header[0] & 0xFF) == 0xD0 && (header[1] & 0xFF) == 0xCF && (header[2] & 0xFF) == 0x11 && (header[3] & 0xFF) == 0xE0)) {
                    throw new BadRequestException("File signature mismatch: file is not a valid legacy Microsoft Office document.");
                }
            }
            case ".txt", ".csv" -> {
                validateTextContent(file);
            }
        }
    }

    private void validateImageParsability(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                throw new BadRequestException("Image file stream could not be read or is empty.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BadRequestException("Unsupported or unrecognized image format.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long totalPixels = (long) width * height;
                if (width <= 0 || width > 10000 || height <= 0 || height > 10000) {
                    throw new BadRequestException("Image dimensions (" + width + "x" + height + ") exceed maximum allowable safety limit (max 10,000x10,000 px).");
                }
                if (totalPixels > 25_000_000L) {
                    throw new BadRequestException("Image total pixel count (" + totalPixels + ") exceeds safety threshold (max 25 MP).");
                }
            } finally {
                reader.dispose();
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Unable to read image stream for validation: " + e.getMessage());
        }
    }

    private void validateZipStructure(MultipartFile file) {
        long compressedSize = file.getSize();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int entryCount = 0;
            long totalUncompressedBytes = 0;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("ZIP archive exceeds maximum entry limit of " + MAX_ZIP_ENTRIES + " entries.");
                }

                String name = entry.getName();
                if (name == null || name.isBlank()) {
                    throw new BadRequestException("ZIP entry name cannot be null or blank.");
                }

                // Path traversal protection including Windows format, UNC paths, and absolute paths
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") ||
                    name.contains(":") || name.contains("\0")) {
                    throw new BadRequestException("ZIP archive contains prohibited path traversal sequence or illegal filename: " + name);
                }

                Path normalized = Paths.get(name).normalize();
                if (normalized.isAbsolute() || normalized.startsWith("..")) {
                    throw new BadRequestException("ZIP archive entry resolves to an unsafe path: " + name);
                }

                if (!entry.isDirectory()) {
                    long entryBytes = 0;
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        entryBytes += read;
                        totalUncompressedBytes += read;

                        if (entryBytes > MAX_SINGLE_FILE_SIZE) {
                            throw new BadRequestException("ZIP entry '" + name + "' exceeds maximum single file size limit (20 MB).");
                        }
                        if (totalUncompressedBytes > MAX_TOTAL_EXPANDED_SIZE) {
                            throw new BadRequestException("ZIP archive exceeds maximum total uncompressed size limit (50 MB).");
                        }
                    }

                    // Compression ratio check for ZIP bomb protection
                    if (compressedSize > 0 && totalUncompressedBytes > compressedSize * MAX_COMPRESSION_RATIO && totalUncompressedBytes > 1024 * 1024) {
                        throw new BadRequestException("Potential ZIP bomb detected: compression ratio exceeds allowable threshold (" + MAX_COMPRESSION_RATIO + ":1).");
                    }
                }

                zis.closeEntry();
            }

            if (entryCount == 0) {
                throw new BadRequestException("ZIP archive is empty or invalid.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Corrupted or invalid ZIP archive stream: " + e.getMessage());
        }
    }

    private void validateOfficeDocument(MultipartFile file, String extension) {
        boolean hasContentTypes = false;
        boolean hasRels = false;
        boolean hasDocumentOrWorkbook = false;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytes = 0;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("Office document package exceeds maximum entry limit.");
                }

                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":")) {
                    throw new BadRequestException("Office package contains suspicious path traversal sequence: " + name);
                }

                if ("[Content_Types].xml".equalsIgnoreCase(name)) {
                    hasContentTypes = true;
                } else if ("_rels/.rels".equalsIgnoreCase(name) || "_rels\\.rels".equalsIgnoreCase(name)) {
                    hasRels = true;
                } else if (".docx".equalsIgnoreCase(extension) && "word/document.xml".equalsIgnoreCase(name)) {
                    hasDocumentOrWorkbook = true;
                } else if (".xlsx".equalsIgnoreCase(extension) && ("xl/workbook.xml".equalsIgnoreCase(name) || "xl/workbench.xml".equalsIgnoreCase(name))) {
                    hasDocumentOrWorkbook = true;
                }

                if (!entry.isDirectory()) {
                    long entryBytes = 0;
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        entryBytes += read;
                        totalBytes += read;
                        if (entryBytes > MAX_SINGLE_FILE_SIZE || totalBytes > MAX_TOTAL_EXPANDED_SIZE) {
                            throw new BadRequestException("Office package exceeds allowable decompressed size limits.");
                        }
                    }
                }
                zis.closeEntry();
            }

            if (!hasContentTypes || !hasRels || !hasDocumentOrWorkbook) {
                throw new BadRequestException("Invalid or malformed Microsoft Office (" + extension + ") package structure. Required OOXML components missing.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Corrupted or invalid Microsoft Office document package: " + e.getMessage());
        }
    }

    private void validateTextContent(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        for (byte b : bytes) {
            if (b == 0x00) {
                throw new BadRequestException("File content contains binary null bytes; file is not valid plain text or CSV.");
            }
        }
        String text = new String(bytes, StandardCharsets.UTF_8).toLowerCase();
        if (text.contains("<script") || text.contains("javascript:") || text.contains("<svg") ||
            text.contains("<iframe") || text.contains("onload=") || text.contains("onerror=")) {
            throw new BadRequestException("Plain text or CSV file contains prohibited embedded HTML/script markup.");
        }
    }

    private void recordHistory(Ticket ticket, User performedBy, String action, String oldValue, String newValue) {
        TicketHistory history = new TicketHistory(ticket, performedBy, action, oldValue, newValue);
        historyRepository.save(history);
    }

    private String generateTicketNumber() {
        return ticketSequenceService.getNextTicketNumber();
    }

    private TicketDto mapToSummaryDto(Ticket t) {
        TicketDto dto = new TicketDto();
        dto.setId(t.getId());
        dto.setTicketNumber(t.getTicketNumber());
        if (t.getCustomer() != null) {
            dto.setCustomerId(t.getCustomer().getId());
            dto.setCustomerName(t.getCustomer().getFullName());
            if (t.getCustomer().getUser() != null) {
                dto.setCustomerEmail(t.getCustomer().getUser().getEmail());
            }
        }
        if (t.getCategory() != null) {
            dto.setCategoryId(t.getCategory().getId());
            dto.setCategoryName(t.getCategory().getName());
        }

        if (t.getAssignedAgent() != null) {
            dto.setAssignedAgentId(t.getAssignedAgent().getId());
            dto.setAssignedAgentName(t.getAssignedAgent().getFullName());
        }

        dto.setSubject(t.getSubject());
        dto.setDescription(t.getDescription());
        dto.setPriority(t.getPriority());
        dto.setStatus(t.getStatus());

        // E-Commerce & SLA Mapping
        dto.setOrderNumber(t.getOrderNumber());
        dto.setTrackingNumber(t.getTrackingNumber());
        dto.setRefundStatus(t.getRefundStatus());
        dto.setSlaDueAt(t.getSlaDueAt());
        dto.setEscalated(t.isEscalated());

        dto.setCreatedAt(t.getCreatedAt());
        dto.setUpdatedAt(t.getUpdatedAt());
        dto.setResolvedAt(t.getResolvedAt());
        dto.setClosedAt(t.getClosedAt());

        // Empty collections to avoid N+1 subqueries across paginated listings
        dto.setReplies(Collections.emptyList());
        dto.setAttachments(Collections.emptyList());
        dto.setHistory(Collections.emptyList());
        dto.setHasFeedback(false);

        return dto;
    }

    private TicketDto mapToDto(Ticket t) {
        TicketDto dto = new TicketDto();
        dto.setId(t.getId());
        dto.setTicketNumber(t.getTicketNumber());
        dto.setCustomerId(t.getCustomer().getId());
        dto.setCustomerName(t.getCustomer().getFullName());
        dto.setCustomerEmail(t.getCustomer().getUser().getEmail());
        dto.setCategoryId(t.getCategory().getId());
        dto.setCategoryName(t.getCategory().getName());

        if (t.getAssignedAgent() != null) {
            dto.setAssignedAgentId(t.getAssignedAgent().getId());
            dto.setAssignedAgentName(t.getAssignedAgent().getFullName());
        }

        dto.setSubject(t.getSubject());
        dto.setDescription(t.getDescription());
        dto.setPriority(t.getPriority());
        dto.setStatus(t.getStatus());

        // E-Commerce & SLA Mapping
        dto.setOrderNumber(t.getOrderNumber());
        dto.setTrackingNumber(t.getTrackingNumber());
        dto.setRefundStatus(t.getRefundStatus());
        dto.setSlaDueAt(t.getSlaDueAt());
        dto.setEscalated(t.isEscalated());

        dto.setCreatedAt(t.getCreatedAt());
        dto.setUpdatedAt(t.getUpdatedAt());
        dto.setResolvedAt(t.getResolvedAt());
        dto.setClosedAt(t.getClosedAt());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isCustomer = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));

        List<TicketReplyDto> replyDtos = replyRepository.findByTicketIdOrderByCreatedAtAsc(t.getId())
                .stream()
                .filter(r -> !isCustomer || !r.isInternal())
                .map(this::mapReplyToDto).toList();
        dto.setReplies(replyDtos);

        List<AttachmentDto> attachmentDtos = attachmentRepository.findByTicketId(t.getId())
                .stream().map(this::mapAttachmentToDto).toList();
        dto.setAttachments(attachmentDtos);

        List<TicketHistoryDto> historyDtos = historyRepository.findByTicketIdOrderByTimestampDesc(t.getId())
                .stream()
                .filter(h -> !isCustomer || !"INTERNAL_NOTE_ADDED".equals(h.getAction()))
                .map(this::mapHistoryToDto).toList();
        dto.setHistory(historyDtos);

        dto.setHasFeedback(feedbackRepository.existsByTicketId(t.getId()));

        return dto;
    }

    private TicketReplyDto mapReplyToDto(TicketReply r) {
        TicketReplyDto dto = new TicketReplyDto();
        dto.setId(r.getId());
        dto.setTicketId(r.getTicket().getId());
        dto.setUserId(r.getUser().getId());
        dto.setUserName(r.getUser().getEmail());
        dto.setUserRole(r.getUser().getRole());
        dto.setMessage(r.getMessage());
        dto.setInternal(r.isInternal());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }

    private AttachmentDto mapAttachmentToDto(Attachment a) {
        AttachmentDto dto = new AttachmentDto();
        dto.setId(a.getId());
        dto.setTicketId(a.getTicket().getId());
        if (a.getReply() != null) {
            dto.setReplyId(a.getReply().getId());
        }
        dto.setOriginalFileName(a.getOriginalFileName());
        dto.setStoredFileName(a.getStoredFileName());
        dto.setFileType(a.getFileType());
        dto.setFileSize(a.getFileSize());
        dto.setFileUrl("/api/tickets/" + a.getTicket().getId() + "/attachments/" + a.getId() + "/download");
        dto.setUploadedAt(a.getUploadedAt());
        return dto;
    }

    private TicketHistoryDto mapHistoryToDto(TicketHistory h) {
        TicketHistoryDto dto = new TicketHistoryDto();
        dto.setId(h.getId());
        dto.setTicketId(h.getTicket().getId());
        dto.setPerformedByName(h.getPerformedBy() != null ? h.getPerformedBy().getEmail() : "System");
        dto.setAction(h.getAction());
        dto.setOldValue(h.getOldValue());
        dto.setNewValue(h.getNewValue());
        dto.setTimestamp(h.getTimestamp());
        return dto;
    }

    private TicketPriority evaluateAutomaticPriority(String subject, String description) {
        String text = ((subject != null ? subject : "") + " " + (description != null ? description : "")).toLowerCase();

        if (text.contains("system down") || text.contains("crash") || text.contains("500") ||
            text.contains("transaction failed") || text.contains("payment failed") ||
            text.contains("urgent") || text.contains("emergency") || text.contains("outage") ||
            text.contains("locked out") || text.contains("critical")) {
            return TicketPriority.URGENT;
        }

        if (text.contains("bug") || text.contains("error") || text.contains("failed") ||
            text.contains("cannot login") || text.contains("rate limit") || text.contains("delay") ||
            text.contains("broken") || text.contains("supervisor") || text.contains("slow")) {
            return TicketPriority.HIGH;
        }

        if (text.contains("doc") || text.contains("how to") || text.contains("inquiry") ||
            text.contains("info") || text.contains("guide") || text.contains("feature")) {
            return TicketPriority.LOW;
        }

        return TicketPriority.MEDIUM;
    }

    @Override
    @Transactional
    public TicketDto updateRefundStatus(Long ticketId, UpdateRefundStatusRequest request, String performedByEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));

        User user = userRepository.findByEmail(performedByEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + performedByEmail));

        // Customer role restriction: customers cannot modify refund status
        if (user.getRole() == Role.CUSTOMER) {
            throw new AccessDeniedException("Customers are not authorized to update refund status.");
        }

        // Unified enterprise authorization policy: officer must be assigned or handle category
        validateTicketAccess(ticket, user);

        RefundStatus oldStatus = ticket.getRefundStatus();
        RefundStatus newStatus = request.getRefundStatus();

        ticket.setRefundStatus(newStatus);
        Ticket savedTicket = ticketRepository.save(ticket);

        String note = request.getNote() != null ? request.getNote() : "";
        recordHistory(savedTicket, user, "REFUND_STATUS_UPDATED", oldStatus != null ? oldStatus.name() : "NONE",
                newStatus.name() + (note.isEmpty() ? "" : " - " + note));

        try {
            Notification notif = notificationFactory.createSystemNotification(
                    savedTicket.getCustomer().getUser(),
                    "Refund Status Updated: " + savedTicket.getTicketNumber(),
                    "Your refund status was updated to " + newStatus.name() + (note.isEmpty() ? "" : ". Note: " + note)
            );
            notificationService.sendNotification(notif);
        } catch (Exception e) {
            logger.error("Failed to deliver refund notification for ticket {}: {}", savedTicket.getTicketNumber(), e.getMessage());
        }

        return mapToDto(savedTicket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketDto> getEscalatedTickets() {
        return ticketRepository.findByIsEscalatedTrue().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int checkAndEscalateSlaBreaches() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> breached = ticketRepository.findUnescalatedBreachedTickets(now);
        for (Ticket ticket : breached) {
            ticket.setEscalated(true);
            ticketRepository.save(ticket);
            recordHistory(ticket, null, "SLA_BREACH_ESCALATED", "Normal",
                    "Ticket automatically escalated to Operations Supervisor due to SLA breach deadline (" + ticket.getSlaDueAt() + ")");
        }
        return breached.size();
    }
}
