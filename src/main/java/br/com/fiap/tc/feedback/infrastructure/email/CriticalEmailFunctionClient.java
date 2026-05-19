package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.application.dto.email.SendCriticalEmailRequest;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.function.email.SendCriticalEmailFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CriticalEmailFunctionClient {
  private static final Logger LOG = Logger.getLogger(CriticalEmailFunctionClient.class);

  @Inject SendCriticalEmailFunction sendCriticalEmailFunction;

  public void invoke(
      String feedbackId, String descricao, Urgencia urgencia, String feedbackCreatedAtIso) {
    var dto = new SendCriticalEmailRequest();
    dto.feedbackId = feedbackId;
    dto.descricao = descricao;
    dto.urgencia = urgencia.name();
    dto.feedbackCreatedAt = feedbackCreatedAtIso;
    try {
      sendCriticalEmailFunction.process(dto);
      LOG.infof("criticalEmail.invoke_direct feedbackId=%s", feedbackId);
    } catch (Exception e) {
      LOG.errorf(e, "criticalEmail.invoke_direct_failed feedbackId=%s", feedbackId);
    }
  }
}
