-- onebot — 用正向 WebSocket 连接 OneBot v11，把一个 QQ 群和游戏聊天接起来。
--
-- 用法：
--   1. 在 NapCat / LLOneBot / Lagrange / go-cqhttp 里打开「正向 WebSocket」。
--   2. 把整个 onebot 目录复制到
--      plugins/HuHoBotPenguin-NukkitPlatform/addons/onebot/
--   3. 改生成出来的 addons/config/onebot.json（不用改这份脚本）。
--   4. /huhobot scripts reload onebot
--
-- 依赖都在运行环境里：WebSocket 用 JDK 17 的 java.net.http，JSON 用服务端自带的 Gson。
-- 不需要 engines/ 里的额外 jar。
--
-- 群里发 /在线 会回当前在线玩家。机器人自己发出的消息不会再转发回游戏。

-- config 是加载器按 _conf_schema.json 注入的。getList 返回 Java List，转成 Lua 表。
local function config_groups()
    local result = {}
    local list = config:getList("groups")
    if list == nil then return result end
    for i = 0, list:size() - 1 do
        result[#result + 1] = tonumber(tostring(list:get(i))) or tostring(list:get(i))
    end
    return result
end

local CONFIG = {
    url = config:getString("url"),
    token = config:getString("token"),
    groups = config_groups(),
    reconnect = config:get("reconnect") ~= false,
    reconnect_seconds = tonumber(tostring(config:get("reconnect_seconds"))) or 5,
    game_to_qq = config:get("game_to_qq") ~= false,
    qq_to_game = config:get("qq_to_game") ~= false,
    qq_prefix = config:getString("qq_prefix"),
    game_prefix = config:getString("game_prefix"),
}

local HttpClient = java.import("java.net.http.HttpClient")
local URI = java.import("java.net.URI")
local Gson = java.import("com.google.gson.Gson")
local JavaMap = java.import("java.util.Map")
local Runnable = java.import("java.lang.Runnable")

local gson = java.new(Gson)
local http = HttpClient:newHttpClient()

local alive = false
local connecting = false
local socket = nil
local buffer = ""
local self_id = nil
local groups = {}

for _, id in ipairs(CONFIG.groups) do
    groups[tostring(id)] = true
end

local function log(text)
    logger:info("[onebot] " .. tostring(text))
end

local function warn(text)
    logger:warning("[onebot] " .. tostring(text))
end

-- WebSocket 回调不在主线程。碰玩家、广播、调度器都要切回来。
local function sync(fn)
    server:getScheduler():scheduleTask(plugin, java.proxy(Runnable, {
        run = function()
            local ok, err = pcall(fn)
            if not ok then warn(err) end
        end
    }))
end

local function later(seconds, fn)
    server:getScheduler():scheduleDelayedTask(plugin, java.proxy(Runnable, {
        run = function()
            local ok, err = pcall(fn)
            if not ok then warn(err) end
        end
    }), math.floor(seconds * 20))
end

-- Gson 把 JSON 数字解成 Double。QQ 号超过 1e7 时 Double:toString() 会变成
-- 1.23456789E9，所以能取到 doubleValue 时一律按整数格式化。
local function id_string(value)
    if value == nil then return "" end
    if type(value) == "number" then return string.format("%.0f", value) end
    if type(value) == "string" then return value end
    local ok, number = pcall(function() return value:doubleValue() end)
    if ok and type(number) == "number" then return string.format("%.0f", number) end
    local raw_ok, raw = pcall(function() return value:toString() end)
    return raw_ok and tostring(raw) or tostring(value)
end

local function json_escape(text)
    text = tostring(text)
    text = text:gsub("\\", "\\\\")
    text = text:gsub('"', '\\"')
    text = text:gsub("\r", "\\r")
    text = text:gsub("\n", "\\n")
    text = text:gsub("\t", "\\t")
    return text
end

local function strip_cq(text)
    return (tostring(text):gsub("%[CQ:[^%]]+%]", ""))
end

local function group_allowed(group_id)
    if next(groups) == nil then return true end
    return groups[id_string(group_id)] == true
end

local function send_raw(payload)
    local current = socket
    if current == nil then
        warn("还没连上，丢弃: " .. payload)
        return false
    end
    local ok, err = pcall(function()
        current:sendText(payload, true)
    end)
    if not ok then
        warn("发送失败: " .. tostring(err))
        return false
    end
    return true
end

local function call_action(action, params, echo)
    send_raw(string.format(
        '{"action":"%s","params":{%s},"echo":"%s"}',
        json_escape(action), params, json_escape(echo)))
end

local function send_group(group_id, text)
    call_action("send_group_msg", string.format(
        '"group_id":%s,"message":"%s"',
        id_string(group_id), json_escape(text)), "send")
end

local function segment_text(segment)
    local ok, text = pcall(function()
        local kind = id_string(segment:get("type"))
        local data = segment:get("data")
        if kind == "text" and data ~= nil then
            return id_string(data:get("text"))
        elseif kind == "image" then
            return "[图片]"
        elseif kind == "at" and data ~= nil then
            return "@" .. id_string(data:get("qq"))
        elseif kind == "face" then
            return "[表情]"
        end
        return ""
    end)
    return ok and text or ""
end

-- message 可能是字符串，也可能是消息段数组。有 raw_message 时优先用它。
local function message_text(event)
    local raw = event:get("raw_message")
    if raw ~= nil then return strip_cq(id_string(raw)) end
    local message = event:get("message")
    if message == nil then return "" end
    if type(message) == "string" then return message end
    local ok, text = pcall(function()
        local name = message:getClass():getName()
        if name == "java.lang.String" then return message:toString() end
        local parts = {}
        local size = message:size()
        for i = 0, size - 1 do
            parts[#parts + 1] = segment_text(message:get(i))
        end
        return table.concat(parts)
    end)
    if ok then return tostring(text) end
    return ""
end

local function sender_name(event)
    local sender = event:get("sender")
    if sender == nil then return id_string(event:get("user_id")) end
    local card = sender:get("card")
    if card ~= nil and id_string(card) ~= "" then return id_string(card) end
    local nickname = sender:get("nickname")
    if nickname ~= nil and id_string(nickname) ~= "" then return id_string(nickname) end
    return id_string(event:get("user_id"))
end

-- Nukkit 有的版本返回 Collection，有的返回 Map。两种都试。
local function each_player(fn)
    local players = server:getOnlinePlayers()
    local ok, it = pcall(function() return players:iterator() end)
    if not ok then
        ok, it = pcall(function() return players:values():iterator() end)
    end
    if not ok then return end
    while it:hasNext() do fn(it:next()) end
end

local function online_names()
    local names = {}
    each_player(function(player)
        names[#names + 1] = player:getName()
    end)
    table.sort(names)
    if #names == 0 then return 0, "没有人在线" end
    return #names, table.concat(names, ", ")
end

local function on_group_message(event)
    local group_id = event:get("group_id")
    if not group_allowed(group_id) then return end
    local user_id = id_string(event:get("user_id"))
    if self_id ~= nil and user_id == self_id then return end

    local text = message_text(event)
    if text == "" then return end
    local trimmed = (text:gsub("^%s+", ""):gsub("%s+$", ""))

    if trimmed == "/在线" then
        local count, names = online_names()
        send_group(group_id, "在线 " .. count .. " 人: " .. names)
        return
    end

    if not CONFIG.qq_to_game then return end
    local line = CONFIG.qq_prefix .. sender_name(event) .. ": " .. text
    server:broadcastMessage(line)
end

local function on_api_result(event)
    if id_string(event:get("echo")) ~= "login" then return end
    local data = event:get("data")
    if data == nil then return end
    self_id = id_string(data:get("user_id"))
    log("登录成功: " .. id_string(data:get("nickname")) .. " (" .. self_id .. ")")
    server:broadcastMessage(CONFIG.qq_prefix .. "机器人已连接")
end

local function handle(text)
    local ok, event = pcall(function()
        return gson:fromJson(text, JavaMap)
    end)
    if not ok or event == nil then
        warn("JSON 解析失败: " .. tostring(event))
        return
    end
    local post = id_string(event:get("post_type"))
    if post == "message" and id_string(event:get("message_type")) == "group" then
        on_group_message(event)
    elseif post == "meta_event" and id_string(event:get("meta_event_type")) == "lifecycle" then
        local sid = event:get("self_id")
        if sid ~= nil then self_id = id_string(sid) end
        call_action("get_login_info", "", "login")
    elseif event:get("echo") ~= nil then
        on_api_result(event)
    end
end

local function schedule_reconnect()
    if not alive or not CONFIG.reconnect or connecting then return end
    log(CONFIG.reconnect_seconds .. " 秒后重连")
    later(CONFIG.reconnect_seconds, function()
        if alive then connect() end
    end)
end

local listener = java.proxy("java.net.http.WebSocket$Listener", {
    onOpen = function(_, webSocket)
        webSocket:request(1)
        sync(function()
            socket = webSocket
            connecting = false
            buffer = ""
            log("已连接 " .. CONFIG.url)
            call_action("get_login_info", "", "login")
        end)
    end,

    onText = function(_, webSocket, data, last)
        local chunk = data:toString()
        sync(function()
            buffer = buffer .. chunk
            if last then
                local whole = buffer
                buffer = ""
                handle(whole)
            end
        end)
        webSocket:request(1)
    end,

    onClose = function(_, _, code, reason)
        sync(function()
            socket = nil
            connecting = false
            warn("连接关闭 " .. tostring(code) .. " " .. tostring(reason))
            schedule_reconnect()
        end)
    end,

    onError = function(_, _, error)
        local message = tostring(error)
        local ok, detail = pcall(function() return error:getMessage() end)
        if ok and detail ~= nil then message = tostring(detail) end
        sync(function()
            connecting = false
            warn("连接错误: " .. message)
            -- onError 之后通常还会 onClose，由那边负责重连。
        end)
    end,
})

function connect()
    if not alive or connecting then return end
    connecting = true
    socket = nil
    buffer = ""
    log("正在连接 " .. CONFIG.url)
    local ok, err = pcall(function()
        local builder = http:newWebSocketBuilder()
        if CONFIG.token ~= "" then
            builder = builder:header("Authorization", "Bearer " .. CONFIG.token)
        end
        builder:buildAsync(URI:create(CONFIG.url), listener)
    end)
    if not ok then
        connecting = false
        warn("无法发起连接: " .. tostring(err))
        schedule_reconnect()
    end
end

function onEnable(_)
    alive = true
    connect()
end

function onPlayerChat(event)
    if not CONFIG.game_to_qq or socket == nil then return end
    if next(groups) == nil then return end
    local text = tostring(event:getMessage())
    if text:sub(1, #CONFIG.qq_prefix) == CONFIG.qq_prefix then return end
    local name = event:getPlayer():getName()
    local line = CONFIG.game_prefix .. name .. ": " .. text
    for id in pairs(groups) do
        send_group(id, line)
    end
end

function onDisable(_)
    alive = false
    local current = socket
    socket = nil
    if current ~= nil then
        pcall(function() current:sendClose(1000, "unload") end)
    end
    log("已断开")
end
