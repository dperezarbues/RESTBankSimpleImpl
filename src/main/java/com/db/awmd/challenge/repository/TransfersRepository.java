package com.db.awmd.challenge.repository;

import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.DuplicateTransferIdException;

import java.util.List;

public interface TransfersRepository {

  void createTransfer(Transfer transfer) throws DuplicateTransferIdException;

  Transfer getTransfer(Long transferId);

  List<Transfer> getTransfer();

  void clearTransfers();
}
