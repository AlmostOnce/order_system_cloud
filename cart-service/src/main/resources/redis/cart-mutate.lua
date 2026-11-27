-- 每次只修改一个顾客 Hash；数据检查、容量/数量校验、写入和 TTL 续期在同一原子脚本中完成。
-- 主键和规格键始终作为字符串比较，避免 Lua/cjson 把 Java BIGINT 选项 ID 转成双精度数后丢精度。
local key = KEYS[1]
local action = ARGV[1]
local field = ARGV[2]
local windowId = ARGV[3]
local productId = ARGV[4]
local selectionKey = ARGV[5]
local value = ARGV[6]
local revision = ARGV[7]
local name = ARGV[8]
local code = ARGV[9]
local specificationText = ARGV[10]
local maxItems = tonumber(ARGV[11])
local maxQuantity = tonumber(ARGV[12])
local ttl = tonumber(ARGV[13])

-- 规格键为 base，或按数值升序排列的正整数字符串，且最多对应十个单选组选项。
-- 比较十进制字符串的长度和字典序，避免把超大选项 ID 转成 Lua number。
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

-- 根据规格身份计算 Redis Field；无规格必须沿用历史 windowId:productId 格式。
local function expectedField(rowWindowId, rowProductId, rowSelectionKey)
    local base = rowWindowId .. ':' .. rowProductId
    if rowSelectionKey == 'base' then
        return base
    end
    return base .. ':' .. rowSelectionKey
end

-- 只解析并验证，不写入 Redis。旧 JSON 缺少 selectionKey 时兼容为 base。
local function decodeAndValidate(raw, expectedWindowId, expectedProductId, expectedSelectionKey, actualField)
    local ok, decoded = pcall(cjson.decode, raw)
    if not ok or type(decoded) ~= 'table' or type(decoded.quantity) ~= 'number'
            or decoded.quantity < 1 or decoded.quantity % 1 ~= 0
            or type(decoded.checked) ~= 'boolean' or type(decoded.revision) ~= 'string'
            or string.len(decoded.revision) == 0
            or tostring(decoded.windowId) ~= expectedWindowId or tostring(decoded.productId) ~= expectedProductId then
        return nil
    end
    local storedSelectionKey = decoded.selectionKey
    if storedSelectionKey == nil or storedSelectionKey == cjson.null then
        storedSelectionKey = 'base'
    end
    if not validSelectionKey(storedSelectionKey)
            or storedSelectionKey ~= expectedSelectionKey
            or actualField ~= expectedField(expectedWindowId, expectedProductId, storedSelectionKey) then
        return nil
    end
    decoded.selectionKey = storedSelectionKey
    if decoded.specificationTextSnapshot == nil or decoded.specificationTextSnapshot == cjson.null then
        decoded.specificationTextSnapshot = ''
    elseif type(decoded.specificationTextSnapshot) ~= 'string' then
        return nil
    end
    return decoded
end

-- 每个由 Java 生成的操作仍在 Lua 验证 Field，避免参数错位时改到同菜的另一规格。
if action ~= 'clearWindow' then
    if not validSelectionKey(selectionKey)
            or field ~= expectedField(windowId, productId, selectionKey) then
        return -4
    end
end

-- 删除不存在行属于幂等成功；存在行必须先验证 JSON 与 Field，不能借删除接口掩盖错位数据。
if action == 'remove' then
    local raw = redis.call('HGET', key, field)
    if not raw then
        return 0
    end
    if not decodeAndValidate(raw, windowId, productId, selectionKey, field) then
        return -4
    end
    local removed = redis.call('HDEL', key, field)
    if removed > 0 and redis.call('EXISTS', key) == 1 then
        redis.call('PEXPIRE', key, ttl)
    end
    return removed
end

-- 清空窗口时先遍历并验证目标窗口所有规格行，再集中删除；损坏行不会造成部分清理。
if action == 'clearWindow' then
    local prefix = windowId .. ':'
    local fields = {}
    for _, itemField in ipairs(redis.call('HKEYS', key)) do
        if string.sub(itemField, 1, string.len(prefix)) == prefix then
            local raw = redis.call('HGET', key, itemField)
            local ok, decoded = pcall(cjson.decode, raw)
            if not ok or type(decoded) ~= 'table' or tostring(decoded.windowId) ~= windowId
                    or type(decoded.productId) ~= 'string' then
                return -4
            end
            local storedSelectionKey = decoded.selectionKey
            if storedSelectionKey == nil or storedSelectionKey == cjson.null then
                storedSelectionKey = 'base'
            end
            if not validSelectionKey(storedSelectionKey)
                    or not decodeAndValidate(raw, windowId, decoded.productId, storedSelectionKey, itemField) then
                return -4
            end
            fields[#fields + 1] = itemField
        end
    end
    if #fields == 0 then
        return 0
    end
    -- 此处才开始写操作。Hash 中单个用户的商品数量受配置限制，逐项删除可避免 unpack 参数上限。
    for _, itemField in ipairs(fields) do
        redis.call('HDEL', key, itemField)
    end
    if redis.call('EXISTS', key) == 1 then
        redis.call('PEXPIRE', key, ttl)
    end
    return #fields
end

local raw = redis.call('HGET', key, field)
local item = nil
if raw then
    -- 原子更新前先验证当前行；revision 与 Field 都必须匹配目标规格。
    item = decodeAndValidate(raw, windowId, productId, selectionKey, field)
    if not item then
        return -4
    end
elseif action ~= 'add' then
    -- PUT/PATCH 不得意外变为新增，不存在时交由 Java 返回 404。
    return -1
end

if action == 'add' then
    if not item then
        -- 容量校验和插入处于同一脚本；并发新增不同规格也不能越过总行数限制。
        if redis.call('HLEN', key) >= maxItems then
            return -2
        end
        item = { windowId = windowId, productId = productId, selectionKey = selectionKey,
                 quantity = 0, checked = true, specificationTextSnapshot = '' }
    end
    local quantity = item.quantity + tonumber(value)
    if quantity < 1 or quantity > maxQuantity then
        return -3
    end
    item.quantity = quantity
    item.productNameSnapshot = name
    item.productCodeSnapshot = code
    item.selectionKey = selectionKey
    item.specificationTextSnapshot = specificationText
elseif action == 'quantity' then
    local quantity = tonumber(value)
    if quantity == nil or quantity < 1 or quantity > maxQuantity then
        return -3
    end
    item.quantity = quantity
elseif action == 'checked' then
    item.checked = value == 'true'
else
    return -4
end

-- 每次实际增改都生成新 revision；下单快照清理因此无法误删其后修改的购物车规格行。
item.revision = revision
redis.call('HSET', key, field, cjson.encode(item))
redis.call('PEXPIRE', key, ttl)
return 1
