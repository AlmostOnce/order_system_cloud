-- generation 是重建任务启动时拿到的 fencing token；租约过期后的旧快照不得覆盖新事件。
if redis.call('GET', KEYS[5]) ~= ARGV[1] then return 0 end
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[6])
local orderCount = tonumber(ARGV[4])
local pos = 5
for i = 1, orderCount do
    redis.call('SADD', KEYS[3], ARGV[pos])
    pos = pos + 1
end
while pos <= #ARGV do
    redis.call('ZADD', KEYS[1], ARGV[pos + 1], ARGV[pos])
    redis.call('HSET', KEYS[2], ARGV[pos], ARGV[pos + 2])
    pos = pos + 3
end
-- 空榜也需要 ready 版本；缓存版本与 MySQL 一致时，读请求才能直接命中。
redis.call('SET', KEYS[4], ARGV[2])
redis.call('SET', KEYS[6], ARGV[3])
return 1
