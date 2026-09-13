package com.fairhome.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByApplicationNumber(String applicationNumber);

    List<Application> findByNationalIdOrderByIdAsc(String nationalId);

    List<Application> findByNormalisedNameAndDateOfBirth(String normalisedName, LocalDate dateOfBirth);

    List<Application> findByDateOfBirth(LocalDate dateOfBirth);

    List<Application> findByNormalisedPhone(String normalisedPhone);

    List<Application> findByNormalisedEmail(String normalisedEmail);

    List<Application> findByStatusOrderByIdAsc(ApplicationStatus status);

    long countByStatus(ApplicationStatus status);

    long countByChannel(Channel channel);

    @Query("""
            select a from Application a
            where (:q is null or :q = ''
                   or lower(a.fullName) like lower(concat('%', :q, '%'))
                   or a.applicationNumber like concat('%', :q, '%')
                   or a.nationalIdLast4 = :q)
              and (:status is null or a.status = :status)
              and (:channel is null or a.channel = :channel)
            order by a.id desc
            """)
    Page<Application> search(@Param("q") String q,
                             @Param("status") ApplicationStatus status,
                             @Param("channel") Channel channel,
                             Pageable pageable);

    @Query("select coalesce(max(a.id), 0) from Application a")
    long maxId();
}
