package com.vplmqa.common;

import java.time.Instant;

/**
 * Unified API response envelope used by all VPLMQA services.
 * All controllers must return this type — never raw entities or DTOs directly.
 *
 * @param <T> the type of the response data payload
 * @param success whether the request succeeded
 * @param data the response data payload
 * @param message a human-readable message
 * @param timestamp the response timestamp
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {
    /**
     * Constructs a success response with data.
     *
     * @param data the payload data
     * @param <T> the payload type
     * @return a successful {@link ApiResponse}
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", Instant.now());
    }

    /**
     * Constructs a success response with a custom message.
     *
     * @param data the payload data
     * @param message the message
     * @param <T> the payload type
     * @return a successful {@link ApiResponse}
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    /**
     * Constructs an error response with no data.
     *
     * @param message the error message
     * @param <T> the payload type
     * @return an error {@link ApiResponse}
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, Instant.now());
    }
}
