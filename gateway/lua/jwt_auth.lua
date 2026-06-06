local security = require "browser_security"
local token_verifier = require "rs256_token"
local session_status = require "session_status"

local function unauthorized(message)
    ngx.status = ngx.HTTP_UNAUTHORIZED
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"code":"SECURITY_TOKEN_INVALID","numericCode":5102,"message":"' .. (message or "Invalid bearer token") ..
        '","fieldErrors":[]}')
    return ngx.exit(ngx.HTTP_UNAUTHORIZED)
end

local function access_token()
    local auth = ngx.var.http_authorization
    if auth and auth ~= "" then
        local match = auth:match("^[Bb]earer%s+(.+)$")
        if match then
            return match
        end
    end

    return security.cookie_value("access_token")
end

if ngx.req.get_method() == "OPTIONS" then
    return
end

if not security.enforce_csrf() then
    return
end

local token = access_token()
if not token or token == "" then
    return unauthorized("Missing access token")
end

local payload = token_verifier.verify(token)
if not payload then
    return unauthorized("Invalid bearer token")
end

local issuer = os.getenv("IDENTITY_JWT_ISSUER") or "account-service"
local audience = os.getenv("IDENTITY_JWT_AUDIENCE") or "account"
if payload.iss ~= issuer then
    return unauthorized("Invalid token issuer")
end

local aud = payload.aud
local audience_ok = aud == audience
if type(aud) == "table" then
    for _, value in ipairs(aud) do
        if value == audience then
            audience_ok = true
            break
        end
    end
end
if not audience_ok then
    return unauthorized("Invalid token audience")
end

local expires_at = tonumber(payload.exp)
if not expires_at or expires_at <= ngx.time() then
    return unauthorized("Expired bearer token")
end
if not payload.sub or payload.sub == "" then
    return unauthorized("Token subject is required")
end
if not session_status.require_active(payload.sid, payload.sub, expires_at) then
    return unauthorized("Session is revoked or unavailable")
end

ngx.var.auth_client_id = payload.sub
ngx.var.auth_expires_at = tostring(expires_at)
