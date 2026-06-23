-- Hash Tag {flash:productId} 保证所有 key 在同一 slot
-- KEYS[1] = {flash:100}:stock
-- KEYS[2] = {flash:100}:bought
-- ARGV[1] = userId
-- 返回: -1=已购买, -2=已售罄, >=0=购买成功剩余库存

if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return -1
end

local stock = tonumber(redis.call('get', KEYS[1]))
if stock == nil or stock <= 0 then
    return -2
end

redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return stock - 1
