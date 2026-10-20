-- 每次只操作一个用户的 Hash；数量校验、容量校验、写入与续期在同一脚本原子完成。
-- ID 保留为字符串，避免 Lua/cjson 的双精度数字截断 Java BIGINT 主键。
local key = KEYS[1]
local action = ARGV[1]
local field = ARGV[2]
local windowId = ARGV[3]
local productId = ARGV[4]
local value = ARGV[5]
local revision = ARGV[6]
local name = ARGV[7]
local code = ARGV[8]
local maxItems = tonumber(ARGV[9])
local maxQuantity = tonumber(ARGV[10])
local ttl = tonumber(ARGV[11])

-- 删除不存在的条目属于幂等成功；没有实际变更时不创建 Key、不延长其它条目 TTL。
if action == 'remove' then
    local removed = redis.call('HDEL', key, field)
    if removed > 0 and redis.call('EXISTS', key) == 1 then
        redis.call('PEXPIRE', key, ttl)
    end
    return removed
end

-- 容量受配置约束，遍历单用户 Hash 即可；前缀带冒号，不会把窗口 1 与 10 混淆。
if action == 'clearWindow' then
    local removed = 0
    local prefix = windowId .. ':'
    for _, itemField in ipairs(redis.call('HKEYS', key)) do
        if string.sub(itemField, 1, string.len(prefix)) == prefix then
            removed = removed + redis.call('HDEL', key, itemField)
        end
    end
    if removed > 0 and redis.call('EXISTS', key) == 1 then
        redis.call('PEXPIRE', key, ttl)
    end
    return removed
end

local raw = redis.call('HGET', key, field)
local item
if raw then
    -- 数据异常必须在任何写操作之前拒绝，Lua 脚本报错不会自动回滚先前写入。
    local ok, decoded = pcall(cjson.decode, raw)
    if not ok or type(decoded) ~= 'table' or type(decoded.quantity) ~= 'number'
            or decoded.quantity < 1 or decoded.quantity % 1 ~= 0
            or type(decoded.checked) ~= 'boolean' or type(decoded.revision) ~= 'string'
            or tostring(decoded.windowId) ~= windowId or tostring(decoded.productId) ~= productId then
        return -4
    end
    item = decoded
elseif action ~= 'add' then
    -- PUT/PATCH 不得意外变为新增，不存在时交由 Java 返回 404。
    return -1
end

if action == 'add' then
    if not item then
        -- 检查容量和插入位于同一脚本，多个实例同时添加不同菜品也不能越过上限。
        if redis.call('HLEN', key) >= maxItems then
            return -2
        end
        item = {windowId = windowId, productId = productId, quantity = 0, checked = true}
    end
    local quantity = item.quantity + tonumber(value)
    if quantity < 1 or quantity > maxQuantity then
        return -3
    end
    item.quantity = quantity
    item.productNameSnapshot = name
    item.productCodeSnapshot = code
elseif action == 'quantity' then
    local quantity = tonumber(value)
    if quantity < 1 or quantity > maxQuantity then
        return -3
    end
    item.quantity = quantity
elseif action == 'checked' then
    item.checked = value == 'true'
else
    return -4
end

-- 每次修改都使用全新版本，删除重建也不会复用版本，便于后续安全清理提交快照。
item.revision = revision
redis.call('HSET', key, field, cjson.encode(item))
redis.call('PEXPIRE', key, ttl)
return 1
