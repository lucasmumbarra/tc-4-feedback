package br.com.fiap.tc.feedback.function.queue;

import br.com.fiap.tc.feedback.application.dto.messaging.CriticalFeedbackMessage;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.email.AdminEmailNotifier;
import br.com.fiap.tc.feedback.infrastructure.messaging.FeedbackQueueNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessCriticalFeedbackFunction {
  private static final Logger LOG = Logger.getLogger(ProcessCriticalFeedbackFunction.class);

  @Inject ObjectMapper mapper;
  @Inject AdminEmailNotifier notifier;

  @FunctionName("processCriticalFeedback")
  public void process(
      @QueueTrigger(
              name = "msg",
              queueName = FeedbackQueueNames.CRITICAL_FEEDBACK,
              connection = "DataStorage")
          String rawMessage,
      final ExecutionContext context) {
    LOG.infof(
        "processCriticalFeedback.start invocationId=%s msgLen=%d",
        context.getInvocationId(),
        rawMessage == null ? 0 : rawMessage.length());
    try {
      var m = mapper.readValue(rawMessage, CriticalFeedbackMessage.class);
      if (m == null || m.urgencia == null || !Urgencia.CRITICA.name().equals(m.urgencia)) {
        LOG.warnf(
            "processCriticalFeedback.skip id=%s urgencia=%s (esperado %s; confirme JSON da mensagem na fila)",
            m == null ? "null" : m.id,
            m == null ? "null" : m.urgencia,
            Urgencia.CRITICA.name());
        return;
      }
      var desc = m.descricao == null ? "" : m.descricao;
      var when = m.createdAt == null ? "" : m.createdAt;
      notifier.notifyCritical(desc, Urgencia.CRITICA, when);
      LOG.infof("processCriticalFeedback.done id=%s", m.id);
    } catch (Exception e) {
      LOG.errorf(e, "processCriticalFeedback.error");
      throw new RuntimeException(e);
    }
  }
}
