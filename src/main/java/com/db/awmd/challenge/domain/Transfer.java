package com.db.awmd.challenge.domain;

import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class Transfer {

  @JsonIgnore
  private static AtomicLong counter= new AtomicLong(0);

  public static enum Status {
    COMPLETED ("Completed"),
    PENDING ("Pending"),
    FAILED ("Failed");
    private String description;
    private Status (String description) {this.description=description;}

    @JsonValue
    public String getDescription(){return this.description;}
  }

  @NotNull
  @Setter(AccessLevel.NONE)
  private final Long transferId;

  @NotNull
  @NotEmpty
  private final String senderAccountId;

  @NotNull
  @NotEmpty
  private final String receiverAccountId;

  @NotNull
  @Min(value = 0, message = "Amount to transfer must be positive.")
  private final BigDecimal amount;

  @Setter(AccessLevel.NONE)
  private Status status;

  @Setter(AccessLevel.NONE)
  private String failureCause;

  @JsonIgnore
  private Account receiverAccount;

  @JsonIgnore
  private Account senderAccount;

  @JsonCreator
  public Transfer(@JsonProperty("senderAccountId") String senderAccountId,
                  @JsonProperty("receiverAccountId") String receiverAccountId,
                  @JsonProperty("amount") BigDecimal amount) {

    this.transferId=counter.addAndGet(1);
    this.senderAccountId = senderAccountId;
    this.receiverAccountId = receiverAccountId;
    this.amount = amount;
    this.status = Status.PENDING;

  }

  public void setReceiverAccount(Account receiverAccount) {
    if (this.receiverAccount == null){this.receiverAccount=receiverAccount;}
  }

  public void setSenderAccount(Account senderAccount) {
    if (this.senderAccount == null){this.senderAccount=senderAccount;}
  }

  public void execute() {
    try {
      if (this.status == Status.PENDING){
        if (this.getSenderAccount() == null){
          throw new AccountNotFoundException("Not account found with accountID: " + this.getSenderAccountId());
        }
        if (this.getReceiverAccount() == null){
          throw new AccountNotFoundException("Not account found with accountID: " + this.getReceiverAccountId());
        }
        senderAccount.withdraw(amount);
        receiverAccount.deposit(amount);
        this.status=Status.COMPLETED;
      } else {
        failureCause="Retrying an already " + status.getDescription() + " transfer";
      }
    } catch (RuntimeException r) {
      failureCause=r.getMessage();
      throw r;
    } finally {
      if (this.status != Status.COMPLETED){
        this.status = Status.FAILED;
      }
    }


  }

}
