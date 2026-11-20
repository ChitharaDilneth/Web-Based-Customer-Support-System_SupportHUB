package com.customersupport.SupportHUB.ticket;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketSequenceRepository extends JpaRepository<TicketSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TicketSequence s WHERE s.sequenceYear = :year")
    Optional<TicketSequence> findBySequenceYearWithLock(@Param("year") Integer year);
}
