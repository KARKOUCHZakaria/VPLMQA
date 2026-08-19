package com.vplmqa.auth.controller;

import com.vplmqa.auth.dto.AuthResponse;
import com.vplmqa.auth.dto.LoginRequest;
import com.vplmqa.auth.dto.RefreshRequest;
import com.vplmqa.auth.dto.RegisterRequest;
import com.vplmqa.auth.dto.ResetPasswordConfirmRequest;
import com.vplmqa.auth.dto.ResetPasswordRequest;
import com.vplmqa.auth.service.AuthService;
import com.vplmqa.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    /**
     * Creates the controller.
     *
     * @param authService auth service
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user.
     *
     * @param request request payload
     * @param httpRequest HTTP servlet request
     * @return auth response
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(request, httpRequest)));
    }

    /**
     * Authenticates a user.
     *
     * @param request request payload
     * @param httpRequest HTTP servlet request
     * @return auth response
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request, httpRequest)));
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param request refresh request
     * @param httpRequest HTTP servlet request
     * @return new auth response
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request,
                                                             HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request, httpRequest)));
    }

    /**
     * Logs out a user by revoking tokens.
     *
     * @param request refresh request
     * @param httpRequest HTTP servlet request
     * @return confirmation response
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshRequest request,
                                                    HttpServletRequest httpRequest) {
        authService.logout(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out"));
    }

    /**
     * Initiates a password reset request.
     *
     * @param request request payload
     * @param httpRequest HTTP servlet request
     * @return confirmation response
     */
    @PostMapping("/reset-password/request")
    public ResponseEntity<ApiResponse<Void>> resetPasswordRequest(@Valid @RequestBody ResetPasswordRequest request,
                                                                  HttpServletRequest httpRequest) {
        authService.resetPasswordRequest(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null, "Reset instructions sent"));
    }

    /**
     * Confirms a password reset.
     *
     * @param request request payload
     * @param httpRequest HTTP servlet request
     * @return confirmation response
     */
    @PostMapping("/reset-password/confirm")
    public ResponseEntity<ApiResponse<Void>> resetPasswordConfirm(
            @Valid @RequestBody ResetPasswordConfirmRequest request,
            HttpServletRequest httpRequest) {
        authService.resetPasswordConfirm(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password updated"));
    }

    /**
     * Changes the password for the currently authenticated user.
     *
     * @param request change password request
     * @param httpRequest HTTP servlet request
     * @param principal authenticated user principal
     * @return confirmation response
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody com.vplmqa.auth.dto.ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            java.security.Principal principal) {
        authService.changePassword(request, principal.getName(), httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password successfully changed"));
    }
}
