package com.sacred.balance.service;

import com.sacred.balance.model.Account;
import com.sacred.balance.model.Transaction;
import com.sacred.balance.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class TransactionService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;


    @Transactional
    public boolean processTransaction(Transaction transaction) {
        Optional<Account> sourceOpt = accountService.findByAccountNumber(transaction.getSourceAccount());
        Optional<Account> destOpt = accountService.findByAccountNumber(transaction.getDestinationAccount());

        if (sourceOpt.isEmpty() || destOpt.isEmpty()) {
            return false;
        }

        Account source = sourceOpt.get();
        Account dest = destOpt.get();

        if (source.getBalance() < transaction.getAmount()) {
            return false;
        }

        source.setBalance(source.getBalance() - transaction.getAmount());
        dest.setBalance(dest.getBalance() + transaction.getAmount());

        accountService.updateAccount(source);
        accountService.updateAccount(dest);

        transactionRepository.save(transaction);
        return true;
    }
}
