package org.example.chatgptmcpserver.repository;

import java.util.List;
import java.util.UUID;

public interface MemoryTagRepository {

    void replaceTags(UUID memoryId, List<String> tags);

    List<String> findTagNamesByMemoryId(UUID memoryId);
}

