package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.repository.AccountsRepository;
import com.db.awmd.challenge.repository.TransfersRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransfersService {

  @Getter
  private final AccountsRepository accountsRepository;

  @Getter
  private final TransfersRepository transfersRepository;

  @Getter
  private final NotificationService notificationService;

  @Autowired
  public TransfersService(AccountsRepository accountsRepository, TransfersRepository transfersRepository, NotificationService notificationService) {
    this.accountsRepository = accountsRepository;
    this.transfersRepository = transfersRepository;
    this.notificationService = notificationService;
  }

  public void executeTransfer(Transfer transfer) {

    Account senderAccount = this.accountsRepository.getAccount(transfer.getSenderAccountId());
    Account receiverAccount = this.accountsRepository.getAccount(transfer.getReceiverAccountId());

    try {
      if (transfer.getStatus() == Transfer.Status.PENDING) {
        if (senderAccount == null) {
          throw new AccountNotFoundException("Not account found with accountID: " + transfer.getSenderAccountId());
        }
        if (receiverAccount == null) {
          throw new AccountNotFoundException("Not account found with accountID: " + transfer.getReceiverAccountId());
        }
        senderAccount.withdraw(transfer.getAmount());
        receiverAccount.deposit(transfer.getAmount());
        transfer.setStatus(Transfer.Status.COMPLETED);
      } else {
        transfer.setFailureCause("Retrying an already " + transfer.getStatus().getDescription() + " transfer");
      }
    } catch (RuntimeException r) {
      transfer.setFailureCause(r.getMessage());
      throw r;
    } finally {
      if (transfer.getStatus() != Transfer.Status.COMPLETED) {
        transfer.setStatus(Transfer.Status.FAILED);
      }
    }

    if (transfer.getStatus() == Transfer.Status.COMPLETED) {
      notificationService.notifyAboutTransfer(senderAccount, "You have sent a transfer " +
        "to Account: " + transfer.getReceiverAccountId() + " for an amount of " + transfer.getAmount());

      notificationService.notifyAboutTransfer(receiverAccount, "You have received a transfer " +
        "from Account: " + transfer.getSenderAccountId() + " for an amount of " + transfer.getAmount());
    }
  }

  public void createTransfer(Transfer transfer) {
    this.transfersRepository.createTransfer(transfer);
  }

  public Transfer getTransfer(Long transferId) {
    return this.transfersRepository.getTransfer(transferId);
  }

  public List<Transfer> getTransfer() {
    return this.transfersRepository.getTransfer();
  }
}
