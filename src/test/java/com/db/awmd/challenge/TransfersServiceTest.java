package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.DuplicateTransferIdException;
import com.db.awmd.challenge.exception.InsufficientFundsException;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.TransfersService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TransfersServiceTest {

  @Autowired
  private TransfersService transfersService;

  @Autowired
  private AccountsService accountsService;

  @Before
  public void prepareAccounts() {

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();

    Account account = new Account("ID-Sender");
    account.setBalance(new BigDecimal(1000));
    this.accountsService.createAccount(account);

    account = new Account("ID-Receiver");
    account.setBalance(new BigDecimal(1000));
    this.accountsService.createAccount(account);
  }

  @Test
  public void createTransfer() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal(100));
    Long transferId = transfer.getTransferId();
    this.transfersService.createTransfer(transfer);

    assertThat(this.transfersService.getTransfer(transferId).getAmount()).isEqualTo(new BigDecimal(100));
    assertThat(this.transfersService.getTransfer(transferId).getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(this.transfersService.getTransfer(transferId).getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(this.transfersService.getTransfer(transferId).getTransferId()).isEqualTo(transferId);
    assertThat(this.transfersService.getTransfer(transferId).getStatus()).isEqualTo(Transfer.Status.PENDING);

    assertThat(this.accountsService.getAccount("ID-Receiver").getBalance()).isEqualTo(new BigDecimal(1000));
    assertThat(this.accountsService.getAccount("ID-Sender").getBalance()).isEqualTo(new BigDecimal(1000));
  }

  @Test
  public void executeTransfer() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal(100));
    Long transferId = transfer.getTransferId();
    this.transfersService.createTransfer(transfer);
    this.transfersService.executeTransfer(transfer);

    assertThat(this.transfersService.getTransfer(transferId).getAmount()).isEqualTo(new BigDecimal(100));
    assertThat(this.transfersService.getTransfer(transferId).getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(this.transfersService.getTransfer(transferId).getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(this.transfersService.getTransfer(transferId).getTransferId()).isEqualTo(transferId);
    assertThat(this.transfersService.getTransfer(transferId).getStatus()).isEqualTo(Transfer.Status.COMPLETED);

    assertThat(this.accountsService.getAccount("ID-Receiver").getBalance()).isEqualTo(new BigDecimal(1100));
    assertThat(this.accountsService.getAccount("ID-Sender").getBalance()).isEqualTo(new BigDecimal(900));
  }

  @Test
  public void createDuplicatedTransfer() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal(100));
    Long transferId = transfer.getTransferId();

    try {
      this.transfersService.createTransfer(transfer);
      this.transfersService.createTransfer(transfer);
      fail("Should have failed when trying to transfer more funds than available");
    } catch (DuplicateTransferIdException ex) {
      assertThat(ex.getMessage()).isEqualTo("Transfer id " + transferId + " already exists!");
    }

    assertThat(this.transfersService.getTransfer(transferId).getAmount()).isEqualTo(new BigDecimal(100));
    assertThat(this.transfersService.getTransfer(transferId).getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(this.transfersService.getTransfer(transferId).getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(this.transfersService.getTransfer(transferId).getTransferId()).isEqualTo(transferId);
    assertThat(this.transfersService.getTransfer(transferId).getStatus()).isEqualTo(Transfer.Status.PENDING);

    assertThat(this.accountsService.getAccount("ID-Receiver").getBalance()).isEqualTo(new BigDecimal(1000));
    assertThat(this.accountsService.getAccount("ID-Sender").getBalance()).isEqualTo(new BigDecimal(1000));
  }

  @Test
  public void executeTransferNoFunds() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal(1100));
    Long transferId = transfer.getTransferId();

    try {
      this.transfersService.createTransfer(transfer);
      this.transfersService.executeTransfer(transfer);
      this.transfersService.executeTransfer(transfer);
      fail("Should have failed when trying to insert a duplicated transfer into the pool");
    } catch (InsufficientFundsException ex) {
      assertThat(ex.getMessage()).isEqualTo("Account id: ID-Sender does not have enough funds available!");
    }

    assertThat(this.transfersService.getTransfer(transferId).getAmount()).isEqualTo(new BigDecimal(1100));
    assertThat(this.transfersService.getTransfer(transferId).getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(this.transfersService.getTransfer(transferId).getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(this.transfersService.getTransfer(transferId).getTransferId()).isEqualTo(transferId);
    assertThat(this.transfersService.getTransfer(transferId).getStatus()).isEqualTo(Transfer.Status.FAILED);

    assertThat(this.accountsService.getAccount("ID-Receiver").getBalance()).isEqualTo(new BigDecimal(1000));
    assertThat(this.accountsService.getAccount("ID-Sender").getBalance()).isEqualTo(new BigDecimal(1000));
  }

  @Test
  public void createNotExistingAccountTransfer() throws Exception {
    Transfer transfer = new Transfer("NOT_EXISTS", "ID-Receiver", new BigDecimal(100));
    Long transferId = transfer.getTransferId();

    try {
      this.transfersService.createTransfer(transfer);
      this.transfersService.executeTransfer(transfer);
      fail("Should have failed when trying to execute transfer from a not existing account");
    } catch (AccountNotFoundException ex) {
      assertThat(ex.getMessage()).isEqualTo("Not account found with accountID: NOT_EXISTS");
    }

    assertThat(this.transfersService.getTransfer(transferId).getAmount()).isEqualTo(new BigDecimal(100));
    assertThat(this.transfersService.getTransfer(transferId).getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(this.transfersService.getTransfer(transferId).getSenderAccountId()).isEqualTo("NOT_EXISTS");
    assertThat(this.transfersService.getTransfer(transferId).getTransferId()).isEqualTo(transferId);
    assertThat(this.transfersService.getTransfer(transferId).getStatus()).isEqualTo(Transfer.Status.FAILED);

    assertThat(this.accountsService.getAccount("ID-Receiver").getBalance()).isEqualTo(new BigDecimal(1000));
    assertThat(this.accountsService.getAccount("ID-Sender").getBalance()).isEqualTo(new BigDecimal(1000));
  }

  @Test
  public void executeTwiceTransfer() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal(100));
    Long transferId = transfer.getTransferId();

    try {
      this.transfersService.createTransfer(transfer);
      this.transfersService.executeTransfer(transfer);
      this.transfersService.executeTransfer(transfer);

    } catch (RuntimeException ex) {
      fail("Should not have failed when trying to execute for the second time the same transfer");
    }

    assertThat(this.transfersService.getTransfer(transferId).getAmount()).isEqualTo(new BigDecimal(100));
    assertThat(this.transfersService.getTransfer(transferId).getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(this.transfersService.getTransfer(transferId).getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(this.transfersService.getTransfer(transferId).getTransferId()).isEqualTo(transferId);
    assertThat(this.transfersService.getTransfer(transferId).getStatus()).isEqualTo(Transfer.Status.COMPLETED);
    assertThat(this.transfersService.getTransfer(transferId).getFailureCause()).isEqualTo("Retrying an already Completed transfer");

    assertThat(this.accountsService.getAccount("ID-Receiver").getBalance()).isEqualTo(new BigDecimal(1100));
    assertThat(this.accountsService.getAccount("ID-Sender").getBalance()).isEqualTo(new BigDecimal(900));
  }

}
