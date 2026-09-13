package com.fairhome.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findFirstByOrderBySequenceDesc();

    Page<AuditEvent> findAllByOrderBySequenceDesc(Pageable pageable);

    List<AuditEvent> findAllByOrderBySequenceAsc();

    @Query("select coalesce(max(a.sequence), 0) from AuditEvent a")
    long maxSequence();
}
