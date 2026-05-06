package com.fiap.tc.infra;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class AzureQueuePublisher {
  public static final String DEFAULT_CRITICAL_QUEUE = "critical-feedback";

  private final QueueClient queue;

  public AzureQueuePublisher(QueueClient queue) {
    this.queue = queue;
  }

  public static AzureQueuePublisher fromConnectionString(String connectionString, String queueName) {
    Objects.requireNonNull(connectionString, "AZURE_STORAGE_CONNECTION_STRING is required");
    var client =
        new QueueClientBuilder().connectionString(connectionString).queueName(queueName).buildClient();
    client.createIfNotExists();
    return new AzureQueuePublisher(client);
  }

  public void sendBase64(String utf8Payload) {
    // Storage Queue payload is expected to be Base64 in many SDKs/flows.
    var base64 = Base64.getEncoder().encodeToString(utf8Payload.getBytes(StandardCharsets.UTF_8));
    queue.sendMessage(base64);
  }
}

