package com.customersupport.SupportHUB.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Enterprise Database-Backed Atomic Ticket Sequence Entity.
 * Guarantees monotonic uniqueness across restarts and multiple running instances.
 */
@Entity
@Table(name = "ticket_sequences")
public class TicketSequence {

    @Id
    @Column(name = "sequence_year", nullable = false)
    private Integer sequenceYear;

    @Column(name = "current_value", nullable = false)
    private Long currentValue;

    public TicketSequence() {
    }

    public TicketSequence(Integer sequenceYear, Long currentValue) {
        this.sequenceYear = sequenceYear;
        this.currentValue = currentValue;
    }

    public Integer getSequenceYear() {
        return sequenceYear;
    }

    public void setSequenceYear(Integer sequenceYear) {
        this.sequenceYear = sequenceYear;
    }

    public Long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Long currentValue) {
        this.currentValue = currentValue;
    }
}
