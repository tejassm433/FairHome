package com.fairhome.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleSetRepository extends JpaRepository<RuleSetVersion, Long> {

    Optional<RuleSetVersion> findByActiveTrue();

    Optional<RuleSetVersion> findByVersion(Integer version);

    List<RuleSetVersion> findAllByOrderByVersionDesc();
}
