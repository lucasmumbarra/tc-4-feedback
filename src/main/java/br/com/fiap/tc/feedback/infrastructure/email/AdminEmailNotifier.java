package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.core.util.Context;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import org.jboss.logging.Logger;

/**
 * Notifica administradores sobre feedback crítico via <strong>Azure Communication Services —
 * Email</strong>. Requer connection string do recurso ACS com Email ativo e endereço de remetente
 * já associado (domínio Azure ou domínio verificado). Sem configuração, o conteúdo é apenas
 * registado nos logs.
 */
@ApplicationScoped
public class AdminEmailNotifier {
  private static final Logger LOG = Logger.getLogger(AdminEmailNotifier.class);

  private volatile EmailClient emailClient;

  public void notifyCritical(String descricao, Urgencia urgencia, String dataEnvioIso) {
    var bodyText = buildPlainBody(descricao, urgencia, dataEnvioIso);
    var conn = emailConnectionString();
    var adminTo = getenv("ADMIN_NOTIFY_EMAIL");
    var from = getenv("NOTIFY_FROM_EMAIL");
    if (conn == null || adminTo == null || from == null) {
      LOG.warnf(
          "notifyCritical.simulated ACS_EMAIL_CONNECTION_STRING (ou AZURE_COMMUNICATION_CONNECTION_STRING), "
              + "ADMIN_NOTIFY_EMAIL e NOTIFY_FROM_EMAIL são necessários para envio real; body=%n%s",
          bodyText);
      return;
    }
    try {
      var client = emailClient(conn);
      var message =
          new EmailMessage()
              .setSenderAddress(from)
              .setToRecipients(adminTo)
              .setSubject("[Feedback] Avaliação crítica recebida")
              .setBodyPlainText(bodyText);
      var poller = client.beginSend(message, Context.NONE);
      poller.waitForCompletion(Duration.ofMinutes(2));
      var result = poller.getFinalResult();
      LOG.infof("notifyCritical.acs_email_ok operationId=%s", result.getId());
    } catch (Exception e) {
      LOG.errorf(e, "notifyCritical.acs_email_error");
    }
  }

  private EmailClient emailClient(String conn) {
    if (emailClient != null) {
      return emailClient;
    }
    synchronized (this) {
      if (emailClient == null) {
        emailClient = new EmailClientBuilder().connectionString(conn).buildClient();
        LOG.info("notifyCritical.acs_email_client_initialized");
      }
      return emailClient;
    }
  }

  private static String emailConnectionString() {
    var a = getenv("ACS_EMAIL_CONNECTION_STRING");
    if (a != null) {
      return a;
    }
    return getenv("AZURE_COMMUNICATION_CONNECTION_STRING");
  }

  private static String buildPlainBody(String descricao, Urgencia urgencia, String dataEnvioIso) {
    return "Feedback com urgência elevada\n\n"
        + "Descrição: "
        + descricao
        + "\nUrgência: "
        + urgencia.name()
        + "\nData de envio: "
        + dataEnvioIso
        + "\n";
  }

  private static String getenv(String name) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? null : v.trim();
  }
}
