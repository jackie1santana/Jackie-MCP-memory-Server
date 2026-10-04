package org.example.chatgptmcpserver.repository;

import org.example.chatgptmcpserver.domain.Memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository {

    Memory save(Memory memory);

    Optional<Memory> findById(UUID id);

    Memory update(Memory memory);

    boolean deactivate(UUID id);

    List<Memory> search(String query, String scope, String category, int limit);

    List<Memory> findByScope(String scope, int limit);

    List<Memory> findByCategory(String category, String scope, int limit);

    List<Memory> findTimeline(Instant from, Instant to, String scope, int limit);

    List<String> findCategories();
}

