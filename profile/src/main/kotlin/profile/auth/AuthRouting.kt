package profile.auth

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
            response { code(HttpStatusCode.Created) { description = "User created and logged in"; body<AuthResponse> { } } }
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
            description = "Authenticates user, returns access token, sets refresh token cookie"
            request { body<LoginRequest> { description = "Login credentials" } }
            response { code(HttpStatusCode.OK) { description = "Login successful"; body<AuthResponse> { } } }
        }) { authController.login(call) }

        post("/refresh", {
            tags = setOf("Auth")
            summary = "Refresh access token"
            description = "Uses refresh token from cookie to issue a new access token"
            response { code(HttpStatusCode.OK) { description = "New access token issued" } }
        }) { authController.refresh(call) }

        authenticate {
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
