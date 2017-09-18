package com.db.awmd.challenge.repository;

import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.DuplicateTransferIdException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TransfersRepositoryInMemory implements TransfersRepository {

  private final Map<Long, Transfer> transfers = new ConcurrentHashMap<>();

  @Override
  public void createTransfer(Transfer transfer) throws DuplicateTransferIdException {
    Transfer previousTransfer = transfers.putIfAbsent(transfer.getTransferId(), transfer);
    if (previousTransfer != null) {
      throw new DuplicateTransferIdException(
        "Transfer id " + transfer.getTransferId() + " already exists!");
    }
  }

  @Override
  public Transfer getTransfer(Long transferId) {
    return transfers.get(transferId);
  }

  @Override
  public void clearTransfers() {
    transfers.clear();
  }

  @Override
  public List<Transfer> getTransfer() {
    return new ArrayList<>(transfers.values());
  }

}
