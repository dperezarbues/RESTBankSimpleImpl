package com.db.awmd.challenge.exception;

public class DuplicateTransferIdException extends RuntimeException {
  public DuplicateTransferIdException(String message) {
    super(message);
  }
}
