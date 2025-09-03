import com.sacred.BalanceCalculationApplication;
import com.sacred.balance.model.Account;
import com.sacred.balance.model.Transaction;
import com.sacred.balance.repository.AccountRepository;
import com.sacred.balance.service.AccountService;
import com.sacred.balance.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = BalanceCalculationApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TransactionServiceTest {
    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        // 清空缓存
        if (cacheManager.getCache("accounts") != null) {
            cacheManager.getCache("accounts").clear();
        }
    }

    @Test
    void shouldProcessValidTransaction() {
        Account source = new Account("A001", 100.0);
        Account dest = new Account("A002", 0.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        Transaction tx = new Transaction();
        tx.setTransactionId(10000001L);
        tx.setSourceAccount("A001");
        tx.setDestinationAccount("A002");
        tx.setAmount(50.0);

        assertTrue(transactionService.processTransaction(tx));

        Optional<Account> updatedSource = accountRepository.findByAccountNumber("A001");
        Optional<Account> updatedDest = accountRepository.findByAccountNumber("A002");

        assertEquals(50.0, updatedSource.get().getBalance());
        assertEquals(50.0, updatedDest.get().getBalance());
    }

    @Test
    void shouldCacheAccountAfterFirstAccess() {
        // 准备测试数据
        Account account = new Account("A003", 200.0);
        accountRepository.save(account);

        // 第一次访问，应该从数据库加载并存入缓存
        Optional<Account> accountFromService = accountService.findByAccountNumber("A003");
        assertTrue(accountFromService.isPresent());
        assertEquals(200.0, accountFromService.get().getBalance());

        // 验证缓存中存在该账户
        Object cachedAccount = redisTemplate.opsForValue().get("accounts::A003");
        assertNotNull(cachedAccount);
        assertEquals(200.0, ((Account) cachedAccount).getBalance());
    }

    @Test
    void shouldUpdateCacheWhenAccountIsModified() {
        // 准备测试数据
        Account account = new Account("A004", 300.0);
        Account savedAccount = accountRepository.save(account);

        // 先访问一次，使数据进入缓存
        accountService.findByAccountNumber("A004");

        // 修改账户余额
        savedAccount.setBalance(400.0);
        Account updatedAccount = accountService.updateAccount(savedAccount);

        // 验证返回值正确
        assertEquals(400.0, updatedAccount.getBalance());

        // 验证缓存中数据已更新
        Object cachedAccount = redisTemplate.opsForValue().get("accounts::A004");
        assertNotNull(cachedAccount);
        assertEquals(400.0, ((Account) cachedAccount).getBalance());

        // 验证数据库中数据也已更新
        Optional<Account> dbAccount = accountRepository.findByAccountNumber("A004");
        assertTrue(dbAccount.isPresent());
        assertEquals(400.0, dbAccount.get().getBalance());
    }

    @Test
    void shouldMaintainConsistencyBetweenCacheAndDatabase() {
        // 创建账户
        Account source = new Account("A005", 500.0);
        Account dest = new Account("A006", 100.0);
        accountRepository.save(source);
        accountRepository.save(dest);

        // 执行交易前，先访问账户使其进入缓存
        accountService.findByAccountNumber("A005");
        accountService.findByAccountNumber("A006");

        // 执行交易
        Transaction tx = new Transaction();
        tx.setTransactionId(10000002L);
        tx.setSourceAccount("A005");
        tx.setDestinationAccount("A006");
        tx.setAmount(100.0);

        assertTrue(transactionService.processTransaction(tx));

        // 检查数据库中的数据
        Optional<Account> dbSource = accountRepository.findByAccountNumber("A005");
        Optional<Account> dbDest = accountRepository.findByAccountNumber("A006");
        assertTrue(dbSource.isPresent());
        assertTrue(dbDest.isPresent());
        assertEquals(400.0, dbSource.get().getBalance());
        assertEquals(200.0, dbDest.get().getBalance());

        // 检查缓存中的数据
        Object cachedSource = redisTemplate.opsForValue().get("accounts::A005");
        Object cachedDest = redisTemplate.opsForValue().get("accounts::A006");
        assertNotNull(cachedSource);
        assertNotNull(cachedDest);
        assertEquals(400.0, ((Account) cachedSource).getBalance());
        assertEquals(200.0, ((Account) cachedDest).getBalance());

        // 验证缓存和数据库数据一致性
        assertEquals(dbSource.get().getBalance(), ((Account) cachedSource).getBalance());
        assertEquals(dbDest.get().getBalance(), ((Account) cachedDest).getBalance());
    }
}
