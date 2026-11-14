package com.pios.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyncLogStartupSweeperTest {

    @Test
    void onlySweepsRunningLogsStartedBeforeProcessStart() {
        Instant start = Instant.parse("2026-11-14T00:00:00Z");
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class, RETURNS_SELF);
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(2);

        SyncLogStartupSweeper sweeper = new SyncLogStartupSweeper(start);
        ReflectionTestUtils.setField(sweeper, "entityManager", em);
        sweeper.run(null);

        verify(em).createQuery(contains("s.startedAt < :processStart"));
        verify(query).setParameter("processStart", start);
    }

    @Test
    void defaultProcessStartIsJvmStartTime() {
        assertThat(new SyncLogStartupSweeper().getProcessStart()).isBefore(Instant.now());
    }
}
