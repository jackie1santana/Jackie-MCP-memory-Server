package org.example.chatgptmcpserver.exception;

public class MemoryNotFoundException extends RuntimeException {
    public MemoryNotFoundException(String message) {
        super(message);
    }
}

