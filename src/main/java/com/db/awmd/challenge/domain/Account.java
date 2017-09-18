package com.db.awmd.challenge.domain;

import com.db.awmd.challenge.exception.InsufficientFundsException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class Account {

  @NotNull
  @NotEmpty
  private final String accountId;

  @NotNull
  @Min(value = 0, message = "Initial balance must be positive.")
  private BigDecimal balance;

  public Account(String accountId) {
    this.accountId = accountId;
    this.balance = BigDecimal.ZERO;
  }

  @JsonCreator
  public Account(@JsonProperty("accountId") String accountId,
    @JsonProperty("balance") BigDecimal balance) {
    this.accountId = accountId;
    this.balance = balance;
  }

  synchronized void withdraw (BigDecimal amount) {

    if (balance.compareTo(amount) >= 0) {
      //log.info("Retrieving account for id {}", accountId);
      balance=balance.subtract(amount);
    } else {
      throw new InsufficientFundsException("Account id: " + accountId + " does not have enough funds available!");
    }
  }

  synchronized void deposit (BigDecimal amount) {
    balance=balance.add(amount);
  }

}
