package.path = (os.getenv("PWD") or ".") .. "/lua/?.lua;" .. package.path

local last_message = nil
local last_exit = nil
local original_getenv = os.getenv
os.getenv = function(name)
    if name == "ACCOUNT_ALLOWED_ORIGINS" then
        return "https://account.example.com,https://app.example.com"
    end
    return original_getenv(name)
end

ngx = {
    HTTP_FORBIDDEN = 403,
    status = 200,
    header = {},
    var = {},
    req = {
        get_method = function() return "POST" end
    },
    say = function(message) last_message = message end,
    exit = function(status) last_exit = status end
}

local security = require "browser_security"

assert(security.is_allowed_origin("http://localhost:5174"))
assert(security.is_allowed_origin("https://account.example.com"))
assert(security.is_allowed_origin("https://app.example.com"))
assert(not security.is_allowed_origin("https://example.com.evil.test"))
assert(not security.is_allowed_origin("https://evil.test"))
assert(not security.is_allowed_origin("http://account.example.com"))

ngx.var.http_origin = "https://account.example.com"
ngx.var.http_cookie = "csrf_token=expected"
ngx.var.http_x_csrf_token = "expected"
assert(security.enforce_csrf())

ngx.var.http_x_csrf_token = "wrong"
assert(not security.enforce_csrf())
assert(last_exit == 403)
assert(last_message:match("Valid CSRF token"))
assert(last_message:match('"code":"SECURITY_CSRF_INVALID"'))
assert(last_message:match('"numericCode":5100'))
-- requestId removed from error responses

ngx.var.http_authorization = "Bearer test"
assert(security.enforce_csrf())

ngx.var.arg_access_token = "query-token"
assert(not security.reject_query_token())
assert(last_exit == 403)
assert(last_message:match('"code":"SECURITY_TOKEN_INVALID"'))

print("account browser_security tests passed")
