package com.sacred.balance.service;

import com.sacred.balance.model.Account;
import com.sacred.balance.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Cacheable(value = "accounts", key = "#accountNumber")
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }


    @CachePut(value = "accounts", key = "#account.accountNumber")
    public Account updateAccount(Account account) {
        return accountRepository.save(account);
    }

}
