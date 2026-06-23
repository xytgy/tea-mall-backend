package com.xytgy.teamallbackend.mq.consumer;

import com.xytgy.teamallbackend.module.flashsale.mapper.FlashSaleFailedOrderMapper;
import com.xytgy.teamallbackend.mq.handler.FlashOrderHandler;
import com.xytgy.teamallbackend.module.flashsale.service.FlashOrderPersistenceService;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleMetrics;
import com.xytgy.teamallbackend.module.flashsale.service.FlashSaleNotificationService;
import com.xytgy.teamallbackend.module.order.mapper.OrdersMapper;
import com.xytgy.teamallbackend.module.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(FlashOrderFlowIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:flashmq;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/flash-order-it-schema.sql"
})
class FlashOrderFlowIntegrationTest {

    @Autowired
    private FlashOrderMqListener listener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        reset(stringRedisTemplate);
        jdbcTemplate.update("DELETE FROM flash_sale_failed_order");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("""
                INSERT INTO product(id, name, price, stock, status, audit_status, is_deleted)
                VALUES (2, 'tea', 99.00, 5, 1, 1, 0)
                """);
    }

    @Test
    void listenerPersistsOrderAndDeductsStock() {
        listener.onMessage("""
                {"transactionId":"flash:1:2:3:1000","flashSaleId":1,"productId":2,"userId":3,"flashPrice":88.00}
                """);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE order_no = 'flash:1:2:3:1000'", Integer.class);
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = 2", Integer.class);
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flash_sale_failed_order", Integer.class);

        assertEquals(1, orderCount);
        assertEquals(4, stock);
        assertEquals(0, failedCount);
        verify(stringRedisTemplate).delete("flash:pending:flash:1:2:3:1000");
    }

    @Test
    void listenerIsIdempotentForDuplicateTransactionId() {
        String payload = """
                {"transactionId":"flash:1:2:3:2000","flashSaleId":1,"productId":2,"userId":3,"flashPrice":88.00}
                """;

        listener.onMessage(payload);
        listener.onMessage(payload);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE order_no = 'flash:1:2:3:2000'", Integer.class);
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = 2", Integer.class);

        assertEquals(1, orderCount);
        assertEquals(4, stock);
        verify(stringRedisTemplate, times(2)).delete(anyString());
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            SqlInitializationAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    @MapperScan(basePackageClasses = {
            OrdersMapper.class,
            ProductMapper.class,
            FlashSaleFailedOrderMapper.class
    })
    static class TestConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        FlashSaleNotificationService flashSaleNotificationService() {
            return mock(FlashSaleNotificationService.class);
        }

        @Bean
        FlashSaleMetrics flashSaleMetrics() {
            return new FlashSaleMetrics();
        }

        @Bean
        FlashOrderPersistenceService flashOrderPersistenceService(
                OrdersMapper ordersMapper,
                ProductMapper productMapper,
                FlashSaleFailedOrderMapper flashSaleFailedOrderMapper,
                FlashSaleMetrics flashSaleMetrics,
                FlashSaleNotificationService flashSaleNotificationService,
                StringRedisTemplate stringRedisTemplate
        ) {
            return new FlashOrderPersistenceService(
                    ordersMapper,
                    productMapper,
                    flashSaleFailedOrderMapper,
                    flashSaleMetrics,
                    flashSaleNotificationService,
                    stringRedisTemplate
            );
        }

        @Bean
        FlashOrderHandler flashOrderHandler(FlashOrderPersistenceService flashOrderPersistenceService) {
            return new FlashOrderHandler(flashOrderPersistenceService);
        }

        @Bean
        FlashOrderMqListener flashOrderMqListener(
                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                FlashOrderHandler flashOrderHandler
        ) {
            return new FlashOrderMqListener(objectMapper, flashOrderHandler);
        }
    }
}
