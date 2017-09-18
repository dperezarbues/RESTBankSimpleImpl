package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.InsufficientFundsException;
import com.db.awmd.challenge.service.TransfersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/transfers")
@Slf4j
public class TransfersController {

  private final TransfersService transfersService;

  @Autowired
  public TransfersController(TransfersService transfersService) {
    this.transfersService = transfersService;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public @ResponseBody
  ResponseEntity<Transfer> executeTransfer(@RequestBody @Valid Transfer transfer) {
    log.info("Creating transfer {}", transfer);

    try {
      this.transfersService.createTransfer(transfer);
      this.transfersService.executeTransfer(transfer);
    } catch (InsufficientFundsException | AccountNotFoundException te) {
      return new ResponseEntity<>(transfer, HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>(transfer, HttpStatus.CREATED);

  }

  @GetMapping(path = "/{transferId}")
  public @ResponseBody
  Transfer getTransfer(@PathVariable Long transferId) {
    log.info("Retrieving transfer for id {}", transferId);

    return this.transfersService.getTransfer(transferId);
  }

  @GetMapping
  public @ResponseBody
  List<Transfer> getTransfers() {
    return this.transfersService.getTransfer();
  }

}
