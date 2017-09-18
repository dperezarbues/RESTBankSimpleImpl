package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transfer;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;
import com.db.awmd.challenge.service.TransfersService;
import org.json.JSONArray;
import org.json.JSONObject;
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


    JSONObject transfer = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transfer.getString("status")).isEqualTo("Completed");
    assertThat(transfer.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transfer.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transfer.getLong("amount")).isEqualTo(200L);
    assertThat(transfer.getString("failureCause")).isEqualTo("null");

    verify(notificationService,times(2)).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

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

    int numThreads=1000;
    CountDownLatch barrier = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    class TransferThread implements Runnable {
      CountDownLatch cdl;

      public TransferThread(CountDownLatch cdl){this.cdl=cdl;}

      @Override
      public void run(){
        try {
          cdl.await();
          mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                  .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":1}"));
        } catch (InterruptedException e) {
          e.printStackTrace();
        } catch (BrokenBarrierException e) {
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    for(int i=0; i<numThreads; i++) {
      executor.execute(new TransferThread(barrier));
    }

    barrier.countDown();
    executor.awaitTermination(5,TimeUnit.SECONDS);
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

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferEmptySenderAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferNotExistingSenderAccountId() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
            .content("{\"senderAccountId\":\"NotExists\",\"receiverAccountId\":\"ID-Receiver\",\"amount\":200}"))
            .andExpect(status().isBadRequest()).andReturn();


    JSONObject transfer = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transfer.getString("status")).isEqualTo("Failed");
    assertThat(transfer.getString("senderAccountId")).isEqualTo("NotExists");
    assertThat(transfer.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transfer.getLong("amount")).isEqualTo(200L);
    assertThat(transfer.getString("failureCause")).isEqualTo("Not account found with accountID: NotExists");

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Receiver");
    assertThat(account.getAccountId()).isEqualTo("ID-Receiver");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createTransferNoReceiverAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

  }

  @Test
  public void createTransferEmptyReceiverAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"\",\"amount\":200}"))
      .andExpect(status().isBadRequest());

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

  }

  @Test
  public void createTransferNotExistingReceiverAccountId() throws Exception {
    MvcResult result = this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
            .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"NotExists\",\"amount\":200}"))
            .andExpect(status().isBadRequest()).andReturn();

    JSONObject transfer = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transfer.getString("status")).isEqualTo("Failed");
    assertThat(transfer.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transfer.getString("receiverAccountId")).isEqualTo("NotExists");
    assertThat(transfer.getLong("amount")).isEqualTo(200L);
    assertThat(transfer.getString("failureCause")).isEqualTo("Not account found with accountID: NotExists");

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

    Account account = accountsService.getAccount("ID-Sender");
    assertThat(account.getAccountId()).isEqualTo("ID-Sender");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");

  }

  @Test
  public void createTransferNoAmount() throws Exception {
    this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
      .content("{\"senderAccountId\":\"ID-Sender\",\"receiverAccountId\":\"ID-Receiver\""))
      .andExpect(status().isBadRequest());

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

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

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

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

    JSONObject transfer = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transfer.getString("status")).isEqualTo("Failed");
    assertThat(transfer.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transfer.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transfer.getLong("amount")).isEqualTo(1200L);
    assertThat(transfer.getString("failureCause")).isEqualTo("Account id: ID-Sender does not have enough funds available!");

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());
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

    JSONObject transfer = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transfer.getString("status")).isEqualTo("Completed");
    assertThat(transfer.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transfer.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transfer.getLong("amount")).isEqualTo(1000L);
    assertThat(transfer.getString("failureCause")).isEqualTo("null");


    verify(notificationService,times(2)).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());

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

    verify(notificationService,never()).notifyAboutTransfer(notificationRcvrCaptor.capture(),notificationTextCaptor.capture());
  }

  @Test
  public void getTransfer() throws Exception {
    Transfer transfer = new Transfer("ID-Sender","ID-Receiver", new BigDecimal("200"));
    Long transferId= transfer.getTransferId();
    this.transfersService.createTransfer(transfer);

    MvcResult result = this.mockMvc.perform(get("/v1/transfers/" + transferId)).andExpect(status().isOk()).andReturn();

    JSONObject transferJSON = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transferJSON.getString("status")).isEqualTo("Pending");
    assertThat(transferJSON.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transferJSON.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transferJSON.getLong("amount")).isEqualTo(200L);
    assertThat(transferJSON.getString("failureCause")).isEqualTo("null");

    this.transfersService.executeTransfer(transfer);
    result = this.mockMvc.perform(get("/v1/transfers/" + transferId)).andExpect(status().isOk()).andReturn();

    transferJSON = new JSONObject(result.getResponse().getContentAsString());
    assertThat(transferJSON.getString("status")).isEqualTo("Completed");
    assertThat(transferJSON.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transferJSON.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transferJSON.getLong("amount")).isEqualTo(200L);
    assertThat(transferJSON.getString("failureCause")).isEqualTo("null");
  }

  @Test
  public void getTransfers() throws Exception {
    Transfer transfer = new Transfer("ID-Sender","ID-Receiver", new BigDecimal("100"));
    Long transferId1= transfer.getTransferId();
    this.transfersService.createTransfer(transfer);
    this.transfersService.executeTransfer(transfer);

    transfer = new Transfer("ID-Sender","ID-Receiver", new BigDecimal("200"));
    Long transferId2= transfer.getTransferId();
    this.transfersService.createTransfer(transfer);
    this.transfersService.executeTransfer(transfer);

    MvcResult result = this.mockMvc.perform(get("/v1/transfers/")).andExpect(status().isOk()).andReturn();

    JSONArray transferJSON = new JSONArray(result.getResponse().getContentAsString());
    JSONObject transfer1 = (JSONObject)transferJSON.get(0);
    JSONObject transfer2 = (JSONObject)transferJSON.get(1);

    if (transfer1.getLong("transferId")==transferId2){
      transfer1=transfer2;
      transfer2=(JSONObject)transferJSON.get(0);
    }

    assertThat(transfer1.getLong("transferId")).isEqualTo(transferId1);
    assertThat(transfer1.getString("status")).isEqualTo("Completed");
    assertThat(transfer1.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transfer1.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transfer1.getLong("amount")).isEqualTo(100L);
    assertThat(transfer1.getString("failureCause")).isEqualTo("null");

    assertThat(transfer2.getLong("transferId")).isEqualTo(transferId2);
    assertThat(transfer2.getString("status")).isEqualTo("Completed");
    assertThat(transfer2.getString("senderAccountId")).isEqualTo("ID-Sender");
    assertThat(transfer2.getString("receiverAccountId")).isEqualTo("ID-Receiver");
    assertThat(transfer2.getLong("amount")).isEqualTo(200L);
    assertThat(transfer2.getString("failureCause")).isEqualTo("null");

  }
}
