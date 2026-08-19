package com.vplmqa.e2e.dto;

import jakarta.validation.constraints.NotBlank;

public record SecretValueRequest(@NotBlank String value) {}
