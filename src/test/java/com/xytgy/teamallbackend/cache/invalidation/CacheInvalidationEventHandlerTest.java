package com.xytgy.teamallbackend.cache.invalidation;

import com.xytgy.teamallbackend.cache.key.VersionedCacheKeyService;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.RestrictedTransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CacheInvalidationEventHandlerTest {

    @Test
    void shouldRouteAllInvalidationOperations() {
        CacheKeyCleaner cleaner = mock(CacheKeyCleaner.class);
        VersionedCacheKeyService versionService = mock(VersionedCacheKeyService.class);
        CacheInvalidationEventHandler handler =
                new CacheInvalidationEventHandler(cleaner, versionService);

        handler.handle(new CacheInvalidationEvent(
                CacheInvalidationEvent.Operation.KEY, "product:1"));
        handler.handle(new CacheInvalidationEvent(
                CacheInvalidationEvent.Operation.PATTERN, "product:*"));
        handler.handle(new CacheInvalidationEvent(
                CacheInvalidationEvent.Operation.VERSION, "product:version"));

        verify(cleaner).delete("product:1");
        verify(cleaner).deleteByPattern("product:*");
        verify(versionService).incrementNow("product:version");
    }

    @Test
    void shouldExecuteOnlyAfterTransactionCommit() {
        CacheKeyCleaner cleaner = mock(CacheKeyCleaner.class);
        VersionedCacheKeyService versionService = mock(VersionedCacheKeyService.class);
        try (GenericApplicationContext context = eventContext(cleaner, versionService)) {
            TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());

            transaction.executeWithoutResult(status -> {
                context.publishEvent(new CacheInvalidationEvent(
                        CacheInvalidationEvent.Operation.KEY, "product:1"));
                verify(cleaner, never()).delete("product:1");
            });

            verify(cleaner).delete("product:1");
        }
    }

    @Test
    void shouldSkipInvalidationWhenTransactionRollsBack() {
        CacheKeyCleaner cleaner = mock(CacheKeyCleaner.class);
        VersionedCacheKeyService versionService = mock(VersionedCacheKeyService.class);
        try (GenericApplicationContext context = eventContext(cleaner, versionService)) {
            TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());

            transaction.executeWithoutResult(status -> {
                context.publishEvent(new CacheInvalidationEvent(
                        CacheInvalidationEvent.Operation.KEY, "product:1"));
                status.setRollbackOnly();
            });

            verify(cleaner, never()).delete("product:1");
        }
    }

    private static GenericApplicationContext eventContext(
            CacheKeyCleaner cleaner,
            VersionedCacheKeyService versionService) {
        GenericApplicationContext context = new GenericApplicationContext();
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
        context.registerBean(RestrictedTransactionalEventListenerFactory.class);
        context.registerBean(CacheInvalidationEventHandler.class,
                () -> new CacheInvalidationEventHandler(cleaner, versionService));
        context.refresh();
        return context;
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
