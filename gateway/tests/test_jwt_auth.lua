package.path = (os.getenv("PWD") or ".") .. "/lua/?.lua;" .. package.path

local verified_token = nil
package.preload["session_status"] = function()
    return { require_active = function(session_id, user_id) return session_id == "session-id" and user_id == "client-id" end }
end
package.preload["rs256_token"] = function()
    return {
        verify = function(token)
            verified_token = token
            local payload = {
                iss = "account-service",
                aud = "account",
                exp = 200,
                sub = "client-id"
                ,sid = "session-id"
            }
            if token == "bad-issuer" then payload.iss = "other" end
            if token == "bad-audience" then payload.aud = "other" end
            if token == "expired" then payload.exp = 99 end
            if token == "invalid" then return nil end
            return payload
        end
    }
end

local last_exit = nil
local last_message = nil
ngx = {
    HTTP_FORBIDDEN = 403,
    HTTP_UNAUTHORIZED = 401,
    status = 200,
    header = {},
    var = {},
    req = {
        get_method = function() return "GET" end
    },
    say = function(message) last_message = message end,
    exit = function(status) last_exit = status end,
    time = function() return 100 end
}

local jwt_auth_path = (os.getenv("PWD") or ".") .. "/lua/jwt_auth.lua"
local function reset()
    last_exit = nil
    verified_token = nil
    ngx.status = 200
    ngx.header = {}
    ngx.var = {}
    last_message = nil
end

reset()
ngx.var.http_cookie = "access_token=cookie-token"
dofile(jwt_auth_path)
assert(last_exit == nil)
assert(verified_token == "cookie-token")
assert(ngx.var.auth_client_id == "client-id")
assert(ngx.var.auth_expires_at == "200")

reset()
ngx.var.http_authorization = "Bearer bearer-token"
ngx.var.http_cookie = "access_token=cookie-token"
dofile(jwt_auth_path)
assert(last_exit == nil)
assert(verified_token == "bearer-token")

for _, token in ipairs({ "invalid", "bad-issuer", "bad-audience", "expired" }) do
    reset()
    ngx.var.http_authorization = "Bearer " .. token
    dofile(jwt_auth_path)
    assert(last_exit == 401)
    assert(last_message:match('"code":"SECURITY_TOKEN_INVALID"'))
    assert(last_message:match('"numericCode":5102'))
end

reset()
ngx.var.arg_access_token = "query-token"
dofile(jwt_auth_path)
assert(last_exit == 403)
assert(verified_token == nil)

print("account jwt_auth tests passed")
