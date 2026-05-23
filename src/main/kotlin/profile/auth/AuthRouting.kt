package profile.auth

import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.routing.*
import profile.infrastructure.db.User
import profile.sessions.SessionController

fun Route.authRouting(authController: AuthController, sessionController: SessionController) {
    route("/api/auth") {
        post("/register", {
            summary = "Register a new user"
            description = "Creates a new user account"
            request { body<RegisterRequest> { description = "Registration details" } }
            response { code(HttpStatusCode.Created) { description = "User created"; body<User> { } } }
        }) { authController.register(call) }

        post("/login", {
            summary = "Login"
            description = "Authenticates user, returns access token, sets refresh token cookie"
            request { body<LoginRequest> { description = "Login credentials" } }
            response { code(HttpStatusCode.OK) { description = "Login successful"; body<AuthResponse> { } } }
        }) { authController.login(call) }

        post("/refresh", {
            summary = "Refresh access token"
            description = "Uses refresh token from cookie to issue a new access token"
            response { code(HttpStatusCode.OK) { description = "New access token issued" } }
        }) { authController.refresh(call) }

        post("/verify-email", {
            summary = "Verify email"
            description = "Verifies user email with a code"
            request { body<VerifyEmailRequest> { description = "Verification code" } }
            response { code(HttpStatusCode.OK) { description = "Email verified" } }
        }) { authController.verifyEmail(call) }

        post("/resend-verification", {
            summary = "Resend verification code"
            description = "Resends email verification code"
            response { code(HttpStatusCode.OK) { description = "Verification code resent" } }
        }) { authController.resendVerification(call) }

        post("/forgot-password", {
            summary = "Forgot password"
            description = "Sends password reset email"
            request { body<ForgotPasswordRequest> { description = "Email address" } }
            response { code(HttpStatusCode.OK) { description = "Reset email sent if account exists" } }
        }) { authController.forgotPassword(call) }

        post("/reset-password", {
            summary = "Reset password"
            description = "Resets password using reset token"
            request { body<ResetPasswordRequest> { description = "Reset token and new password" } }
            response { code(HttpStatusCode.OK) { description = "Password reset successfully" } }
        }) { authController.resetPassword(call) }

        post("/logout", {
            summary = "Logout"
            description = "Revokes refresh token and clears cookie"
            response { code(HttpStatusCode.OK) { description = "Logged out" } }
        }) { authController.logout(call) }

        post("/logout-all", {
            summary = "Logout from all devices"
            description = "Revokes all refresh tokens and clears cookie"
            response { code(HttpStatusCode.OK) { description = "Logged out from all devices" } }
        }) { authController.logoutAll(call) }
    }
}
