package.path = (os.getenv("PWD") or ".") .. "/lua/?.lua;" .. package.path

local stored = { ["profile:session:active"] = "user-1" }
package.preload["resty.redis"] = function()
    return { new = function()
        return {
            set_timeout = function() end,
            connect = function() return true end,
            get = function(_, key) return stored[key] end,
            setex = function(_, key, _, value) stored[key] = value end,
            set_keepalive = function() end
        }
    end }
end

ngx = {
    time = function() return 100 end,
    location = { capture = function(_, options)
        if options.args.sid == "fallback" and options.args.uid == "user-1" then return { status = 204 } end
        return { status = 401 }
    end }
}

local status = require "session_status"
assert(status.require_active("active", "user-1", 200))
assert(status.require_active("fallback", "user-1", 200))
assert(not status.require_active("revoked", "user-1", 200))
print("account session status tests passed")
