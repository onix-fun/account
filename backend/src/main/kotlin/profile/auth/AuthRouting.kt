package profile.auth

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import profile.sessions.SessionController

fun Route.authRouting(authController: AuthController, sessionController: SessionController) {
    route("/api/auth") {
        post("/register", {
            tags = setOf("Auth")
            summary = "Register a new user"
            description = "Starts a pending registration and sends a verification code"
            request { body<RegisterRequest> { description = "Registration details" } }
            response { code(HttpStatusCode.Accepted) { description = "Registration verification code sent"; body<RegistrationStartedResponse> { } } }
        }) { authController.register(call) }

        post("/confirm-registration", {
            tags = setOf("Auth")
            summary = "Confirm registration"
            description = "Creates the user account after the email verification code is confirmed and starts a session"
            request { body<ConfirmRegistrationRequest> { description = "Email and registration code" } }
            response { code(HttpStatusCode.Created) { description = "User created and logged in"; body<BrowserAuthResponse> { } } }
        }) { authController.confirmRegistration(call) }

        post("/resend-registration-code", {
            tags = setOf("Auth")
            summary = "Resend registration code"
            description = "Sends a new code for a pending registration"
            request { body<ResendRegistrationCodeRequest> { description = "Pending registration email" } }
            response { code(HttpStatusCode.OK) { description = "Registration code resent"; body<RegistrationStartedResponse> { } } }
        }) { authController.resendRegistrationCode(call) }

        post("/login", {
            tags = setOf("Auth")
            summary = "Login"
            description = "Authenticates a browser user and sets HttpOnly session cookies"
            request { body<LoginRequest> { description = "Login credentials" } }
            response { code(HttpStatusCode.OK) { description = "Login successful"; body<BrowserAuthResponse> { } } }
        }) { authController.login(call) }

        post("/token", {
            tags = setOf("Auth")
            summary = "Issue API tokens"
            description = "Authenticates an API client and returns a Bearer access token plus opaque refresh token"
            request { body<LoginRequest> { description = "API client credentials" } }
            response { code(HttpStatusCode.OK) { description = "Token pair issued"; body<ApiTokenResponse> { } } }
        }) { authController.token(call) }

        post("/token/refresh", {
            tags = setOf("Auth")
            summary = "Refresh API tokens"
            description = "Rotates an API refresh token and returns a new token pair"
            request { body<TokenRefreshRequest> { description = "Opaque API refresh token" } }
            response { code(HttpStatusCode.OK) { description = "Token pair rotated"; body<ApiTokenResponse> { } } }
        }) { authController.tokenRefresh(call) }

        get("/csrf", {
            tags = setOf("Auth")
            summary = "Initialize browser CSRF protection"
            response { code(HttpStatusCode.OK) { description = "CSRF token issued"; body<CsrfResponse> { } } }
        }) { authController.csrf(call) }

        get("/username-available", {
            tags = setOf("Auth")
            summary = "Check username availability"
            request { queryParameter<String>("username") { description = "Username to check" } }
            response { code(HttpStatusCode.OK) { description = "Username availability"; body<UsernameAvailabilityResponse> { } } }
        }) { authController.usernameAvailable(call) }

        get("/account-lookup", {
            tags = setOf("Auth")
            summary = "Lookup account before password entry"
            request { queryParameter<String>("identifier") { description = "Email or username" } }
            response { code(HttpStatusCode.OK) { body<AccountLookupResponse> { } } }
        }) { authController.accountLookup(call) }

        post("/public-verification/request", {
            tags = setOf("Auth")
            summary = "Send verification code before login"
            request { body<PublicVerificationRequest> { } }
            response { code(HttpStatusCode.OK) { description = "Code sent" } }
        }) { authController.requestPublicVerification(call) }

        post("/public-verification/confirm", {
            tags = setOf("Auth")
            summary = "Confirm email before login"
            request { body<PublicVerificationConfirmRequest> { } }
            response { code(HttpStatusCode.OK) { description = "Email verified" } }
        }) { authController.confirmPublicVerification(call) }

        get("/accounts", {
            tags = setOf("Auth")
            summary = "List browser accounts"
            response { code(HttpStatusCode.OK) { description = "Available browser accounts"; body<List<BrowserAccountDto>> { } } }
        }) { authController.accounts(call) }

        post("/switch", {
            tags = setOf("Auth")
            summary = "Switch browser account"
            request { body<SwitchAccountRequest> { description = "Account to activate" } }
            response { code(HttpStatusCode.OK) { description = "Account activated"; body<BrowserAuthResponse> { } } }
            }) { authController.switchAccount(call) }

            authenticate {
                post("/owner/switch", {
                    tags = setOf("Auth")
                    securitySchemeNames("BearerToken")
                    summary = "Switch active owner"
                    response { code(HttpStatusCode.OK) { description = "Active owner switched"; body<BrowserAuthResponse> { } } }
                }) { authController.switchActiveOwner(call) }
            }

        post("/refresh", {
            tags = setOf("Auth")
            summary = "Refresh browser session"
            description = "Uses the active account refresh cookie to rotate browser session cookies"
            response { code(HttpStatusCode.OK) { description = "Browser session refreshed"; body<BrowserAuthResponse> { } } }
        }) { authController.refresh(call) }

        post("/qr/consume", {
            tags = setOf("Auth")
            summary = "Consume QR login code"
            description = "Consumes a QR login scan token or manual code and starts a browser session"
            request { body<QrLoginConsumeRequest> { description = "QR token or manual code" } }
            response { code(HttpStatusCode.OK) { description = "QR login successful"; body<BrowserAuthResponse> { } } }
        }) { authController.consumeQrChallenge(call) }

        authenticate {
            post("/qr/challenges", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Create QR login challenge"
                description = "Creates a short-lived one-time QR login challenge for the authenticated account"
                response { code(HttpStatusCode.Created) { description = "QR login challenge"; body<QrLoginChallengeCreatedResponse> { } } }
            }) { authController.createQrChallenge(call) }

            get("/qr/challenges/{id}", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Get QR login challenge status"
                request { pathParameter<String>("id") { description = "QR challenge UUID" } }
                response { code(HttpStatusCode.OK) { description = "QR login challenge status"; body<QrLoginChallengeStatusResponse> { } } }
            }) { authController.getQrChallenge(call) }

            delete("/qr/challenges/{id}", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Cancel QR login challenge"
                request { pathParameter<String>("id") { description = "QR challenge UUID" } }
                response { code(HttpStatusCode.OK) { description = "QR login challenge cancelled"; body<QrLoginChallengeStatusResponse> { } } }
            }) { authController.cancelQrChallenge(call) }

            post("/verify-email", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Verify email"
                description = "Verifies user email with a code"
                request { body<VerifyEmailRequest> { description = "Verification code" } }
                response { code(HttpStatusCode.OK) { description = "Email verified" } }
            }) { authController.verifyEmail(call) }

            post("/resend-verification", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Resend verification code"
                description = "Resends email verification code"
                response { code(HttpStatusCode.OK) { description = "Verification code resent" } }
            }) { authController.resendVerification(call) }

            post("/logout-all", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Logout from all devices"
                description = "Revokes all refresh tokens and clears cookie"
                response { code(HttpStatusCode.OK) { description = "Logged out from all devices" } }
            }) { authController.logoutAll(call) }

            post("/change-password", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Change password"
                description = "Changes password using the current password and revokes existing sessions"
                request { body<ChangePasswordRequest> { description = "Current and new password" } }
                response { code(HttpStatusCode.OK) { description = "Password changed" } }
            }) { authController.changePassword(call) }

            delete("/account", {
                tags = setOf("Auth")
                securitySchemeNames("BearerToken")
                summary = "Delete current account"
                description = "Deletes the authenticated account after password confirmation"
                request { body<DeleteAccountRequest> { description = "Password confirmation" } }
                response { code(HttpStatusCode.OK) { description = "Account deleted" } }
            }) { authController.deleteAccount(call) }
        }

        post("/forgot-password", {
            tags = setOf("Auth")
            summary = "Forgot password"
            description = "Sends password reset email"
            request { body<ForgotPasswordRequest> { description = "Email address" } }
            response { code(HttpStatusCode.OK) { description = "Reset email sent if account exists" } }
        }) { authController.forgotPassword(call) }

        post("/reset-password", {
            tags = setOf("Auth")
            summary = "Reset password"
            description = "Resets password using reset token"
            request { body<ResetPasswordRequest> { description = "Reset token and new password" } }
            response { code(HttpStatusCode.OK) { description = "Password reset successfully" } }
        }) { authController.resetPassword(call) }

        post("/logout", {
            tags = setOf("Auth")
            summary = "Logout"
            description = "Revokes refresh token and clears cookie"
            response { code(HttpStatusCode.OK) { description = "Logged out" } }
        }) { authController.logout(call) }
    }
}
