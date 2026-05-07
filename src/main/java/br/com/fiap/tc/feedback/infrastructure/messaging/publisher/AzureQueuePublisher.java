package br.com.fiap.tc.feedback.infrastructure.messaging.publisher;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import java.time.Duration;
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
    var timeoutSeconds = envOrDefaultInt("AZURE_HTTP_TIMEOUT_SECONDS", 10);
    var timeout = Duration.ofSeconds(timeoutSeconds);
    var client =
        new QueueClientBuilder()
            .connectionString(connectionString)
            .queueName(queueName)
            .httpClient(new NettyAsyncHttpClientBuilder().responseTimeout(timeout).build())
            .buildClient();
    client.createIfNotExists();
    return new AzureQueuePublisher(client);
  }

  public void sendBase64(String utf8Payload) {
    // Storage Queue payload is expected to be Base64 in many SDKs/flows.
    var base64 = Base64.getEncoder().encodeToString(utf8Payload.getBytes(StandardCharsets.UTF_8));
    queue.sendMessage(base64);
  }

  private static int envOrDefaultInt(String name, int def) {
    var v = System.getenv(name);
    if (v == null || v.isBlank()) return def;
    try {
      return Integer.parseInt(v.trim());
    } catch (Exception ignored) {
      return def;
    }
  }
}

