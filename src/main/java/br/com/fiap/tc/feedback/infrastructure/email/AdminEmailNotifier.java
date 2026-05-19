package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Notifica administradores sobre feedback crítico via <strong>SendGrid</strong>. Requer API key e
 * endereço de remetente verificado no SendGrid. Sem configuração, o conteúdo é apenas registado
 * nos logs.
 */
@ApplicationScoped
public class AdminEmailNotifier {
  private static final Logger LOG = Logger.getLogger(AdminEmailNotifier.class);

  private volatile SendGrid sendGrid;

  public void notifyCritical(String descricao, Urgencia urgencia, String dataEnvioIso) {
    var bodyText = buildPlainBody(descricao, urgencia, dataEnvioIso);
    var apiKey = getenv("SENDGRID_API_KEY");
    var adminTo = getenv("ADMIN_NOTIFY_EMAIL");
    var from = getenv("NOTIFY_FROM_EMAIL");
    if (apiKey == null || adminTo == null || from == null) {
      LOG.warnf(
          "notifyCritical.mode=SIMULATED missing=%s — configure na Function App (Configuration) as três variáveis; "
              + "sem isto o processamento da fila corre mas não envia e-mail. Corpo que seria enviado:%n%s",
          missingEmailConfigParts(apiKey, adminTo, from),
          bodyText);
      return;
    }
    try {
      var client = sendGridClient(apiKey);
      var mail =
          new Mail(
              new Email(from),
              "[Feedback] Avaliação crítica recebida",
              new Email(adminTo),
              new Content("text/plain", bodyText));

      var request = new Request();
      request.setMethod(Method.POST);
      request.setEndpoint("mail/send");
      request.setBody(mail.build());

      var response = client.api(request);
      var status = response.getStatusCode();
      if (status >= 200 && status < 300) {
        LOG.infof("notifyCritical.mode=SENT statusCode=%d", status);
      } else {
        LOG.errorf(
            "notifyCritical.mode=SENDGRID_FAILED statusCode=%d body=%s",
            status,
            response.getBody());
      }
    } catch (Exception e) {
      LOG.errorf(e, "notifyCritical.sendgrid_exception");
    }
  }

  private static String missingEmailConfigParts(String apiKey, String adminTo, String from) {
    var b = new StringBuilder();
    if (apiKey == null) {
      b.append("SENDGRID_API_KEY;");
    }
    if (adminTo == null) {
      b.append("ADMIN_NOTIFY_EMAIL;");
    }
    if (from == null) {
      b.append("NOTIFY_FROM_EMAIL;");
    }
    return b.toString();
  }

  private SendGrid sendGridClient(String apiKey) {
    if (sendGrid != null) {
      return sendGrid;
    }
    synchronized (this) {
      if (sendGrid == null) {
        sendGrid = new SendGrid(apiKey);
        LOG.info("notifyCritical.sendgrid_client_initialized");
      }
      return sendGrid;
    }
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
