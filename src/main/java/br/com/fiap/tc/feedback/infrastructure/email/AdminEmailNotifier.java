package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendStatus;
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
          "notifyCritical.mode=SIMULATED missing=%s — configure na Function App (Configuration) as três variáveis; "
              + "sem isto o processamento da fila corre mas não envia e-mail. Corpo que seria enviado:%n%s",
          missingEmailConfigParts(conn, adminTo, from),
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
      if (result.getStatus() == null || !EmailSendStatus.SUCCEEDED.equals(result.getStatus())) {
        LOG.errorf(
            "notifyCritical.mode=ACS_FAILED status=%s operationId=%s error=%s",
            result.getStatus(),
            result.getId(),
            result.getError());
      } else {
        LOG.infof("notifyCritical.mode=SENT operationId=%s status=%s", result.getId(), result.getStatus());
      }
    } catch (Exception e) {
      LOG.errorf(e, "notifyCritical.acs_email_exception");
    }
  }

  private static String missingEmailConfigParts(String conn, String adminTo, String from) {
    var b = new StringBuilder();
    if (conn == null) {
      b.append("ACS_EMAIL_CONNECTION_STRING|AZURE_COMMUNICATION_CONNECTION_STRING;");
    }
    if (adminTo == null) {
      b.append("ADMIN_NOTIFY_EMAIL;");
    }
    if (from == null) {
      b.append("NOTIFY_FROM_EMAIL;");
    }
    return b.toString();
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
