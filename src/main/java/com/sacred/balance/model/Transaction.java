package com.sacred.balance.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transactions",
       indexes = {
           @Index(name = "idx_transaction_id", columnList = "transactionId"),
           @Index(name = "idx_source_account", columnList = "sourceAccount"),
           @Index(name = "idx_destination_account", columnList = "destinationAccount"),
           @Index(name = "idx_timestamp", columnList = "timestamp")
       })
public class Transaction implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transactionId")
    private String transactionId;

    @Column(name = "sourceAccount")
    private String sourceAccount;

    @Column(name = "destinationAccount")
    private String destinationAccount;

    @Column(name = "amount")
    private double amount;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private LocalDateTime timestamp;

    public Transaction() {}

}
