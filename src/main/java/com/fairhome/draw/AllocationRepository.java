package com.fairhome.draw;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    Optional<Allocation> findByDrawRunIdAndApplicationId(Long drawRunId, Long applicationId);

    List<Allocation> findByDrawRunIdOrderBySeatNumberAsc(Long drawRunId);

    void deleteByDrawRunId(Long drawRunId);

    @Query("""
            select a from Allocation a
            where a.drawRunId = :runId
              and (:outcome is null or a.outcome = :outcome)
              and (:category is null or :category = '' or a.categoryCode = :category)
              and (:q is null or :q = ''
                   or lower(a.applicantName) like lower(concat('%', :q, '%'))
                   or a.applicationNumber like concat('%', :q, '%'))
            order by a.categoryCode asc, a.rankInCategory asc
            """)
    Page<Allocation> search(@Param("runId") Long runId,
                            @Param("outcome") Outcome outcome,
                            @Param("category") String category,
                            @Param("q") String q,
                            Pageable pageable);

    @Query("""
            select a.categoryCode, a.outcome, count(a)
            from Allocation a where a.drawRunId = :runId
            group by a.categoryCode, a.outcome
            order by a.categoryCode
            """)
    List<Object[]> summariseByCategory(@Param("runId") Long runId);
}
