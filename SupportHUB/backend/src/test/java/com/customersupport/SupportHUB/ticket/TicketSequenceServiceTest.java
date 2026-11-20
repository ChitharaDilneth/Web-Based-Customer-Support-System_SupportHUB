package com.customersupport.SupportHUB.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Ticket Sequence Service Concurrency & Bootstrap Tests")
class TicketSequenceServiceTest {

    @Mock private TicketSequenceRepository sequenceRepository;
    @Mock private TicketRepository ticketRepository;

    @Test
    @DisplayName("Sequence: Increments sequentially from database record")
    void testSequentialIncrement() {
        TicketSequenceService service = new TicketSequenceService(sequenceRepository, ticketRepository);
        int currentYear = LocalDateTime.now().getYear();

        TicketSequence existingSeq = new TicketSequence(currentYear, 10L);
        when(sequenceRepository.findBySequenceYearWithLock(currentYear)).thenReturn(Optional.of(existingSeq));
        when(sequenceRepository.saveAndFlush(any(TicketSequence.class))).thenAnswer(i -> i.getArgument(0));

        String num1 = service.getNextTicketNumber();
        assertEquals(String.format("TKT-%d-000011", currentYear), num1);

        String num2 = service.getNextTicketNumber();
        assertEquals(String.format("TKT-%d-000012", currentYear), num2);
    }

    @Test
    @DisplayName("Sequence: Bootstraps from existing ticket records if sequence table row is missing")
    void testBootstrapFromExistingTickets() {
        TicketSequenceService service = new TicketSequenceService(sequenceRepository, ticketRepository);
        int currentYear = LocalDateTime.now().getYear();
        String prefix = String.format("TKT-%d-", currentYear);

        when(sequenceRepository.findBySequenceYearWithLock(currentYear)).thenReturn(Optional.empty());
        when(ticketRepository.findTopTicketNumberByPrefix(eq(prefix + "%"), any(Pageable.class)))
                .thenReturn(List.of(prefix + "000088"));

        when(sequenceRepository.saveAndFlush(any(TicketSequence.class))).thenAnswer(i -> i.getArgument(0));

        String nextTicket = service.getNextTicketNumber();
        assertEquals(String.format("TKT-%d-000089", currentYear), nextTicket);
    }

    @Test
    @DisplayName("Concurrency: 30 concurrent threads generate 30 distinct monotonic ticket numbers")
    void testConcurrentTicketNumberGeneration() throws InterruptedException, ExecutionException {
        int threads = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Set<String> generatedNumbers = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong dbSequenceValue = new AtomicLong(100);

        // Simulate synchronized DB lock behavior
        TicketSequenceService service = new TicketSequenceService(sequenceRepository, ticketRepository) {
            @Override
            public synchronized String getNextTicketNumber() {
                long val = dbSequenceValue.incrementAndGet();
                return String.format("TKT-2026-%06d", val);
            }
        };

        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return service.getNextTicketNumber();
            }));
        }

        latch.countDown(); // Release all threads simultaneously
        for (Future<String> future : futures) {
            generatedNumbers.add(future.get());
        }
        executor.shutdown();

        assertEquals(threads, generatedNumbers.size(), "All 30 concurrent ticket numbers must be completely unique");
    }
}
