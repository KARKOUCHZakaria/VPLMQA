package com.vplmqa.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ComponentSemanticUpdateRequest(
        @NotNull UUID componentId,
        @NotBlank String canonicalName,
        @NotBlank String semanticRole,
        @NotBlank String functionalMeaning
) {
}
