package com.sacred.balance.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
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

}
