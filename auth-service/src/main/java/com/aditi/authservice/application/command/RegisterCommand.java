package com.aditi.authservice.application.command;

import com.aditi.authservice.domain.model.Role;

/**
 * Input for the "register" use case. A record, so it is immutable and
 * self-documenting at the call site.
 */
public record RegisterCommand(String username, String rawPassword, Role role) {
}
