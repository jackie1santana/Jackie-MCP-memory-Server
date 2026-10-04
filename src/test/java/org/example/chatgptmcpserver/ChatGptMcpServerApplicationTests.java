package org.example.chatgptmcpserver;

import org.example.chatgptmcpserver.domain.MemoryScope;
import org.junit.jupiter.api.Test;
class ChatGptMcpServerApplicationTests {

    @Test
    void scopeEnumParsesExpectedValues() {
        org.assertj.core.api.Assertions.assertThat(MemoryScope.fromValue("personal").value()).isEqualTo("personal");
        org.assertj.core.api.Assertions.assertThat(MemoryScope.fromValue("off").value()).isEqualTo("off");
    }

}
