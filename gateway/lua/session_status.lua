local _M = {}
local redis = require "resty.redis"
local uuid_pattern = "^%x%x%x%x%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%x%x%x%x%x%x%x%x$"

local function is_valid_uuid(value)
    if not value then return false end
    return value:match(uuid_pattern) ~= nil
end

local function release(client)
    if not client then return end
    client:set_keepalive(
        tonumber(os.getenv("IDENTITY_REDIS_KEEPALIVE_MS") or "10000"),
        tonumber(os.getenv("IDENTITY_REDIS_POOL_SIZE") or "50")
    )
end

local function connect()
    local client = redis:new()
    client:set_timeout(250)
    local options = {
        pool = "account-session-status",
        pool_size = tonumber(os.getenv("IDENTITY_REDIS_POOL_SIZE") or "50"),
        backlog = tonumber(os.getenv("IDENTITY_REDIS_POOL_BACKLOG") or "100")
    }
    if os.getenv("IDENTITY_REDIS_TLS") == "true" then
        options.ssl = true
        options.ssl_verify = true
    end
    local ok = client:connect(
        os.getenv("IDENTITY_REDIS_HOST") or "account-redis",
        tonumber(os.getenv("IDENTITY_REDIS_PORT") or "6379"),
        options
    )
    if not ok then return nil end
    local password = os.getenv("IDENTITY_REDIS_PASSWORD")
    if password and password ~= "" then
        local authed = client:auth(password)
        if not authed then release(client); return nil end
    end
    return client
end

function _M.require_active(session_id, user_id, expires_at)
    if not session_id or session_id == "" then return false end
    if not is_valid_uuid(session_id) then return false end
    local client = connect()
    if client then
        local value = client:get("profile:session:" .. session_id)
        if value == user_id then release(client); return true end
    end
    local response = ngx.location.capture("/_internal/session-check", { args = { sid = session_id, uid = user_id } })
    if not response or response.status ~= 204 then release(client); return false end
    if client then
        client:setex("profile:session:" .. session_id, math.max(1, expires_at - ngx.time()), user_id)
    end
    release(client)
    return true
end

return _M
