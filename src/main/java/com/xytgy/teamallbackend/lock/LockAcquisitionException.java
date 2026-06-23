package com.xytgy.teamallbackend.lock;

/**
 * 分布式锁获取失败的异常。
 * <p>
 * 当 tryLock 超时或被中断时抛出，由 GlobalExceptionHandler 统一处理为友好错误提示。
 * </p>
 */
public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String message) {
        super(message);
    }
}
