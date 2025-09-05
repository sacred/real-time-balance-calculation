package com.sacred.balance.service;

import com.sacred.BalanceCalculationApplication;
import com.sacred.balance.model.Account;
import com.sacred.balance.model.BatchResult;
import com.sacred.balance.model.Transaction;
import com.sacred.balance.model.TransactionResult;
import com.sacred.balance.repository.AccountRepository;
import com.sacred.balance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BalanceCalculationApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.redis.database=1" // 使用Redis数据库1进行测试

})
class TransactionServiceTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        transactionRepository.deleteAll();

        // 清空Redis中的测试数据
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void shouldProcessValidTransaction() {
        // 准备测试数据
        Account source = new Account("A001", 100.0);
        Account dest = new Account("A002", 0.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        Transaction tx = new Transaction();
        tx.setTransactionId("T001");
        tx.setSourceAccount("A001");
        tx.setDestinationAccount("A002");
        tx.setAmount(50.0);

        TransactionResult result = transactionService.processTransaction(tx);

        // 验证结果
        assertTrue(result.isSuccess());
        assertEquals("T001", result.getTransactionId());
        assertEquals("Success", result.getMessage());

        // 验证数据库余额更新
        Optional<Account> updatedSource = accountRepository.findByAccountNumber("A001");
        Optional<Account> updatedDest = accountRepository.findByAccountNumber("A002");

        assertTrue(updatedSource.isPresent());
        assertTrue(updatedDest.isPresent());
        assertEquals(50.0, updatedSource.get().getBalance());
        assertEquals(50.0, updatedDest.get().getBalance());

        // 验证交易已保存到数据库
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void shouldFailTransactionWhenSourceAccountNotFound() {
        Account dest = new Account("A002", 0.0);
        accountRepository.save(dest);

        Transaction tx = new Transaction();
        tx.setTransactionId("T001");
        tx.setSourceAccount("A001"); // 不存在的账户
        tx.setDestinationAccount("A002");
        tx.setAmount(50.0);

        TransactionResult result = transactionService.processTransaction(tx);

        assertFalse(result.isSuccess());
        assertEquals("404", result.getErrorCode());
        assertTrue(result.getMessage().contains("Source account not found"));
    }

    @Test
    void shouldFailTransactionWhenDestinationAccountNotFound() {
        Account source = new Account("A001", 100.0);
        accountRepository.save(source);

        Transaction tx = new Transaction();
        tx.setTransactionId("T001");
        tx.setSourceAccount("A001");
        tx.setDestinationAccount("A002"); // 不存在的账户
        tx.setAmount(50.0);

        TransactionResult result = transactionService.processTransaction(tx);

        assertFalse(result.isSuccess());
        assertEquals("404", result.getErrorCode());
        assertTrue(result.getMessage().contains("Destination account not found"));
    }

    @Test
    void shouldFailTransactionWhenInsufficientBalance() {
        Account source = new Account("A001", 30.0);
        Account dest = new Account("A002", 0.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        Transaction tx = new Transaction();
        tx.setTransactionId("T001");
        tx.setSourceAccount("A001");
        tx.setDestinationAccount("A002");
        tx.setAmount(50.0); // 超过余额

        TransactionResult result = transactionService.processTransaction(tx);

        assertFalse(result.isSuccess());
        assertEquals("400", result.getErrorCode());
        assertTrue(result.getMessage().contains("Insufficient balance"));

        // 验证余额未发生变化
        Optional<Account> updatedSource = accountRepository.findByAccountNumber("A001");
        Optional<Account> updatedDest = accountRepository.findByAccountNumber("A002");

        assertTrue(updatedSource.isPresent());
        assertTrue(updatedDest.isPresent());
        assertEquals(30.0, updatedSource.get().getBalance());
        assertEquals(0.0, updatedDest.get().getBalance());
    }

    @Test
    void shouldHandleIdempotentTransactionProcessing() {
        // 创建账户
        Account source = new Account("A003", 200.0);
        Account dest = new Account("A004", 100.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        Transaction tx = new Transaction();
        tx.setTransactionId("T002");
        tx.setSourceAccount("A003");
        tx.setDestinationAccount("A004");
        tx.setAmount(50.0);

        // 第一次处理
        TransactionResult firstResult = transactionService.processTransaction(tx);

        assertTrue(firstResult.isSuccess());
        assertEquals("T002", firstResult.getTransactionId());

        // 第二次处理同一笔交易（幂等性检查）
        TransactionResult secondResult = transactionService.processTransaction(tx);

        assertTrue(secondResult.isSuccess());
        assertEquals("T002", secondResult.getTransactionId());
        assertEquals("Already processed", secondResult.getMessage());

        // 验证余额只变化一次
        Optional<Account> finalSource = accountRepository.findByAccountNumber("A003");
        Optional<Account> finalDest = accountRepository.findByAccountNumber("A004");

        assertTrue(finalSource.isPresent());
        assertTrue(finalDest.isPresent());
        assertEquals(150.0, finalSource.get().getBalance()); // 只扣了一次50
        assertEquals(150.0, finalDest.get().getBalance());   // 只加了一次50
    }

    @Test
    void shouldProcessBatchTransactions() {
        // 创建账户
        Account source = new Account("A008", 300.0);
        Account dest = new Account("A009", 100.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        // 创建多个交易
        Transaction tx1 = new Transaction();
        tx1.setTransactionId("T006");
        tx1.setSourceAccount("A008");
        tx1.setDestinationAccount("A009");
        tx1.setAmount(50.0);

        Transaction tx2 = new Transaction();
        tx2.setTransactionId("T007");
        tx2.setSourceAccount("A008");
        tx2.setDestinationAccount("A009");
        tx2.setAmount(100.0);

        Transaction tx3 = new Transaction();
        tx3.setTransactionId("T008");
        tx3.setSourceAccount("A008");
        tx3.setDestinationAccount("A009");
        tx3.setAmount(150.0); // 这个交易应该成功，余额正好够

        // 批量处理交易
        BatchResult result = transactionService.processBatchTransactions(tx1, tx2, tx3);

        assertTrue(result.getSuccessfulTransactions() == 3);
        assertEquals(3, result.getTotalTransactions());
        assertEquals(0, result.getFailedTransactions());
        assertEquals(3, result.getResults().size());

        // 验证最终余额
        Optional<Account> finalSource = accountRepository.findByAccountNumber("A008");
        Optional<Account> finalDest = accountRepository.findByAccountNumber("A009");

        assertTrue(finalSource.isPresent());
        assertTrue(finalDest.isPresent());

        // A008: 300 - 50 - 100 - 150 = 0
        // A009: 100 + 50 + 100 + 150 = 400
        assertEquals(0.0, finalSource.get().getBalance());
        assertEquals(400.0, finalDest.get().getBalance());
    }

    @Test
    void shouldHandleBatchWithSomeFailedTransactions() {
        // 创建账户
        Account source = new Account("A010", 100.0);
        Account dest = new Account("A011", 50.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        // 创建多个交易，其中一个会失败
        Transaction tx1 = new Transaction();
        tx1.setTransactionId("T009");
        tx1.setSourceAccount("A010");
        tx1.setDestinationAccount("A011");
        tx1.setAmount(30.0);

        Transaction tx2 = new Transaction();
        tx2.setTransactionId("T010");
        tx2.setSourceAccount("A010");
        tx2.setDestinationAccount("A011");
        tx2.setAmount(80.0); // 这个交易会失败，因为余额不足

        Transaction tx3 = new Transaction();
        tx3.setTransactionId("T011");
        tx3.setSourceAccount("A010");
        tx3.setDestinationAccount("A011");
        tx3.setAmount(20.0);

        // 批量处理交易
        BatchResult result = transactionService.processBatchTransactions(tx1, tx2, tx3);

        // 应该有两个成功，一个失败
        assertEquals(2, result.getSuccessfulTransactions());
        assertEquals(1, result.getFailedTransactions());
        assertEquals(3, result.getTotalTransactions());

        // 验证结果详情
        assertEquals(3, result.getResults().size());
        assertTrue(result.getResults().get(0).isSuccess()); // T009 应该成功
        assertFalse(result.getResults().get(1).isSuccess()); // T010 应该失败
        assertTrue(result.getResults().get(2).isSuccess()); // T011 应该成功

        // 验证余额变化（只有成功的交易生效）
        Optional<Account> finalSource = accountRepository.findByAccountNumber("A010");
        Optional<Account> finalDest = accountRepository.findByAccountNumber("A011");

        assertTrue(finalSource.isPresent());
        assertTrue(finalDest.isPresent());

        // A010: 100 - 30 - 20 = 50 (T010失败，不扣款)
        // A011: 50 + 30 + 20 = 100
        assertEquals(50.0, finalSource.get().getBalance());
        assertEquals(100.0, finalDest.get().getBalance());
    }

    @Test
    void shouldCheckIfTransactionIsProcessed() {
        // 创建账户
        Account source = new Account("A012", 100.0);
        Account dest = new Account("A013", 50.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        // 创建并处理交易
        Transaction tx = new Transaction();
        tx.setTransactionId("T012");
        tx.setSourceAccount("A012");
        tx.setDestinationAccount("A013");
        tx.setAmount(30.0);

        TransactionResult result = transactionService.processTransaction(tx);

        assertTrue(result.isSuccess());

        // 检查交易是否已经处理
        boolean isProcessed = transactionService.isTransactionProcessed("T012");
        assertTrue(isProcessed);

        // 检查不存在的交易
        boolean isNotProcessed = transactionService.isTransactionProcessed("NON_EXISTENT_TX");
        assertFalse(isNotProcessed);
    }

    @Test
    void shouldGetTransactionResult() {
        // 创建账户
        Account source = new Account("A014", 200.0);
        Account dest = new Account("A015", 100.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        // 创建并处理交易
        Transaction tx = new Transaction();
        tx.setTransactionId("T013");
        tx.setSourceAccount("A014");
        tx.setDestinationAccount("A015");
        tx.setAmount(50.0);

        TransactionResult processResult = transactionService.processTransaction(tx);

        assertTrue(processResult.isSuccess());

        // 获取交易结果
        TransactionResult getResult = transactionService.getTransactionResult("T013");

        assertTrue(getResult.isSuccess());
        assertEquals("T013", getResult.getTransactionId());
        assertEquals("Transaction processed successfully", getResult.getMessage());

        // 获取不存在的交易结果
        TransactionResult notFoundResult = transactionService.getTransactionResult("NON_EXISTENT_TX");

        assertFalse(notFoundResult.isSuccess());
        assertEquals("404", notFoundResult.getErrorCode());
        assertEquals("Transaction not found", notFoundResult.getMessage());
    }

    @Test
    void shouldHandleProcessingTimeout() {
        // 创建账户
        Account source = new Account("A016", 100.0);
        Account dest = new Account("A017", 0.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        String transactionId = "T014";

        // 手动设置一个超时的处理状态
        String idempotentKey = "transaction:idempotent:" + transactionId;
        String startTimeKey = idempotentKey + ":starttime";

        stringRedisTemplate.opsForValue().set(idempotentKey, "processing");
        // 设置一个很久以前的开始时间（超过5分钟）
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(10);
        stringRedisTemplate.opsForValue().set(startTimeKey, oldTime.toString());

        // 创建交易
        Transaction tx = new Transaction();
        tx.setTransactionId(transactionId);
        tx.setSourceAccount("A016");
        tx.setDestinationAccount("A017");
        tx.setAmount(50.0);

        // 处理交易，应该能够重置超时状态并成功处理
        TransactionResult result = transactionService.processTransaction(tx);

        assertTrue(result.isSuccess());
        assertEquals(transactionId, result.getTransactionId());
        assertEquals("Success", result.getMessage());

        // 验证余额已更新
        Optional<Account> updatedSource = accountRepository.findByAccountNumber("A016");
        Optional<Account> updatedDest = accountRepository.findByAccountNumber("A017");

        assertTrue(updatedSource.isPresent());
        assertTrue(updatedDest.isPresent());
        assertEquals(50.0, updatedSource.get().getBalance());
        assertEquals(50.0, updatedDest.get().getBalance());
    }

    @Test
    void shouldHandleTransactionWithNullId() {
        // 创建账户
        Account source = new Account("A018", 100.0);
        Account dest = new Account("A019", 0.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        // 创建没有ID的交易
        Transaction tx = new Transaction();
        tx.setSourceAccount("A018");
        tx.setDestinationAccount("A019");
        tx.setAmount(30.0);

        // 处理交易，应该自动生成ID
        TransactionResult result = transactionService.processTransaction(tx);

        assertTrue(result.isSuccess());
        assertNotNull(result.getTransactionId());
        assertFalse(result.getTransactionId().isEmpty());
        assertNotEquals("unknown", result.getTransactionId());

        // 验证余额已更新
        Optional<Account> updatedSource = accountRepository.findByAccountNumber("A018");
        Optional<Account> updatedDest = accountRepository.findByAccountNumber("A019");

        assertTrue(updatedSource.isPresent());
        assertTrue(updatedDest.isPresent());
        assertEquals(70.0, updatedSource.get().getBalance());
        assertEquals(30.0, updatedDest.get().getBalance());
    }

    @Test
    void shouldHandleInvalidTransactionAmount() {
        Transaction tx = new Transaction();
        tx.setTransactionId("T015");
        tx.setSourceAccount("A020");
        tx.setDestinationAccount("A021");
        tx.setAmount(-10.0); // 负数金额

        TransactionResult result = transactionService.processTransaction(tx);

        assertFalse(result.isSuccess());
        assertEquals("400", result.getErrorCode());
        assertTrue(result.getMessage().contains("Transaction amount must be positive"));
    }

    @Test
    void shouldHandleNullAccounts() {
        Transaction tx = new Transaction();
        tx.setTransactionId("T016");
        tx.setSourceAccount(null);
        tx.setDestinationAccount("A021");
        tx.setAmount(50.0);

        TransactionResult result = transactionService.processTransaction(tx);

        assertFalse(result.isSuccess());
        assertEquals("400", result.getErrorCode());
        assertTrue(result.getMessage().contains("Source and destination accounts are required"));
    }
}
