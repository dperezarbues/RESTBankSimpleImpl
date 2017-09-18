package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;
import com.db.awmd.challenge.service.TransfersService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class TransfersControllerTest {

  private MockMvc mockMvc;

  @Mock
  private NotificationService notificationService;

  @Captor
  private ArgumentCaptor<Account> notificationRcvrCaptor;

  @Captor
  private ArgumentCaptor<String> notificationTextCaptor;

  @Autowired
  private AccountsService accountsService;

  @Autowired
  private TransfersService transfersService;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Before
  public void prepareMockMvc() throws Exception {
    ReflectionTestUtils.setField(transfersService, "notificationService", notificationService);
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();

    // Reset the existing accounts before each test.
    transfersService.getTransfersRepository().clearTransfers();

    // Create two accounts for transferring funds between them
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"ID-Sender\",\"balance\":1000}"));

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"ID-Receiver\",\"balance\":1000}"));
  }

  @Test
  public void createTransfer() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":200}"))
      .andExpect(status().isCreated()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer transfer = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transfer.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
    assertThat(transfer.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transfer.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transfer.getAmount()).isEqualTo(new BigDecimal(200));
    assertThat(transfer.getFailureCause()).isNull();

    verify(notificationService, times(2)).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    List<Account> capturedAccounts = notificationRcvrCaptor.getAllValues();
    List<String> capturedTexts = notificationTextCaptor.getAllValues();

    assertThat(capturedAccounts.get(0).getAccountId()).isEqualTo("ID-Sender");
    assertThat(capturedAccounts.get(1).getAccountId()).isEqualTo("ID-Receiver");
    assertThat(capturedTexts.get(0)).isEqualTo("You have sent a transfer to Account: ID-Receiver for an amount of 200");
    assertThat(capturedTexts.get(1)).isEqualTo("You have received a transfer from Account: ID-Sender for an amount of 200");

    assertThat(capturedAccounts.get(0).getAccountId()).isEqualTo("ID-Sender");
    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("800");

    account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1200");
  }


  @Test
  public void createConcurrentTransfer() throws Exception {

    int numThreads = 1000;
    CountDownLatch barrier = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    class TransferThread implements Runnable {
      private CountDownLatch cdl;

      private TransferThread(CountDownLatch cdl) {
        this.cdl = cdl;
      }

      @Override
      public void run() {
        try {
          cdl.await();
          mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
            .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":1}"));
        } catch (InterruptedException | BrokenBarrierException e) {
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    for (int i = 0; i < numThreads; i++) {
      executor.execute(new TransferThread(barrier));
    }

    barrier.countDown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
    executor.shutdown();

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("0");

    account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("2000");
  }

  @Test
  public void createTransferNoSenderAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"receiverAccountId\":\"ID-Receiver\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferEmptySenderAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferNotExistingSenderAccountId() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"NotExists\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":200}"))
      .andExpect(status().isBadRequest()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer transfer = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transfer.getStatus()).isEqualTo(Transfer.Status.FAILED);
    assertThat(transfer.getSenderAccountId()).isEqualTo("NotExists");
    assertThat(transfer.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transfer.getAmount()).isEqualTo(new BigDecimal(200));
    assertThat(transfer.getFailureCause()).isEqualTo("Not account found with accountID: NotExists");

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferNoReceiverAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

  }

  @Test
  public void createTransferEmptyReceiverAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

  }

  @Test
  public void createTransferNotExistingReceiverAccountId() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"NotExists\",\"amount\":200}"))
      .andExpect(status().isBadRequest()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer transfer = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transfer.getStatus()).isEqualTo(Transfer.Status.FAILED);
    assertThat(transfer.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transfer.getReceiverAccountId()).isEqualTo("NotExists");
    assertThat(transfer.getAmount()).isEqualTo(new BigDecimal(200));
    assertThat(transfer.getFailureCause()).isEqualTo("Not account found with accountID: NotExists");

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

  }

  @Test
  public void createTransferNoAmount() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\""))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

    account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferNegativeAmount() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":-200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

    account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferNoFunds() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":1200}"))
      .andExpect(status().isBadRequest()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer transfer = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transfer.getStatus()).isEqualTo(Transfer.Status.FAILED);
    assertThat(transfer.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transfer.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transfer.getAmount()).isEqualTo(new BigDecimal(1200));
    assertThat(transfer.getFailureCause()).isEqualTo("Account id: ID-Sender does not have enough funds available!");

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());
    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

    account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferLimitFunds() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":1000}"))
      .andExpect(status().isCreated()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer transfer = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transfer.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
    assertThat(transfer.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transfer.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transfer.getAmount()).isEqualTo(new BigDecimal(1000));
    assertThat(transfer.getFailureCause()).isNull();

    verify(notificationService, times(2)).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());

    List<Account> capturedAccounts = notificationRcvrCaptor.getAllValues();
    List<String> capturedTexts = notificationTextCaptor.getAllValues();

    assertThat(capturedAccounts.get(0).getAccountId()).isEqualTo("ID-Sender");
    assertThat(capturedAccounts.get(1).getAccountId()).isEqualTo("ID-Receiver");
    assertThat(capturedTexts.get(0)).isEqualTo("You have sent a transfer to Account: ID-Receiver for an amount of 1000");
    assertThat(capturedTexts.get(1)).isEqualTo("You have received a transfer from Account: ID-Sender for an amount of 1000");

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("0");

    account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("2000");
  }

  @Test
  public void createTransferNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest());

    verify(notificationService, never()).notifyAboutTransfer(notificationRcvrCaptor.capture(), notificationTextCaptor.capture());
  }

  @Test
  public void getTransfer() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal("200"));
    Long transferId = transfer.getTransferId();
    this.transfersService.createTransfer(transfer);

    MvcResult result = this.mockMvc.perform(get("/v1/transfers/" + transferId)).andExpect(status().isOk()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer transferJSON = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transferJSON.getStatus()).isEqualTo(Transfer.Status.PENDING);
    assertThat(transferJSON.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transferJSON.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transferJSON.getAmount()).isEqualTo(new BigDecimal(200));
    assertThat(transferJSON.getFailureCause()).isNull();

    this.transfersService.executeTransfer(transfer);
    result = this.mockMvc.perform(get("/v1/transfers/" + transferId)).andExpect(status().isOk()).andReturn();

    transferJSON = om.readValue(result.getResponse().getContentAsString(), Transfer.class);
    assertThat(transferJSON.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
    assertThat(transferJSON.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transferJSON.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transferJSON.getAmount()).isEqualTo(new BigDecimal(200));
    assertThat(transferJSON.getFailureCause()).isNull();
  }

  @Test
  public void getTransfers() throws Exception {
    Transfer transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal("100"));
    Long transferId1 = transfer.getTransferId();
    this.transfersService.createTransfer(transfer);
    this.transfersService.executeTransfer(transfer);

    transfer = new Transfer("ID-Sender", "ID-Receiver", new BigDecimal("200"));
    Long transferId2 = transfer.getTransferId();
    this.transfersService.createTransfer(transfer);
    this.transfersService.executeTransfer(transfer);

    MvcResult result = this.mockMvc.perform(get("/v1/transfers/")).andExpect(status().isOk()).andReturn();

    ObjectMapper om = new ObjectMapper();
    //Added ignoring to fail on unknown properties just in case HATEOAS is used
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Transfer[] transferJSON = om.readValue(result.getResponse().getContentAsString(), Transfer[].class);
    Transfer transfer1 = transferJSON[0];
    Transfer transfer2 = transferJSON[1];

    if (transfer1.getTransferId().equals(transferId2)) {
      transfer1 = transfer2;
      transfer2 = transferJSON[0];
    }

    assertThat(transfer1.getTransferId()).isEqualTo(transferId1);
    assertThat(transfer1.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
    assertThat(transfer1.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transfer1.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transfer1.getAmount()).isEqualTo(new BigDecimal(100));
    assertThat(transfer1.getFailureCause()).isNull();

    assertThat(transfer2.getTransferId()).isEqualTo(transferId2);
    assertThat(transfer2.getStatus()).isEqualTo(Transfer.Status.COMPLETED);
    assertThat(transfer2.getSenderAccountId()).isEqualTo("ID-Sender");
    assertThat(transfer2.getReceiverAccountId()).isEqualTo("ID-Receiver");
    assertThat(transfer2.getAmount()).isEqualTo(new BigDecimal(200));
    assertThat(transfer2.getFailureCause()).isNull();


  }
}
