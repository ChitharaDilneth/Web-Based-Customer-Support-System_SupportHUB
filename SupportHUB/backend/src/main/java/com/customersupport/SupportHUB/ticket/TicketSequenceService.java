package com.customersupport.SupportHUB.ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise Database-Backed Atomic Ticket Number Generator.
 * Executes in an independent REQUIRES_NEW transaction with pessimistic write locking
 * to ensure crash safety, multi-instance concurrency safety, and guaranteed uniqueness.
 */
@Service
public class TicketSequenceService {

    private static final Logger logger = LoggerFactory.getLogger(TicketSequenceService.class);

    private final TicketSequenceRepository sequenceRepository;
    private final TicketRepository ticketRepository;

    public TicketSequenceService(TicketSequenceRepository sequenceRepository, TicketRepository ticketRepository) {
        this.sequenceRepository = sequenceRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Generates the next sequential ticket number in the format TKT-YYYY-XXXXXX.
     * Guaranteed atomic across restarts and multiple running instances.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String getNextTicketNumber() {
        int year = LocalDateTime.now().getYear();
        String prefix = String.format("TKT-%d-", year);

        TicketSequence sequence = sequenceRepository.findBySequenceYearWithLock(year)
                .orElseGet(() -> {
                    long seedValue = bootstrapSequenceFromExistingTickets(prefix);
                    TicketSequence initial = new TicketSequence(year, seedValue);
                    return sequenceRepository.saveAndFlush(initial);
                });

        long nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        sequenceRepository.saveAndFlush(sequence);

        String generated = String.format("TKT-%d-%06d", year, nextValue);
        logger.debug("Allocated atomic ticket number: {}", generated);
        return generated;
    }

    private long bootstrapSequenceFromExistingTickets(String prefix) {
        try {
            List<String> topTickets = ticketRepository.findTopTicketNumberByPrefix(prefix + "%", PageRequest.of(0, 1));
            if (!topTickets.isEmpty()) {
                String top = topTickets.get(0);
                String suffix = top.substring(top.lastIndexOf('-') + 1);
                long parsed = Long.parseLong(suffix);
                logger.info("Bootstrapping ticket sequence from existing database record: {} -> {}", top, parsed);
                return parsed;
            }
        } catch (Exception e) {
            logger.warn("Could not parse existing ticket number suffix for bootstrap. Defaulting to 0: {}", e.getMessage());
        }
        return 0L;
    }
}
