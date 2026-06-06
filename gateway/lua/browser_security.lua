local _M = {}

local function trim(value)
    return (value:gsub("^%s+", ""):gsub("%s+$", ""))
end

local function cookie_value(name)
    local raw = ngx.var.http_cookie
    if not raw or raw == "" then
        return nil
    end

    for item in raw:gmatch("[^;]+") do
        local cookie_name, value = trim(item):match("^([^=]+)=(.*)$")
        if cookie_name == name then
            return value
        end
    end

    return nil
end

local function forbidden(code, numeric_code, message)
    ngx.status = ngx.HTTP_FORBIDDEN
    ngx.header["Content-Type"] = "application/json"
    ngx.say('{"code":"' .. code .. '","numericCode":' .. numeric_code .. ',"message":"' .. message ..
        '","fieldErrors":[]}')
    ngx.exit(ngx.HTTP_FORBIDDEN)
    return false
end

local function constant_time_equals(left, right)
    if not left or not right or #left ~= #right then
        return false
    end

    local different = 0
    for index = 1, #left do
        if left:byte(index) ~= right:byte(index) then
            different = different + 1
        end
    end
    return different == 0
end

function _M.cookie_value(name)
    return cookie_value(name)
end

function _M.is_allowed_origin(origin)
    if not origin or origin == "" then
        return false
    end

    local scheme, authority = origin:match("^(https?)://([^/]+)$")
    if not scheme or not authority then
        return false
    end

    local host = authority:gsub(":%d+$", ""):lower()
    local environment = os.getenv("APP_ENV") or ""
    if environment == "development" and (host == "localhost" or host == "127.0.0.1") then
        return scheme == "http" or scheme == "https"
    end

    local allowed = os.getenv("ACCOUNT_ALLOWED_ORIGINS") or ""
    for candidate in allowed:gmatch("[^,]+") do
        if trim(candidate):lower() == origin:lower() then
            return true
        end
    end
    return false
end

function _M.reject_query_token()
    if ngx.var.arg_access_token and ngx.var.arg_access_token ~= "" then
        return forbidden("SECURITY_TOKEN_INVALID", 5102, "Query access tokens are not supported")
    end
    return true
end

function _M.enforce_csrf()
    if not _M.reject_query_token() then
        return false
    end

    local method = ngx.req.get_method()
    if method ~= "POST" and method ~= "PUT" and method ~= "PATCH" and method ~= "DELETE" then
        return true
    end

    local authorization = ngx.var.http_authorization
    if authorization and authorization:match("^[Bb]earer%s+.+$") then
        return true
    end

    local uri = ngx.var.uri
    if uri == "/api/auth/token" or uri == "/api/auth/token/refresh" then
        return true
    end

    if not _M.is_allowed_origin(ngx.var.http_origin) then
        return forbidden("SECURITY_ORIGIN_INVALID", 5101, "Trusted Origin header is required")
    end

    local header_token = ngx.var.http_x_csrf_token
    local cookie_token = cookie_value("csrf_token")
    if not constant_time_equals(header_token, cookie_token) then
        return forbidden("SECURITY_CSRF_INVALID", 5100, "Valid CSRF token is required")
    end

    return true
end

return _M
