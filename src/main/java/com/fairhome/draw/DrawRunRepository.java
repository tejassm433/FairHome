package com.fairhome.draw;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrawRunRepository extends JpaRepository<DrawRun, Long> {

    List<DrawRun> findAllByOrderByIdDesc();

    Optional<DrawRun> findByPublishedTrue();

    Optional<DrawRun> findFirstByModeOrderByIdDesc(DrawMode mode);
}
