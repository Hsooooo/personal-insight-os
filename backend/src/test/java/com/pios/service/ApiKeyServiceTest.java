package com.pios.service;

import com.pios.domain.ApiKey;
import com.pios.repository.ApiKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    @Test
    void legacyScanSkippedPermanentlyWhenNoLegacyRows() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        when(repo.findByKeyPrefix(anyString())).thenReturn(List.of());
        when(repo.countByKeyPrefixIsNull()).thenReturn(0L);
        ApiKeyService service = new ApiKeyService(repo);

        assertThat(service.authenticate("pios_unknownkey1", "1.1.1.1")).isEmpty();
        assertThat(service.authenticate("pios_unknownkey2", "1.1.1.1")).isEmpty();

        verify(repo, times(1)).countByKeyPrefixIsNull();
        verify(repo, never()).findTop20ByKeyPrefixIsNullOrderByIdAsc();
    }

    @Test
    void legacyScanIsRateLimitedPerIp() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        when(repo.findByKeyPrefix(anyString())).thenReturn(List.of());
        when(repo.countByKeyPrefixIsNull()).thenReturn(1L);
        when(repo.findTop20ByKeyPrefixIsNullOrderByIdAsc()).thenReturn(List.of(ApiKey.builder()
                .id(1L).userId(1L).keyHash(new BCryptPasswordEncoder(4).encode("pios_realkey")).build()));
        ApiKeyService service = new ApiKeyService(repo);

        for (int i = 0; i < 25; i++) {
            service.authenticate("pios_bad" + i, "6.6.6.6");
        }
        verify(repo, times(20)).findTop20ByKeyPrefixIsNullOrderByIdAsc();
        // another IP still gets its own budget
        service.authenticate("pios_other", "7.7.7.7");
        verify(repo, times(21)).findTop20ByKeyPrefixIsNullOrderByIdAsc();
    }

    @Test
    void legacyKeyIsMigratedOnUse() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        ApiKey legacy = ApiKey.builder().id(3L).userId(9L)
                .keyHash(new BCryptPasswordEncoder(4).encode("pios_legacyKeyValue123")).build();
        when(repo.findByKeyPrefix(anyString())).thenReturn(List.of());
        when(repo.countByKeyPrefixIsNull()).thenReturn(1L);
        when(repo.findTop20ByKeyPrefixIsNullOrderByIdAsc()).thenReturn(List.of(legacy));
        ApiKeyService service = new ApiKeyService(repo);

        assertThat(service.authenticate("pios_legacyKeyValue123", "1.1.1.1"))
                .contains(new ApiKeyService.ApiKeyPrincipal(3L, 9L));
        assertThat(legacy.getKeyPrefix()).isEqualTo("pios_legacyKeyVa");
        verify(repo).save(legacy);
        verify(repo).touch(eq(3L), any(), eq("1.1.1.1"));
    }
}
