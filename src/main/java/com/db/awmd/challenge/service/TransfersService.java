package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Transfer;
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
  public TransfersService(AccountsRepository accountsRepository,TransfersRepository transfersRepository,NotificationService notificationService) {
    this.accountsRepository = accountsRepository;
    this.transfersRepository = transfersRepository;
    this.notificationService = notificationService;
  }

  public void executeTransfer(Transfer transfer) {

    transfer.setSenderAccount(this.accountsRepository.getAccount(transfer.getSenderAccountId()));
    transfer.setReceiverAccount(this.accountsRepository.getAccount(transfer.getReceiverAccountId()));

    transfer.execute();
    if (transfer.getStatus()== Transfer.Status.COMPLETED) {
      notificationService.notifyAboutTransfer(transfer.getSenderAccount(), "You have sent a transfer " +
              "to Account: " + transfer.getReceiverAccountId() + " for an amount of " + transfer.getAmount());

      notificationService.notifyAboutTransfer(transfer.getReceiverAccount(), "You have received a transfer " +
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
