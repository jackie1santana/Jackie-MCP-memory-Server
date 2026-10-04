package org.example.chatgptmcpserver.service;

import org.example.chatgptmcpserver.dto.request.GetMemoryTimelineRequest;
import org.example.chatgptmcpserver.dto.request.RememberMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SearchMemoryRequest;
import org.example.chatgptmcpserver.dto.request.SetMemoryScopeRequest;
import org.example.chatgptmcpserver.dto.request.UpdateMemoryRequest;
import org.example.chatgptmcpserver.dto.response.MemoryResponse;

import java.util.List;
import java.util.UUID;

public interface MemoryService {

    MemoryResponse saveMemory(RememberMemoryRequest request);

    List<MemoryResponse> searchMemories(SearchMemoryRequest request);

    MemoryResponse getMemory(UUID id, String explicitScope, String scopeContext);

    MemoryResponse updateMemory(UpdateMemoryRequest request);

    boolean deactivateMemory(UUID id);

    List<MemoryResponse> getMemoriesByScope(String scope, Integer limit);

    List<MemoryResponse> getMemoriesByCategory(String category, String scope, Integer limit);

    String setActiveMemoryScope(SetMemoryScopeRequest request);

    String getActiveMemoryScope();

    String getActiveMemoryScope(String scopeContext);

    List<MemoryResponse> getTimelineMemories(GetMemoryTimelineRequest request);

    List<String> getCategories();
}

