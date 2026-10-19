-- 单用户 Hash 内原子比较与删除；不跨用户、不跨窗口，不刷新剩余购物车的 TTL。
local key = KEYS[1]
local windowId = ARGV[1]
local ok, submitted = pcall(cjson.decode, ARGV[2])
if not ok or type(submitted) ~= 'table' or #submitted == 0 or #submitted > 100 then
    return -4
end

local fields = {}
local seen = {}
for _, snapshot in ipairs(submitted) do
    -- 所有检查必须在首次写入前完成；Redis Lua 发生错误不会回滚之前执行的删除。
    if type(snapshot) ~= 'table' or type(snapshot.productId) ~= 'string'
            or type(snapshot.revision) ~= 'string' or type(snapshot.quantity) ~= 'number' then
        return -4
    end
    local field = windowId .. ':' .. snapshot.productId
    if seen[field] then
        return -4
    end
    seen[field] = true
    local raw = redis.call('HGET', key, field)
    if raw then
        local decoded, item = pcall(cjson.decode, raw)
        if not decoded or type(item) ~= 'table' or type(item.revision) ~= 'string'
                or type(item.quantity) ~= 'number' or item.quantity < 1 or item.quantity % 1 ~= 0
                or type(item.checked) ~= 'boolean' or tostring(item.windowId) ~= windowId
                or tostring(item.productId) ~= snapshot.productId then
            return -4
        end
        -- 新增、改数量、改勾选和删除重建都会更新 revision，旧快照不能删掉新意图。
        if item.revision == snapshot.revision and item.quantity == snapshot.quantity and item.checked then
            fields[#fields + 1] = field
        end
    end
end

-- 先收集并验证全部候选，再统一删除；空列表不写 Redis，也不创建空购物车。
if #fields == 0 then
    return 0
end
return redis.call('HDEL', key, unpack(fields))
