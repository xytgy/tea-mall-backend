package com.xytgy.teamallbackend.cache.invalidation;

import com.xytgy.teamallbackend.cache.key.VersionedCacheKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在数据库事务提交后执行缓存失效；无事务调用则立即执行。
 */
@Component
@RequiredArgsConstructor
public class CacheInvalidationEventHandler {

    private final CacheKeyCleaner keyCleaner;
    private final VersionedCacheKeyService versionedCacheKeyService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void handle(CacheInvalidationEvent event) {
        if (event == null || event.operation() == null) {
            return;
        }
        switch (event.operation()) {
            case KEY -> keyCleaner.delete(event.target());
            case PATTERN -> keyCleaner.deleteByPattern(event.target());
            case VERSION -> versionedCacheKeyService.incrementNow(event.target());
        }
    }
}
