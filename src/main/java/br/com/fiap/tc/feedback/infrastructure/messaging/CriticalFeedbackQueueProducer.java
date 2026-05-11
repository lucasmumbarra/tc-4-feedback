package br.com.fiap.tc.feedback.infrastructure.messaging;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CriticalFeedbackQueueProducer {
  private static final Logger LOG = Logger.getLogger(CriticalFeedbackQueueProducer.class);

  private final ObjectMapper mapper;
  private volatile QueueClient queueClient;
  private volatile String queueName;

  @Inject
  public CriticalFeedbackQueueProducer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void enqueueJson(String jsonBody) {
    if (jsonBody == null || jsonBody.isBlank()) {
      return;
    }
    try {
      client().sendMessage(jsonBody);
      LOG.infof("criticalQueue.enqueued bytes=%d", jsonBody.length());
    } catch (Exception e) {
      LOG.errorf(e, "criticalQueue.enqueue_failed");
      throw new RuntimeException("Failed to enqueue critical feedback message", e);
    }
  }

  private QueueClient client() {
    if (queueClient != null) {
      return queueClient;
    }
    synchronized (this) {
      if (queueClient != null) {
        return queueClient;
      }
      var conn = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
      Objects.requireNonNull(conn, "AZURE_STORAGE_CONNECTION_STRING is required");
      queueName = envOrDefault("CRITICAL_FEEDBACK_QUEUE_NAME", "critical-feedback");
      queueClient =
          new QueueClientBuilder().connectionString(conn).queueName(queueName).buildClient();
      queueClient.createIfNotExists();
      LOG.infof("criticalQueue.init queue=%s", queueName);
      return queueClient;
    }
  }

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }
}
