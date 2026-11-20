package com.customersupport.SupportHUB.feedback;

import com.customersupport.SupportHUB.category.TicketCategory;
import com.customersupport.SupportHUB.common.BadRequestException;
import com.customersupport.SupportHUB.common.DuplicateResourceException;
import com.customersupport.SupportHUB.common.Role;
import com.customersupport.SupportHUB.common.User;
import com.customersupport.SupportHUB.customer.Customer;
import com.customersupport.SupportHUB.customer.CustomerRepository;
import com.customersupport.SupportHUB.notification.NotificationFactory;
import com.customersupport.SupportHUB.notification.NotificationService;
import com.customersupport.SupportHUB.ticket.Ticket;
import com.customersupport.SupportHUB.ticket.TicketPriority;
import com.customersupport.SupportHUB.ticket.TicketRepository;
import com.customersupport.SupportHUB.ticket.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Customer Feedback & CSAT Immutability Tests")
class FeedbackServiceTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ReportService reportService;
    @Mock private NotificationService notificationService;
    @Mock private NotificationFactory notificationFactory;

    private FeedbackServiceImpl feedbackService;

    private Customer mockCustomer;
    private Ticket mockTicket;
    private User mockUser;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackServiceImpl(
                feedbackRepository,
                ticketRepository,
                customerRepository,
                reportService,
                notificationService,
                notificationFactory
        );

        mockUser = new User("customer@test.com", "hash", Role.CUSTOMER);
        mockCustomer = new Customer(mockUser, "Test Customer", "0771234567", "Colombo");
        mockCustomer.setId(10L);

        TicketCategory category = new TicketCategory("General Support", "General queries");
        category.setId(1L);

        mockTicket = new Ticket("TCK-1001", mockCustomer, category, "Login issue", "Cannot login", TicketPriority.MEDIUM);
        mockTicket.setId(100L);
    }

    @Test
    @DisplayName("Submit: Successful feedback on RESOLVED ticket")
    void testSubmitFeedback_Success_WhenTicketResolved() {
        mockTicket.setStatus(TicketStatus.RESOLVED);

        when(customerRepository.findByUserEmail("customer@test.com")).thenReturn(Optional.of(mockCustomer));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));
        when(feedbackRepository.existsByTicketId(100L)).thenReturn(false);

        Feedback savedFeedback = new Feedback(mockTicket, mockCustomer, 5, "Great experience", "None");
        savedFeedback.setId(50L);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(savedFeedback);

        CreateFeedbackRequest request = new CreateFeedbackRequest(100L, 5, "Great experience", "None");
        FeedbackDto dto = feedbackService.submitFeedback("customer@test.com", request);

        assertNotNull(dto);
        assertEquals(5, dto.getRating());
        assertEquals("Great experience", dto.getComment());
        verify(feedbackRepository, times(1)).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Submit: Throws BadRequestException when ticket is still OPEN")
    void testSubmitFeedback_ThrowsBadRequest_WhenTicketOpen() {
        mockTicket.setStatus(TicketStatus.OPEN);

        when(customerRepository.findByUserEmail("customer@test.com")).thenReturn(Optional.of(mockCustomer));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));

        CreateFeedbackRequest request = new CreateFeedbackRequest(100L, 5, "Should fail", null);
        assertThrows(BadRequestException.class, () -> feedbackService.submitFeedback("customer@test.com", request));
        verify(feedbackRepository, never()).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Submit: Prevents duplicate feedback submission")
    void testSubmitFeedback_ThrowsDuplicate_WhenFeedbackAlreadyExists() {
        mockTicket.setStatus(TicketStatus.RESOLVED);

        when(customerRepository.findByUserEmail("customer@test.com")).thenReturn(Optional.of(mockCustomer));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(mockTicket));
        when(feedbackRepository.existsByTicketId(100L)).thenReturn(true);

        CreateFeedbackRequest request = new CreateFeedbackRequest(100L, 4, "Second feedback", null);
        assertThrows(DuplicateResourceException.class, () -> feedbackService.submitFeedback("customer@test.com", request));
        verify(feedbackRepository, never()).save(any(Feedback.class));
    }

    @Test
    @DisplayName("Immutability: Update customer feedback throws BadRequestException")
    void testFeedbackImmutability_UpdateThrowsBadRequest() {
        UpdateFeedbackRequest request = new UpdateFeedbackRequest(3, "Modified comment", "Modified suggestions");
        assertThrows(BadRequestException.class, () -> feedbackService.updateFeedback(50L, request));
    }

    @Test
    @DisplayName("Immutability: Delete customer feedback throws BadRequestException")
    void testFeedbackImmutability_DeleteThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> feedbackService.deleteFeedback(50L));
    }
}
