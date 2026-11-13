-- 榜单必须与调用方在 MySQL 读取的版本相同；版本变化期间返回空结果以触发锁内重建。
local cachedVersion = redis.call('GET', KEYS[4])
if not cachedVersion or cachedVersion ~= ARGV[2] then return {} end
local updated = redis.call('GET', KEYS[6])
if not updated then return {} end
local rows = redis.call('ZREVRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1, 'WITHSCORES')
local result = {updated}
for pos = 1, #rows, 2 do
    local meta = redis.call('HGET', KEYS[2], rows[pos])
    if not meta then return {} end
    result[#result + 1] = meta
    result[#result + 1] = rows[pos + 1]
end
return result
