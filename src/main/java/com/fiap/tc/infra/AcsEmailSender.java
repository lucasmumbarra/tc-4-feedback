package com.fiap.tc.infra;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.core.util.polling.SyncPoller;
import java.util.Objects;

public class AcsEmailSender {
  private final EmailClient client;
  private final String from;

  public AcsEmailSender(EmailClient client, String from) {
    this.client = client;
    this.from = from;
  }

  public static AcsEmailSender fromEnv(String connectionString, String from) {
    Objects.requireNonNull(connectionString, "ACS_EMAIL_CONNECTION_STRING is required");
    Objects.requireNonNull(from, "EMAIL_FROM is required");
    var client = new EmailClientBuilder().connectionString(connectionString).buildClient();
    return new AcsEmailSender(client, from);
  }

  public void sendPlainText(String to, String subject, String body) {
    var message =
        new EmailMessage()
            .setSenderAddress(from)
            .setToRecipients(to)
            .setSubject(subject)
            .setBodyPlainText(body);

    SyncPoller<com.azure.communication.email.models.EmailSendResult, com.azure.communication.email.models.EmailSendResult>
        poller = client.beginSend(message, null);
    poller.waitForCompletion();
  }
}

