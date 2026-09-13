package com.fairhome.dedup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DuplicateFlagRepository extends JpaRepository<DuplicateFlag, Long> {

    List<DuplicateFlag> findByResolutionOrderByScoreDescIdAsc(DuplicateResolution resolution);

    Page<DuplicateFlag> findByResolutionOrderByScoreDescIdAsc(DuplicateResolution resolution,
                                                              Pageable pageable);

    List<DuplicateFlag> findByNewApplicationIdOrExistingApplicationId(Long newId, Long existingId);

    long countByResolution(DuplicateResolution resolution);
}
