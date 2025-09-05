package com.sacred.balance.service.tools;

import com.sacred.balance.model.Account;
import com.sacred.balance.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 测试数据生成器
 * 用于批量生成账户和交易数据并写入CSV文件
 */
public class TestDataGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TestDataGenerator.class);

    // 配置参数
    private static final int ACCOUNT_COUNT = 1000;        // 账户数量
    private static final int TRANSACTION_COUNT = 100000;  // 交易数量
    private static final double INITIAL_BALANCE_MIN = 1000.0;   // 初始余额最小值
    private static final double INITIAL_BALANCE_MAX = 10000000.0; // 初始余额最大值
    private static final double TRANSACTION_AMOUNT_MIN = 1.0;   // 交易金额最小值
    private static final double TRANSACTION_AMOUNT_MAX = 5000.0; // 交易金额最大值

    private final Random random = new Random();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成测试数据并写入CSV文件
     */
    public static void main(String[] args) {
        try {
            logger.info("开始生成测试数据...");
            TestDataGenerator gen = new TestDataGenerator();
            // 生成账户数据
            List<Account> accounts = gen.generateAccounts();
            gen.writeAccountsToCsv(accounts, "accounts.csv");

            // 生成交易数据
            List<Transaction> transactions = gen.generateTransactions(accounts);
            gen.writeTransactionsToCsv(transactions, "transactions.csv");

            logger.info("测试数据生成完成！");
            logger.info("生成账户数量: {}", accounts.size());
            logger.info("生成交易数量: {}", transactions.size());

        } catch (Exception e) {
            logger.error("生成测试数据时发生错误", e);
        }
    }

    /**
     * 生成账户数据
     */
    private List<Account> generateAccounts() {
        List<Account> accounts = new ArrayList<>(ACCOUNT_COUNT);

        for (int i = 1; i <= ACCOUNT_COUNT; i++) {
            Account account = new Account();
            account.setAccountNumber(String.format("ACC%06d", i)); // ACC000001, ACC000002, ...

            // 生成随机初始余额
            double balance = INITIAL_BALANCE_MIN + (INITIAL_BALANCE_MAX - INITIAL_BALANCE_MIN) * random.nextDouble();
            account.setBalance(Math.round(balance * 100.0) / 100.0); // 保留两位小数

            accounts.add(account);
        }

        logger.info("生成 {} 个账户", accounts.size());
        return accounts;
    }

    /**
     * 生成交易数据
     */
    private List<Transaction> generateTransactions(List<Account> accounts) {
        List<Transaction> transactions = new ArrayList<>(TRANSACTION_COUNT);

        for (int i = 1; i <= TRANSACTION_COUNT; i++) {
            Transaction transaction = new Transaction();
            transaction.setTransactionId("TX" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));

            // 随机选择源账户和目标账户
            Account sourceAccount = accounts.get(random.nextInt(accounts.size()));
            Account destinationAccount = accounts.get(random.nextInt(accounts.size()));

            // 确保源账户和目标账户不同
            while (sourceAccount.getAccountNumber().equals(destinationAccount.getAccountNumber())) {
                destinationAccount = accounts.get(random.nextInt(accounts.size()));
            }

            transaction.setSourceAccount(sourceAccount.getAccountNumber());
            transaction.setDestinationAccount(destinationAccount.getAccountNumber());

            // 生成随机交易金额
            double amount = TRANSACTION_AMOUNT_MIN + (TRANSACTION_AMOUNT_MAX - TRANSACTION_AMOUNT_MIN) * random.nextDouble();
            transaction.setAmount(Math.round(amount * 100.0) / 100.0); // 保留两位小数

            transactions.add(transaction);

            // 每10000条记录输出一次进度
            if (i % 10000 == 0) {
                logger.info("已生成 {} 条交易记录", i);
            }
        }

        logger.info("生成 {} 条交易记录", transactions.size());
        return transactions;
    }

    /**
     * 将账户数据写入CSV文件
     */
    private void writeAccountsToCsv(List<Account> accounts, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 写入CSV头部
            writer.write("id,accountNumber,balance");
            writer.newLine();

            // 写入账户数据
            for (Account account : accounts) {
                writer.write(String.format("%s,%s,%.2f",
                    "", // ID在数据库中自动生成
                    account.getAccountNumber(),
                    account.getBalance()));
                writer.newLine();
            }
        }

        logger.info("账户数据已写入文件: {}", filename);
    }

    /**
     * 将交易数据写入CSV文件
     */
    private void writeTransactionsToCsv(List<Transaction> transactions, String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 写入CSV头部
            writer.write("id,transactionId,sourceAccount,destinationAccount,amount,timestamp");
            writer.newLine();

            // 写入交易数据
            for (Transaction transaction : transactions) {
                writer.write(String.format("%s,%s,%s,%s,%.2f,%s",
                    "", // ID在数据库中自动生成
                    transaction.getTransactionId(),
                    transaction.getSourceAccount(),
                    transaction.getDestinationAccount(),
                    transaction.getAmount(),
                    LocalDateTime.now().format(formatter)));
                writer.newLine();
            }
        }

        logger.info("交易数据已写入文件: {}", filename);
    }

    /**
     * 生成大量交易数据用于压力测试（可选方法）
     */
    public void generateStressTestData(int accountCount, int transactionCount) {
        try {
            logger.info("开始生成压力测试数据... 账户数: {}, 交易数: {}", accountCount, transactionCount);

            // 临时调整参数
            int originalAccountCount = ACCOUNT_COUNT;
            int originalTransactionCount = TRANSACTION_COUNT;

            // 生成数据
            List<Account> accounts = generateAccountsCustom(accountCount);
            writeAccountsToCsv(accounts, "stress_accounts.csv");

            List<Transaction> transactions = generateTransactionsCustom(accounts, transactionCount);
            writeTransactionsToCsv(transactions, "stress_transactions.csv");

            logger.info("压力测试数据生成完成！");

        } catch (Exception e) {
            logger.error("生成压力测试数据时发生错误", e);
        }
    }

    /**
     * 自定义账户数量生成
     */
    private List<Account> generateAccountsCustom(int count) {
        List<Account> accounts = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            Account account = new Account();
            account.setAccountNumber(String.format("ACC%07d", i));

            double balance = INITIAL_BALANCE_MIN + (INITIAL_BALANCE_MAX - INITIAL_BALANCE_MIN) * random.nextDouble();
            account.setBalance(Math.round(balance * 100.0) / 100.0);

            accounts.add(account);
        }

        return accounts;
    }

    /**
     * 自定义交易数量生成
     */
    private List<Transaction> generateTransactionsCustom(List<Account> accounts, int count) {
        List<Transaction> transactions = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            Transaction transaction = new Transaction();
            transaction.setTransactionId("STRESS_TX" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));

            Account sourceAccount = accounts.get(random.nextInt(accounts.size()));
            Account destinationAccount = accounts.get(random.nextInt(accounts.size()));

            while (sourceAccount.getAccountNumber().equals(destinationAccount.getAccountNumber())) {
                destinationAccount = accounts.get(random.nextInt(accounts.size()));
            }

            transaction.setSourceAccount(sourceAccount.getAccountNumber());
            transaction.setDestinationAccount(destinationAccount.getAccountNumber());

            double amount = TRANSACTION_AMOUNT_MIN + (TRANSACTION_AMOUNT_MAX - TRANSACTION_AMOUNT_MIN) * random.nextDouble();
            transaction.setAmount(Math.round(amount * 100.0) / 100.0);

            transactions.add(transaction);

            if (i % 50000 == 0) {
                logger.info("已生成 {} 条压力测试交易记录", i);
            }
        }

        return transactions;
    }
}
