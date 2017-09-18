package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.InsufficientFundsException;
import com.db.awmd.challenge.service.TransfersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

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
  public @ResponseBody ResponseEntity<Resource<Transfer>> executeTransfer(@RequestBody @Valid Transfer transfer ) {
    log.info("Creating transfer {}", transfer);

    try {
      this.transfersService.createTransfer(transfer);
      this.transfersService.executeTransfer(transfer);
    } catch (InsufficientFundsException | AccountNotFoundException te) {
      return new ResponseEntity<>(buildTransferResource(transfer), HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>(buildTransferResource(transfer), HttpStatus.CREATED);

  }

  @GetMapping(path = "/{transferId}")
  public @ResponseBody Resource<Transfer> getTransfer(@PathVariable Long transferId) {
    log.info("Retrieving transfer for id {}", transferId);

    return buildTransferResource(this.transfersService.getTransfer(transferId));
  }

  @GetMapping
  public @ResponseBody
  List<Resource<Transfer>> getTransfers() {
    List<Resource<Transfer>> resources = new ArrayList<Resource<Transfer>>();
    for (Transfer transfer:this.transfersService.getTransfer()){
      resources.add(buildTransferResource(transfer));
    }
    return resources;
  }


  private Resource<Transfer> buildTransferResource (Transfer transfer){
    Resource<Transfer> resource = new Resource<Transfer>(transfer);
    resource.add(linkTo(methodOn(TransfersController.class).getTransfer(transfer.getTransferId())).withSelfRel());
    resource.add(linkTo(methodOn(AccountsController.class).getAccount(transfer.getReceiverAccountId())).withRel("receiver"));
    resource.add(linkTo(methodOn(AccountsController.class).getAccount(transfer.getSenderAccountId())).withRel("sender"));

    return resource;
  }
}
