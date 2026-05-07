package br.com.fiap.tc.feedback.function.event;

import br.com.fiap.tc.feedback.application.dto.message.CriticalFeedbackMessage;
import br.com.fiap.tc.feedback.application.dto.message.FeedbackIngestMessage;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.database.TableFeedbackRepository;
import br.com.fiap.tc.feedback.infrastructure.messaging.publisher.AzureQueuePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ProcessFeedbackIngestFunction {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  @FunctionName("processFeedbackIngest")
  public void run(
      @QueueTrigger(
              name = "msg",
              queueName = "%FEEDBACK_INGEST_QUEUE_NAME%",
              connection = "AZURE_STORAGE_CONNECTION_STRING")
          String message,
      final ExecutionContext context) {
    try {
      var payload = MAPPER.readValue(message, FeedbackIngestMessage.class);

      var urgencia = Urgencia.valueOf(payload.urgencia());
      var createdAt = Instant.parse(payload.dataEnvioUtc());

      var repo = jakarta.enterprise.inject.spi.CDI.current().select(TableFeedbackRepository.class).get();
      repo.saveWithId(payload.id(), payload.descricao(), payload.nota(), urgencia, createdAt);
      context.getLogger().info("feedback.persisted id=" + payload.id() + " urgencia=" + payload.urgencia());

      if (urgencia == Urgencia.CRITICA) {
        enfileirarCritico(payload, createdAt);
      }
    } catch (Exception e) {
      context.getLogger().severe("Falha ao processar feedback ingest: " + e.getMessage());
    }
  }

  private static void enfileirarCritico(FeedbackIngestMessage payload, Instant createdAt) {
    var queueName =
        envOrDefault("CRITICAL_FEEDBACK_QUEUE_NAME", AzureQueuePublisher.DEFAULT_CRITICAL_QUEUE);
    try {
      var msg = new CriticalFeedbackMessage(payload.descricao(), payload.urgencia(), TS.format(createdAt));
      var json = MAPPER.writeValueAsString(msg);
      AzureQueuePublisher.fromConnectionString(System.getenv("AZURE_STORAGE_CONNECTION_STRING"), queueName)
          .sendBase64(json);
    } catch (Exception ignored) {
      // Intencional: notificação não deve derrubar o processamento.
    }
  }

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }
}

