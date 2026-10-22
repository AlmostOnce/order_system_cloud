if redis.call('GET', KEYS[5]) ~= ARGV[1] then return -2 end
if not redis.call('GET', KEYS[4]) then return -1 end
if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then return 0 end
-- 先校验所有增量的精确整数边界，再执行任何写入。
for pos = 4, #ARGV, 3 do
    local quantity = tonumber(ARGV[pos + 1])
    local current = tonumber(redis.call('ZSCORE', KEYS[1], ARGV[pos]) or '0')
    if not quantity or quantity <= 0 or quantity % 1 ~= 0 or current + quantity > 9007199254740991 then return -3 end
end
for pos = 4, #ARGV, 3 do
    local member = ARGV[pos]
    redis.call('ZINCRBY', KEYS[1], ARGV[pos + 1], member)
    local previous = redis.call('HGET', KEYS[2], member)
    local incoming = ARGV[pos + 2]
    -- 元数据以 20 位订单明细 ID 作版本；乱序消息不回退菜品名称。
    if not previous or string.sub(incoming, 1, 20) > string.sub(previous, 1, 20) then
        redis.call('HSET', KEYS[2], member, incoming)
    end
end
redis.call('SADD', KEYS[3], ARGV[2])
redis.call('SET', KEYS[4], ARGV[3])
return 1
