package br.com.fiap.tc.feedback.function.email;

import br.com.fiap.tc.feedback.application.dto.email.SendCriticalEmailRequest;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.email.CriticalEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Azure Function HTTP: envia e-mail de feedback crítico (SendGrid SMTP) e grava log em
 * {@code emaillogs}. Invocada de forma assíncrona por {@link
 * br.com.fiap.tc.feedback.function.http.SubmitFeedbackFunction}.
 */
@ApplicationScoped
public class SendCriticalEmailFunction {
  private static final Logger LOG = Logger.getLogger(SendCriticalEmailFunction.class);

  @Inject ObjectMapper mapper;
  @Inject CriticalEmailSender emailSender;

  @FunctionName("sendCriticalEmail")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              methods = {HttpMethod.POST},
              route = "send-critical-email",
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      final ExecutionContext context) {
    LOG.infof("sendCriticalEmail.start invocationId=%s", context.getInvocationId());
    try {
      var raw = request.getBody().orElse(null);
      if (raw == null || raw.isBlank()) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("body required").build();
      }
      var dto = mapper.readValue(raw, SendCriticalEmailRequest.class);
      if (dto.feedbackId == null
          || dto.descricao == null
          || dto.urgencia == null
          || !Urgencia.CRITICA.name().equals(dto.urgencia)) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body("feedbackId, descricao and urgencia=CRITICA required")
            .build();
      }
      var when = dto.feedbackCreatedAt == null ? "" : dto.feedbackCreatedAt;
      emailSender.send(dto.feedbackId, dto.descricao, Urgencia.CRITICA, when);
      return request.createResponseBuilder(HttpStatus.ACCEPTED).body("accepted").build();
    } catch (Exception e) {
      LOG.errorf(e, "sendCriticalEmail.error invocationId=%s", context.getInvocationId());
      return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("error").build();
    }
  }
}
