package com.fairhome.dedup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DuplicateFlagRepository extends JpaRepository<DuplicateFlag, Long> {

    @Query("select f from DuplicateFlag f where f.resolution = :resolution order by f.score desc, f.id asc")
    List<DuplicateFlag> findByResolutionOrderByScoreDescIdAsc(@Param("resolution") DuplicateResolution resolution);

    @Query("select f from DuplicateFlag f where f.resolution = :resolution order by f.score desc, f.id asc")
    Page<DuplicateFlag> findByResolutionOrderByScoreDescIdAsc(@Param("resolution") DuplicateResolution resolution,
                                                              Pageable pageable);

    List<DuplicateFlag> findByNewApplicationIdOrExistingApplicationId(Long newId, Long existingId);

    @Query("select count(f) from DuplicateFlag f where f.resolution = :resolution")
    long countByResolution(@Param("resolution") DuplicateResolution resolution);
}
