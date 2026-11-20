package com.customersupport.SupportHUB.ticket;

import com.customersupport.SupportHUB.agent.SupportAgent;
import com.customersupport.SupportHUB.agent.SupportAgentRepository;
import com.customersupport.SupportHUB.category.TicketCategory;
import com.customersupport.SupportHUB.category.TicketCategoryRepository;
import com.customersupport.SupportHUB.common.AccessDeniedException;
import com.customersupport.SupportHUB.common.Attachment;
import com.customersupport.SupportHUB.common.AttachmentRepository;
import com.customersupport.SupportHUB.common.BadRequestException;
import com.customersupport.SupportHUB.common.Role;
import com.customersupport.SupportHUB.common.User;
import com.customersupport.SupportHUB.common.UserRepository;
import com.customersupport.SupportHUB.customer.Customer;
import com.customersupport.SupportHUB.customer.CustomerRepository;
import com.customersupport.SupportHUB.feedback.FeedbackRepository;
import com.customersupport.SupportHUB.notification.NotificationFactory;
import com.customersupport.SupportHUB.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Ticket Service Lifecycle & Security Tests")
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SupportAgentRepository agentRepository;
    @Mock private TicketCategoryRepository categoryRepository;
    @Mock private TicketReplyRepository replyRepository;
    @Mock private TicketHistoryRepository historyRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private FeedbackRepository feedbackRepository;
    @Mock private NotificationService notificationService;
    @Mock private NotificationFactory notificationFactory;
    @Mock private TicketSequenceService ticketSequenceService;

    private TicketServiceImpl ticketService;

    private Customer mockCustomer;
    private Ticket mockTicket;
    private SupportAgent mockAgent;
    private TicketCategory deliveryCategory;
    private TicketCategory paymentCategory;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(
                ticketRepository,
                customerRepository,
                agentRepository,
                categoryRepository,
                replyRepository,
                historyRepository,
                attachmentRepository,
                userRepository,
                feedbackRepository,
                notificationService,
                notificationFactory,
                ticketSequenceService
        );
        ReflectionTestUtils.setField(ticketService, "uploadDir", "./uploads");

        User customerUser = new User("customer@supporthub.com", "hash", Role.CUSTOMER);
        customerUser.setId(1L);
        lenient().when(userRepository.findByEmail("customer@supporthub.com")).thenReturn(Optional.of(customerUser));
        mockCustomer = new Customer(customerUser, "John Customer", "0771234567", "Colombo, Sri Lanka");
        mockCustomer.setId(1L);

        deliveryCategory = new TicketCategory("Delivery Issues", "Delivery issues");
        deliveryCategory.setId(10L);
        deliveryCategory.setSlaHours(24);

        paymentCategory = new TicketCategory("Payment & Refunds", "Payment issues");
        paymentCategory.setId(20L);
        paymentCategory.setSlaHours(12);

        mockTicket = new Ticket("TCK-2026-000001", mockCustomer, deliveryCategory, "Late delivery", "My order is delayed", TicketPriority.MEDIUM);
        mockTicket.setId(100L);

        User officerUser = new User("officer@supporthub.com", "hash", Role.CUSTOMER_SERVICE_OFFICER);
        officerUser.setId(2L);
        mockAgent = new SupportAgent(officerUser, "Officer Alice", "0779998888", "EMP-001");
        mockAgent.setId(5L);
        mockAgent.setAssignedCategories(Set.of(deliveryCategory));

        lenient().when(ticketSequenceService.getNextTicketNumber()).thenReturn("TKT-2026-000001");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create Ticket: Correct SLA calculation & sequence assignment")
    void testCreateTicket_CalculatesPriorityAndSla() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setCategoryId(10L);
        request.setSubject("Item not arrived");
        request.setDescription("Package has not arrived yet");
        request.setPriority(TicketPriority.HIGH);

        when(customerRepository.findByUserEmail("customer@supporthub.com")).thenReturn(Optional.of(mockCustomer));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(deliveryCategory));
        when(agentRepository.findByCategoryId(10L)).thenReturn(Collections.emptyList());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketDto dto = ticketService.createTicket("customer@supporthub.com", request, null);

        assertNotNull(dto);
        assertEquals("TKT-2026-000001", dto.getTicketNumber());
        assertEquals(TicketPriority.HIGH, dto.getPriority());
        assertNotNull(dto.getSlaDueAt());
    }

    @Test
    @DisplayName("Assignment: Rejects assignment if agent lacks ticket category")
    void testAssignAgent_InvalidCategory_ThrowsBadRequest() {
        mockTicket.setCategory(paymentCategory); // Delivery category agent cannot take Payment ticket

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));
        when(agentRepository.findById(5L)).thenReturn(Optional.of(mockAgent));

        assertThrows(BadRequestException.class, () -> ticketService.assignAgent(100L, 5L, "supervisor@supporthub.com"));
    }

    @Test
    @DisplayName("Access Control: Customer cannot view another customer's ticket")
    void testCustomerTicketOwnership_AccessDenied_WhenViewingOtherCustomerTicket() {
        Customer otherCustomer = new Customer();
        otherCustomer.setId(999L);
        User otherUser = new User("other@supporthub.com", "hash", Role.CUSTOMER);
        otherUser.setId(999L);
        otherCustomer.setUser(otherUser);
        mockTicket.setCustomer(otherCustomer);

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );

        assertThrows(AccessDeniedException.class, () -> ticketService.getTicketById(100L));
    }

    @Test
    @DisplayName("Access Control: Officer outside caseload and category cannot access ticket")
    void testOfficerCannotViewTicket_OutsideCaseloadAndCategory_ThrowsAccessDenied() {
        mockTicket.setAssignedAgent(null);
        mockTicket.setCategory(paymentCategory);

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        User intruderUser = new User("intruder@supporthub.com", "hash", Role.CUSTOMER_SERVICE_OFFICER);
        intruderUser.setId(99L);
        when(userRepository.findByEmail("intruder@supporthub.com")).thenReturn(Optional.of(intruderUser));

        SupportAgent intruderAgent = new SupportAgent(intruderUser, "Intruder Officer", "0770000000", "EMP-999");
        intruderAgent.setId(99L);
        intruderAgent.setAssignedCategories(Set.of(deliveryCategory));
        when(agentRepository.findByUserId(99L)).thenReturn(Optional.of(intruderAgent));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("intruder@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_SERVICE_OFFICER")))
        );

        assertThrows(AccessDeniedException.class, () -> ticketService.getTicketById(100L));
    }

    @Test
    @DisplayName("Access Control: Officer without SupportAgent record fails closed")
    void testOfficerWithoutSupportAgentRecord_ThrowsAccessDenied() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        User ghostUser = new User("ghost@supporthub.com", "hash", Role.CUSTOMER_SERVICE_OFFICER);
        ghostUser.setId(500L);
        when(userRepository.findByEmail("ghost@supporthub.com")).thenReturn(Optional.of(ghostUser));
        when(agentRepository.findByUserId(500L)).thenReturn(Optional.empty());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_SERVICE_OFFICER")))
        );

        assertThrows(AccessDeniedException.class, () -> ticketService.getTicketById(100L));
    }

    @Test
    @DisplayName("Refund: Enforces officer caseload validation on refund updates")
    void testOfficerRefundUpdate_CaseloadEnforcement_ThrowsAccessDenied() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        User unassignedOfficer = new User("other_officer@supporthub.com", "hash", Role.CUSTOMER_SERVICE_OFFICER);
        unassignedOfficer.setId(77L);
        when(userRepository.findByEmail("other_officer@supporthub.com")).thenReturn(Optional.of(unassignedOfficer));

        SupportAgent unassignedAgent = new SupportAgent(unassignedOfficer, "Unassigned Agent", "0771112222", "EMP-077");
        unassignedAgent.setId(77L);
        unassignedAgent.setAssignedCategories(Collections.emptySet());
        when(agentRepository.findByUserId(77L)).thenReturn(Optional.of(unassignedAgent));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("other_officer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_SERVICE_OFFICER")))
        );

        UpdateRefundStatusRequest req = new UpdateRefundStatusRequest(RefundStatus.APPROVED, "Testing refund");
        assertThrows(AccessDeniedException.class, () -> ticketService.updateRefundStatus(100L, req, "other_officer@supporthub.com"));
    }

    @Test
    @DisplayName("Attachment Security: Blocks XSS/HTML scripts in text/csv files")
    void testAttachmentValidation_BlocksProhibitedHtmlInTextFile() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        byte[] payload = "Order,Status\n101,<script>alert('XSS')</script>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "invoice.csv", "text/csv", payload);

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        assertThrows(BadRequestException.class, () ->
                ticketService.uploadAttachment(100L, null, file)
        );
    }

    @Test
    @DisplayName("Attachment Security: Blocks truncated PDF without %%EOF marker")
    void testAttachmentValidation_BlocksTruncatedPdf() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        byte[] payload = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>".getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", payload);

        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        assertThrows(BadRequestException.class, () ->
                ticketService.uploadAttachment(100L, null, file)
        );
    }

    @Test
    @DisplayName("Attachment Security: Blocks ZIP path traversal (Zip-Slip)")
    void testAttachmentValidation_BlocksZipTraversal() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("../../etc/passwd"));
            zos.write("root:x:0:0::/root:/bin/bash".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "archive.zip", "application/zip", baos.toByteArray());
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        assertThrows(BadRequestException.class, () ->
                ticketService.uploadAttachment(100L, null, file)
        );
    }

    @Test
    @DisplayName("Attachment Security: Blocks ZIP bomb (ratio > 20:1)")
    void testAttachmentValidation_BlocksZipBomb() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("bomb.txt"));
            // Write 3 MB of highly compressible zeroes
            byte[] zeros = new byte[1024 * 1024 * 3];
            zos.write(zeros);
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "bomb.zip", "application/zip", baos.toByteArray());
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        assertThrows(BadRequestException.class, () ->
                ticketService.uploadAttachment(100L, null, file)
        );
    }

    @Test
    @DisplayName("Attachment Security: Rejects WebP from allowlist")
    void testAttachmentValidation_RejectsWebpFromAllowlist() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        byte[] webpHeader = new byte[16];
        webpHeader[0] = 'R'; webpHeader[1] = 'I'; webpHeader[2] = 'F'; webpHeader[3] = 'F';
        webpHeader[8] = 'W'; webpHeader[9] = 'E'; webpHeader[10] = 'B'; webpHeader[11] = 'P';

        MockMultipartFile file = new MockMultipartFile("file", "photo.webp", "image/webp", webpHeader);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        assertThrows(BadRequestException.class, () ->
                ticketService.uploadAttachment(100L, null, file)
        );
    }

    @Test
    @DisplayName("Attachment Security: Validates Microsoft Office (DOCX/XLSX) package components")
    void testAttachmentValidation_OfficeDocumentStructure() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        // Create malformed DOCX without word/document.xml
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            zos.write("<Relationships></Relationships>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile("file", "report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray());
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        // Missing word/document.xml must throw BadRequestException
        assertThrows(BadRequestException.class, () ->
                ticketService.uploadAttachment(100L, null, file)
        );
    }

    @Test
    @DisplayName("Attachment Security: Inspects image dimensions and rejects oversized dimensions (> 10,000 px)")
    void testAttachmentValidation_ImageDimensionsAndPixels() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@supporthub.com", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
        );
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        MockMultipartFile validImage = new MockMultipartFile("file", "valid.png", "image/png", baos.toByteArray());
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        Attachment savedAtt = new Attachment(mockTicket, null, "valid.png", "uuid.png", "image/png", 100L, "./uploads/uuid.png");
        savedAtt.setId(1L);
        when(attachmentRepository.save(any(Attachment.class))).thenReturn(savedAtt);

        assertDoesNotThrow(() ->
                ticketService.uploadAttachment(100L, null, validImage)
        );
    }

    @Test
    @DisplayName("Ticket Number: Database-backed sequence ensures monotonic atomic generation")
    void testTicketSequenceGeneration_DatabaseBacked() {
        when(ticketSequenceService.getNextTicketNumber()).thenReturn("TKT-2026-000042");

        CreateTicketRequest request = new CreateTicketRequest();
        request.setCategoryId(10L);
        request.setSubject("Database Sequence Test");
        request.setDescription("Testing atomic generation");

        when(customerRepository.findByUserEmail("customer@supporthub.com")).thenReturn(Optional.of(mockCustomer));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(deliveryCategory));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));

        TicketDto dto = ticketService.createTicket("customer@supporthub.com", request, null);
        assertEquals("TKT-2026-000042", dto.getTicketNumber());
        verify(ticketSequenceService, times(1)).getNextTicketNumber();
    }
}
