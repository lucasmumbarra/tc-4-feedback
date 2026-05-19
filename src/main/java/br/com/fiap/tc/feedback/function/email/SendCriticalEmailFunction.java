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

@ApplicationScoped
public class SendCriticalEmailFunction {
  private static final Logger LOG = Logger.getLogger(SendCriticalEmailFunction.class);

  @Inject ObjectMapper mapper;
  @Inject CriticalEmailSender emailSender;

  public void process(SendCriticalEmailRequest dto) {
    validate(dto);
    var when = dto.feedbackCreatedAt == null ? "" : dto.feedbackCreatedAt;
    LOG.infof("sendCriticalEmail.process.start feedbackId=%s", dto.feedbackId);
    emailSender.send(dto.feedbackId, dto.descricao, Urgencia.CRITICA, when);
    LOG.infof("sendCriticalEmail.process.done feedbackId=%s", dto.feedbackId);
  }

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
      process(dto);
      return request.createResponseBuilder(HttpStatus.ACCEPTED).body("accepted").build();
    } catch (IllegalArgumentException e) {
      return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body(e.getMessage()).build();
    } catch (Exception e) {
      LOG.errorf(e, "sendCriticalEmail.error invocationId=%s", context.getInvocationId());
      return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("error").build();
    }
  }

  private static void validate(SendCriticalEmailRequest dto) {
    if (dto == null
        || dto.feedbackId == null
        || dto.descricao == null
        || dto.urgencia == null
        || !Urgencia.CRITICA.name().equals(dto.urgencia)) {
      throw new IllegalArgumentException("feedbackId, descricao and urgencia=CRITICA required");
    }
  }
}
