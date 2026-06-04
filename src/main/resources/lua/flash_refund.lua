-- KEYS[1] = {flash:100}:stock
-- KEYS[2] = {flash:100}:refunded
-- ARGV[1] = transactionId
-- 返回: -1=已回补(幂等), 1=回补成功

if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return -1
end
redis.call('incr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 1
