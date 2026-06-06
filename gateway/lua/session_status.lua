local _M = {}
local redis = require "resty.redis"

function _M.require_active(session_id, user_id, expires_at)
    if not session_id or session_id == "" then return false end
    local client = redis:new()
    client:set_timeout(250)
    local ok = client:connect(os.getenv("IDENTITY_REDIS_HOST") or "account-redis", tonumber(os.getenv("IDENTITY_REDIS_PORT") or "6379"))
    if ok then
        local value = client:get("profile:session:" .. session_id)
        if value == user_id then client:set_keepalive(10000, 20); return true end
    end
    local response = ngx.location.capture("/_internal/session-check", { args = { sid = session_id, uid = user_id } })
    if not response or response.status ~= 204 then return false end
    if ok then
        client:setex("profile:session:" .. session_id, math.max(1, expires_at - ngx.time()), user_id)
        client:set_keepalive(10000, 20)
    end
    return true
end

return _M
