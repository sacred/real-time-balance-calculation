package com.sacred.balance.model;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "accounts",
       indexes = {
           @Index(name = "idx_account_number", columnList = "accountNumber", unique = true)
       })
public class Account implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "accountNumber", unique = true)
    private String accountNumber;

    @Column(name = "balance")
    private double balance;

    public Account() {}

    public Account(String accountNumber, double balance) {
        this.accountNumber = accountNumber;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
