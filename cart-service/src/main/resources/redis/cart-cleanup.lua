-- 单用户 Hash 内原子比较与删除；不跨用户、不跨窗口，不刷新剩余购物车的 TTL。
-- 先验证下单快照与 Redis 行，再统一删除，避免并发修改或规格键错误时误删其它规格。
local key = KEYS[1]
local windowId = ARGV[1]
local ok, submitted = pcall(cjson.decode, ARGV[2])
if not ok or type(submitted) ~= 'table' or #submitted == 0 or #submitted > 100 then
    return -4
end

local function validSelectionKey(candidate)
    if candidate == 'base' then
        return true
    end
    if type(candidate) ~= 'string' or string.len(candidate) == 0 or string.len(candidate) > 210
            or string.sub(candidate, 1, 1) == '-' or string.sub(candidate, -1) == '-'
            or string.find(candidate, '--', 1, true) ~= nil then
        return false
    end
    local count = 0
    local previous = nil
    for part in string.gmatch(candidate, '[^-]+') do
        count = count + 1
        if count > 10 or string.match(part, '^[1-9][0-9]*$') == nil then
            return false
        end
        if previous ~= nil and (string.len(previous) > string.len(part)
                or (string.len(previous) == string.len(part) and previous >= part)) then
            return false
        end
        previous = part
    end
    return count > 0
end

local function expectedField(rowWindowId, rowProductId, rowSelectionKey)
    local base = rowWindowId .. ':' .. rowProductId
    if rowSelectionKey == 'base' then
        return base
    end
    return base .. ':' .. rowSelectionKey
end

local fields = {}
local seen = {}
for _, snapshot in ipairs(submitted) do
    -- 商品主键使用十进制字符串；revision、数量及规格键须有明确值，避免 cjson 类型转换绕过核验。
    if type(snapshot) ~= 'table' or type(snapshot.productId) ~= 'string'
            or string.match(snapshot.productId, '^[1-9][0-9]*$') == nil
            or type(snapshot.revision) ~= 'string' or string.len(snapshot.revision) == 0
            or type(snapshot.quantity) ~= 'number' or snapshot.quantity < 1 or snapshot.quantity % 1 ~= 0 then
        return -4
    end
    local selectionKey = snapshot.selectionKey
    if selectionKey == nil or selectionKey == cjson.null then
        -- 兼容旧前端下单快照：没有 selectionKey 的订单只能清理 base 菜品行。
        selectionKey = 'base'
    end
    if not validSelectionKey(selectionKey) then
        return -4
    end
    local field = expectedField(windowId, snapshot.productId, selectionKey)
    if seen[field] then
        return -4
    end
    seen[field] = true
    local raw = redis.call('HGET', key, field)
    if raw then
        -- 先完整核验储存 JSON、规格键和 Field 对应关系；数据错误会中止整批清理。
        local decoded, item = pcall(cjson.decode, raw)
        if not decoded or type(item) ~= 'table' or type(item.revision) ~= 'string'
                or string.len(item.revision) == 0
                or type(item.quantity) ~= 'number' or item.quantity < 1 or item.quantity % 1 ~= 0
                or type(item.checked) ~= 'boolean' or tostring(item.windowId) ~= windowId
                or tostring(item.productId) ~= snapshot.productId then
            return -4
        end
        local storedSelectionKey = item.selectionKey
        if storedSelectionKey == nil or storedSelectionKey == cjson.null then
            -- 旧 Redis JSON 缺字段时与旧 Hash Field 一样按 base 解释。
            storedSelectionKey = 'base'
        end
        if not validSelectionKey(storedSelectionKey)
                or storedSelectionKey ~= selectionKey
                or field ~= expectedField(windowId, snapshot.productId, storedSelectionKey) then
            return -4
        end
        -- 加入、增减数量及勾选都换 revision；只有和原下单快照完全一致且已勾选才清理。
        if item.revision == snapshot.revision and item.quantity == snapshot.quantity and item.checked then
            fields[#fields + 1] = field
        end
    end
end

-- 全量校验结束后才删除；缺失或已变化的行被安全跳过，不会误删同菜的其它规格。
if #fields == 0 then
    return 0
end
return redis.call('HDEL', key, unpack(fields))
