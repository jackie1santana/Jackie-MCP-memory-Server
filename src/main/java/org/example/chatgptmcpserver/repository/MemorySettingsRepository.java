package org.example.chatgptmcpserver.repository;

public interface MemorySettingsRepository {

    void setActiveScope(String scope, String scopeContext);

    String getActiveScope(String scopeContext);
}

