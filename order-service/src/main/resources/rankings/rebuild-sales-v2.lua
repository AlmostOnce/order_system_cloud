-- 同一个锁序列的 generation 阻止租约过期后的旧快照覆盖新消费。
if redis.call('GET', KEYS[5]) ~= ARGV[1] then return 0 end
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4])
local count = tonumber(ARGV[3])
local pos = 4
for i = 1, count do
    redis.call('SADD', KEYS[3], ARGV[pos]); pos = pos + 1
end
while pos <= #ARGV do
    redis.call('ZADD', KEYS[1], ARGV[pos + 1], ARGV[pos])
    redis.call('HSET', KEYS[2], ARGV[pos], ARGV[pos + 2])
    pos = pos + 3
end
-- 包括空榜；投影和去重集合一起持久保存，周期重建负责校正。
redis.call('SET', KEYS[4], ARGV[2])
return 1
