package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.database.TableEmailLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/** Envia e-mail crítico via SMTP e persiste o resultado em {@code emaillogs}. */
@ApplicationScoped
public class CriticalEmailSender {
  private static final Logger LOG = Logger.getLogger(CriticalEmailSender.class);

  @Inject AdminEmailNotifier notifier;
  @Inject TableEmailLogRepository emailLogRepo;

  public void send(String feedbackId, String descricao, Urgencia urgencia, String feedbackCreatedAtIso) {
    LOG.infof("criticalEmail.send.start feedbackId=%s urgencia=%s", feedbackId, urgencia.name());
    var result = notifier.notifyCritical(descricao, urgencia, feedbackCreatedAtIso);
    try {
      emailLogRepo.save(
          feedbackId, descricao, urgencia.name(), feedbackCreatedAtIso, result, Instant.now());
    } catch (Exception e) {
      LOG.errorf(e, "criticalEmail.send.log_failed feedbackId=%s mode=%s", feedbackId, result.mode());
    }
    LOG.infof("criticalEmail.send.done feedbackId=%s mode=%s", feedbackId, result.mode());
  }
}
