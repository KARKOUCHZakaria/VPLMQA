package com.vplmqa.project.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating a page.
 *
 * @param name page name
 * @param url page URL
 * @param path page path
 */
public record PageRequest(
        @NotBlank String name,
        @NotBlank String url,
        @NotBlank String path
) {
}
