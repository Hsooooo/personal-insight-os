package com.pios.repository;

import com.pios.domain.FinanceCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinanceCycleRepository extends JpaRepository<FinanceCycle, Long> {
    List<FinanceCycle> findByUserIdOrderByStartsAtDesc(Long userId);
    Optional<FinanceCycle> findByUserIdAndStartsAt(Long userId, Instant startsAt);
    Optional<FinanceCycle> findFirstByUserIdAndStartsAtLessThanEqualOrderByStartsAtDesc(Long userId, Instant startsAt);
}
