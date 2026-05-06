package com.fiap.tc.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.tc.avaliacao.CriticalFeedbackMessage;
import com.fiap.tc.infra.AcsEmailSender;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import java.time.Instant;

public class ProcessCriticalFeedbackFunction {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @FunctionName("processCriticalFeedback")
  public void run(
      @QueueTrigger(
              name = "msg",
              queueName = "%CRITICAL_FEEDBACK_QUEUE_NAME%",
              connection = "AZURE_STORAGE_CONNECTION_STRING")
          String message,
      final ExecutionContext context) {
    try {
      // message arrives decoded by binding when sender used base64; treat as JSON string.
      var payload = MAPPER.readValue(message, CriticalFeedbackMessage.class);
      enviarEmail(payload);
      context.getLogger().info("Notificação de feedback crítico enviada.");
    } catch (Exception e) {
      context.getLogger().severe("Falha ao processar mensagem crítica: " + e.getMessage());
    }
  }

  private static void enviarEmail(CriticalFeedbackMessage payload) throws Exception {
    var to = System.getenv("ADMIN_EMAIL_TO");
    var from = System.getenv("EMAIL_FROM");
    var acs = System.getenv("ACS_EMAIL_CONNECTION_STRING");
    if (to == null || to.isBlank() || from == null || from.isBlank() || acs == null || acs.isBlank()) {
      return;
    }

    var subject = "Feedback crítico recebido";
    var body =
        """
        Descrição: %s
        Urgência: %s
        Data de envio (UTC): %s
        Processado em (UTC): %s
        """
            .formatted(payload.descricao(), payload.urgencia(), payload.dataEnvioUtc(), Instant.now());
    AcsEmailSender.fromEnv(acs, from).sendPlainText(to, subject, body);
  }
}

