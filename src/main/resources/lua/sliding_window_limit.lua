-- 滑动窗口限流 Lua 脚本（1 次 RTT 完成清理+计数+记录+过期）
-- KEYS[1] = flash:rate:{userId}:{flashSaleId}
-- ARGV[1] = 当前时间戳(毫秒)
-- ARGV[2] = 窗口大小(毫秒)，固定 60000
-- ARGV[3] = 请求唯一ID（UUID）
-- 返回：当前窗口内的请求计数（不含本次请求）

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local requestId = ARGV[3]

-- 清理窗口外的过期记录，防止 Sorted Set 无限膨胀
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 获取当前窗口内的请求数（清理后的真实计数）
local count = redis.call('ZCARD', key)

-- 记录本次请求，score 为时间戳用于后续窗口清理
redis.call('ZADD', key, now, requestId)

-- 设置 key 过期时间，避免无访问时残留（毫秒精度）
redis.call('PEXPIRE', key, window)

-- 返回计数（不含本次），由调用方判断是否超限
return count
