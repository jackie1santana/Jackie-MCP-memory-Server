package org.example.chatgptmcpserver.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SetMemoryScopeRequest(
		@NotBlank String scope,
		String scopeContext
) {
}

