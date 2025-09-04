package com.sacred.balance.service;

import com.sacred.BalanceCalculationApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BalanceCalculationApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.redis.database=1" // 使用Redis数据库1进行测试
})
class TransactionRecoveryServiceTest {

    @Autowired
    private TransactionRecoveryService transactionRecoveryService;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // 清空Redis中的测试数据
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void shouldRecoverStaleTransactions() {
        String transactionId = "STALE_TX_001";
        String idempotentKey = "transaction:idempotent:" + transactionId;
        String startTimeKey = idempotentKey + ":starttime";

        // 设置一个超时的处理状态
        stringRedisTemplate.opsForValue().set(idempotentKey, "processing");
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(10); // 10分钟前
        stringRedisTemplate.opsForValue().set(startTimeKey, oldTime.toString());

        // 验证状态已设置
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNotNull(stringRedisTemplate.opsForValue().get(startTimeKey));

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证超时状态已被清理
        assertNull(stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNull(stringRedisTemplate.opsForValue().get(startTimeKey));
    }

    @Test
    void shouldNotRecoverFreshTransactions() {
        String transactionId = "FRESH_TX_001";
        String idempotentKey = "transaction:idempotent:" + transactionId;
        String startTimeKey = idempotentKey + ":starttime";

        // 设置一个新鲜的处理状态
        stringRedisTemplate.opsForValue().set(idempotentKey, "processing");
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(2); // 2分钟前
        stringRedisTemplate.opsForValue().set(startTimeKey, recentTime.toString());

        // 验证状态已设置
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNotNull(stringRedisTemplate.opsForValue().get(startTimeKey));

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证新鲜状态未被清理
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNotNull(stringRedisTemplate.opsForValue().get(startTimeKey));
    }

    @Test
    void shouldHandleTransactionsWithoutStartTime() {
        String transactionId = "NO_START_TIME_TX_001";
        String idempotentKey = "transaction:idempotent:" + transactionId;

        // 设置一个没有开始时间的处理状态
        stringRedisTemplate.opsForValue().set(idempotentKey, "processing");

        // 验证状态已设置
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNull(stringRedisTemplate.opsForValue().get(idempotentKey + ":starttime"));

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证无开始时间的状态已被清理
        assertNull(stringRedisTemplate.opsForValue().get(idempotentKey));
    }

    @Test
    void shouldHandleAlreadyProcessedTransactions() {
        String transactionId = "PROCESSED_TX_001";
        String idempotentKey = "transaction:idempotent:" + transactionId;

        // 设置已处理状态
        stringRedisTemplate.opsForValue().set(idempotentKey, "processed");

        // 验证状态已设置
        assertEquals("processed", stringRedisTemplate.opsForValue().get(idempotentKey));

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证已处理状态未被清理
        assertEquals("processed", stringRedisTemplate.opsForValue().get(idempotentKey));
    }

    @Test
    void shouldHandleEmptyKeys() {
        // 确保Redis中没有任何键
        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 不应该抛出异常
        assertTrue(true); // 测试通过，没有异常
    }

    @Test
    void shouldHandleInvalidStartTimeFormat() {
        String transactionId = "INVALID_TIME_TX_001";
        String idempotentKey = "transaction:idempotent:" + transactionId;
        String startTimeKey = idempotentKey + ":starttime";

        // 设置处理状态和无效的时间格式
        stringRedisTemplate.opsForValue().set(idempotentKey, "processing");
        stringRedisTemplate.opsForValue().set(startTimeKey, "invalid-time-format");

        // 验证状态已设置
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertEquals("invalid-time-format", stringRedisTemplate.opsForValue().get(startTimeKey));

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证无效时间格式的交易仍然存在（不会被错误地清理）
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertEquals("invalid-time-format", stringRedisTemplate.opsForValue().get(startTimeKey));
    }

    @Test
    void shouldRecoverMultipleStaleTransactions() {
        // 设置多个超时的交易
        String[] transactionIds = {"STALE_TX_001", "STALE_TX_002", "STALE_TX_003"};
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(10); // 10分钟前

        for (String transactionId : transactionIds) {
            String idempotentKey = "transaction:idempotent:" + transactionId;
            String startTimeKey = idempotentKey + ":starttime";

            stringRedisTemplate.opsForValue().set(idempotentKey, "processing");
            stringRedisTemplate.opsForValue().set(startTimeKey, oldTime.toString());

            // 验证状态已设置
            assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
            assertNotNull(stringRedisTemplate.opsForValue().get(startTimeKey));
        }

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证所有超时状态都已被清理
        for (String transactionId : transactionIds) {
            String idempotentKey = "transaction:idempotent:" + transactionId;
            String startTimeKey = idempotentKey + ":starttime";

            assertNull(stringRedisTemplate.opsForValue().get(idempotentKey));
            assertNull(stringRedisTemplate.opsForValue().get(startTimeKey));
        }
    }

    @Test
    void shouldHandleMixedTransactionStates() {
        // 设置不同状态的交易
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(10); // 10分钟前
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(2); // 2分钟前

        // 1. 超时的处理中交易
        stringRedisTemplate.opsForValue().set("transaction:idempotent:STALE_001", "processing");
        stringRedisTemplate.opsForValue().set("transaction:idempotent:STALE_001:starttime", oldTime.toString());

        // 2. 新鲜的处理中交易
        stringRedisTemplate.opsForValue().set("transaction:idempotent:FRESH_001", "processing");
        stringRedisTemplate.opsForValue().set("transaction:idempotent:FRESH_001:starttime", recentTime.toString());

        // 3. 已完成的交易
        stringRedisTemplate.opsForValue().set("transaction:idempotent:PROCESSED_001", "processed");

        // 4. 没有开始时间的交易
        stringRedisTemplate.opsForValue().set("transaction:idempotent:NO_TIME_001", "processing");

        // 执行恢复
        transactionRecoveryService.recoverStaleTransactions();

        // 验证结果
        // 1. 超时的处理中交易应该被清理
        assertNull(stringRedisTemplate.opsForValue().get("transaction:idempotent:STALE_001"));
        assertNull(stringRedisTemplate.opsForValue().get("transaction:idempotent:STALE_001:starttime"));

        // 2. 新鲜的处理中交易应该保留
        assertEquals("processing", stringRedisTemplate.opsForValue().get("transaction:idempotent:FRESH_001"));
        assertNotNull(stringRedisTemplate.opsForValue().get("transaction:idempotent:FRESH_001:starttime"));

        // 3. 已完成的交易应该保留
        assertEquals("processed", stringRedisTemplate.opsForValue().get("transaction:idempotent:PROCESSED_001"));

        // 4. 没有开始时间的交易应该被清理
        assertNull(stringRedisTemplate.opsForValue().get("transaction:idempotent:NO_TIME_001"));
    }

    @Test
    void shouldRecoverStaleTransactionsPeriodically() throws InterruptedException {
        String transactionId = "STALE_TX_PERIODIC_001";
        String idempotentKey = "transaction:idempotent:" + transactionId;
        String startTimeKey = idempotentKey + ":starttime";

        // 设置一个超时的处理状态
        stringRedisTemplate.opsForValue().set(idempotentKey, "processing");
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(10); // 10分钟前
        stringRedisTemplate.opsForValue().set(startTimeKey, oldTime.toString());

        // 验证状态已设置
        assertEquals("processing", stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNotNull(stringRedisTemplate.opsForValue().get(startTimeKey));

        // 执行定期恢复
        transactionRecoveryService.recoverStaleTransactionsPeriodically();

        // 验证超时状态已被清理
        assertNull(stringRedisTemplate.opsForValue().get(idempotentKey));
        assertNull(stringRedisTemplate.opsForValue().get(startTimeKey));
    }
}
