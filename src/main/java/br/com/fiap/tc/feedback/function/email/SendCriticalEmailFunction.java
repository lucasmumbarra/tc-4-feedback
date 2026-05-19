package br.com.fiap.tc.feedback.function.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.database.TableEmailLogRepository;
import br.com.fiap.tc.feedback.infrastructure.email.AdminEmailNotifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Serviço interno (não aparece como função separada no portal Azure). É invocado por {@link
 * br.com.fiap.tc.feedback.function.http.SubmitFeedbackFunction} quando a urgência é {@code
 * CRITICA}. Envia e-mail via SendGrid SMTP e persiste o resultado na tabela {@code emaillogs}.
 */
@ApplicationScoped
public class SendCriticalEmailFunction {
  private static final Logger LOG = Logger.getLogger(SendCriticalEmailFunction.class);

  @Inject AdminEmailNotifier notifier;
  @Inject TableEmailLogRepository emailLogRepo;

  public void sendForCriticalFeedback(
      String feedbackId, String descricao, Urgencia urgencia, String feedbackCreatedAtIso) {
    LOG.infof("sendCriticalEmail.start feedbackId=%s urgencia=%s", feedbackId, urgencia.name());
    var result = notifier.notifyCritical(descricao, urgencia, feedbackCreatedAtIso);
    try {
      emailLogRepo.save(
          feedbackId, descricao, urgencia.name(), feedbackCreatedAtIso, result, Instant.now());
    } catch (Exception e) {
      LOG.errorf(e, "sendCriticalEmail.log_failed feedbackId=%s mode=%s", feedbackId, result.mode());
    }
    LOG.infof("sendCriticalEmail.done feedbackId=%s mode=%s", feedbackId, result.mode());
  }
}
