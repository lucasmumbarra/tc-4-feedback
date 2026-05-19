package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.jboss.logging.Logger;

/**
 * Notifica administradores sobre feedback crítico via <strong>SendGrid SMTP</strong>
 * ({@code smtp.sendgrid.net}). Requer API key (senha SMTP), remetente verificado e destino. Sem
 * configuração, regista modo {@code SIMULATED}.
 */
@ApplicationScoped
public class AdminEmailNotifier {
  private static final Logger LOG = Logger.getLogger(AdminEmailNotifier.class);
  private static final String SUBJECT = "[Feedback] Avaliação crítica recebida";
  private static final int SMTP_OK = 250;

  public EmailSendResult notifyCritical(String descricao, Urgencia urgencia, String dataEnvioIso) {
    var bodyText = buildPlainBody(descricao, urgencia, dataEnvioIso);
    var apiKey = getenv("SENDGRID_API_KEY");
    var adminTo = getenv("ADMIN_NOTIFY_EMAIL");
    var from = getenv("NOTIFY_FROM_EMAIL");
    if (apiKey == null || adminTo == null || from == null) {
      LOG.warnf(
          "notifyCritical.mode=SIMULATED missing=%s — configure na Function App: SENDGRID_API_KEY, "
              + "NOTIFY_FROM_EMAIL, ADMIN_NOTIFY_EMAIL. Corpo que seria enviado:%n%s",
          missingEmailConfigParts(apiKey, adminTo, from),
          bodyText);
      return new EmailSendResult("SIMULATED", null, missingEmailConfigParts(apiKey, adminTo, from), from, adminTo);
    }
    try {
      sendViaSmtp(from, adminTo, bodyText, apiKey);
      LOG.infof("notifyCritical.mode=SENT via=smtp host=%s", smtpHost());
      return new EmailSendResult("SENT", SMTP_OK, null, from, adminTo);
    } catch (Exception e) {
      LOG.errorf(e, "notifyCritical.smtp_exception");
      return new EmailSendResult("SMTP_FAILED", null, e.getMessage(), from, adminTo);
    }
  }

  private static void sendViaSmtp(String from, String to, String bodyText, String apiKey) throws Exception {
    var props = new Properties();
    props.put("mail.smtp.host", smtpHost());
    props.put("mail.smtp.port", smtpPort());
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.connectiontimeout", "15000");
    props.put("mail.smtp.timeout", "15000");
    props.put("mail.smtp.writetimeout", "15000");

    final var smtpUser = envOrDefault("SMTP_USERNAME", "apikey");

    var session =
        Session.getInstance(
            props,
            new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, apiKey);
              }
            });

    var message = new MimeMessage(session);
    message.setFrom(new InternetAddress(from));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
    message.setSubject(SUBJECT);
    message.setText(bodyText);

    Transport.send(message);
  }

  private static String smtpHost() {
    return envOrDefault("SMTP_HOST", "smtp.sendgrid.net");
  }

  private static String smtpPort() {
    return envOrDefault("SMTP_PORT", "587");
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

  private static String envOrDefault(String name, String def) {
    var v = getenv(name);
    return v == null ? def : v;
  }
}
