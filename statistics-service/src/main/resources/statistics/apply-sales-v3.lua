-- 只有仍持有最新 fencing generation 的实例才能写入缓存；固定租约过期的旧线程必须退出。
if redis.call('GET', KEYS[5]) ~= ARGV[1] then return -2 end
local ready = redis.call('GET', KEYS[4])
if not ready then return -1 end
local cachedVersion = tonumber(ready)
local targetVersion = tonumber(ARGV[4])
if not cachedVersion or not targetVersion then return -4 end
if cachedVersion >= targetVersion then return 0 end
-- 版本必须连续递增；中间若漏了其他订单事件，调用方会从 MySQL 汇总完整重建。
if cachedVersion + 1 ~= targetVersion then return -4 end
if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then return -3 end

-- 先检查所有增量的精确整数上限，再写任何一个菜品，避免一半成功后返回异常。
for pos = 5, #ARGV, 3 do
    local quantity = tonumber(ARGV[pos + 1])
    local current = tonumber(redis.call('ZSCORE', KEYS[1], ARGV[pos]) or '0')
    if not quantity or quantity <= 0 or quantity % 1 ~= 0 or current + quantity > 9007199254740991 then return -5 end
end
for pos = 5, #ARGV, 3 do
    local member = ARGV[pos]
    redis.call('ZINCRBY', KEYS[1], ARGV[pos + 1], member)
    local previous = redis.call('HGET', KEYS[2], member)
    local incoming = ARGV[pos + 2]
    -- 元数据用20位订单明细ID比较，RocketMQ乱序重投也不会回退菜品名称。
    if not previous or string.sub(incoming, 1, 20) > string.sub(previous, 1, 20) then
        redis.call('HSET', KEYS[2], member, incoming)
    end
end
redis.call('SADD', KEYS[3], ARGV[2])
redis.call('SET', KEYS[4], ARGV[4])
redis.call('SET', KEYS[6], ARGV[3])
return 1
