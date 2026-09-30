package com.aditi.authservice.application.command;

/** Input for the "log in" use case. */
public record LoginCommand(String username, String rawPassword) {
}
