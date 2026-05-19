package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.report.WeeklyReportData;
import jakarta.activation.DataHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.Locale;
import java.util.Properties;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AdminEmailNotifier {
  private static final Logger LOG = Logger.getLogger(AdminEmailNotifier.class);
  private static final String CRITICAL_SUBJECT = "[Feedback] Avaliação crítica recebida";
  private static final int SMTP_OK = 250;

  public EmailSendResult notifyCritical(String descricao, Urgencia urgencia, String dataEnvioIso) {
    var bodyText = buildCriticalPlainBody(descricao, urgencia, dataEnvioIso);
    return sendPlain(CRITICAL_SUBJECT, bodyText);
  }

  public EmailSendResult sendWeeklyReportPdf(WeeklyReportData data, byte[] pdfBytes, String pdfFileName) {
    var subject =
        String.format(
            Locale.US, "[Feedback] Relatório semanal %s a %s", data.start(), data.end());
    var body =
        String.format(
            Locale.US,
            "Segue em anexo o relatório semanal (PDF).%n%n"
                + "Período (UTC): %s a %s%n"
                + "Média das notas: %s%n"
                + "Conteúdo: quantidade por dia, por urgência, e cada avaliação com descrição, urgência e data de envio.%n",
            data.start(),
            data.end(),
            data.hasRows() ? String.format(Locale.US, "%.2f", data.average()) : "n/d");
    return sendWithPdfAttachment(subject, body, pdfBytes, pdfFileName);
  }

  private EmailSendResult sendPlain(String subject, String bodyText) {
    var cfg = smtpConfig();
    if (cfg == null) {
      LOG.warnf(
          "email.mode=SIMULATED missing=%s subject=%s%n%s",
          missingParts(), subject, bodyText);
      return simulated(bodyText);
    }
    try {
      sendMessage(cfg, buildPlainMessage(cfg, subject, bodyText));
      LOG.infof("email.mode=SENT subject=%s via=smtp", subject);
      return new EmailSendResult("SENT", SMTP_OK, null, cfg.from(), cfg.to());
    } catch (Exception e) {
      LOG.errorf(e, "email.smtp_exception subject=%s", subject);
      return new EmailSendResult("SMTP_FAILED", null, e.getMessage(), cfg.from(), cfg.to());
    }
  }

  private EmailSendResult sendWithPdfAttachment(
      String subject, String bodyText, byte[] pdfBytes, String pdfFileName) {
    var cfg = smtpConfig();
    if (cfg == null) {
      LOG.warnf(
          "email.mode=SIMULATED missing=%s subject=%s attachment=%s (%d bytes)",
          missingParts(),
          subject,
          pdfFileName,
          pdfBytes.length);
      return simulated(bodyText);
    }
    try {
      sendMessage(cfg, buildPdfMessage(cfg, subject, bodyText, pdfBytes, pdfFileName));
      LOG.infof("email.mode=SENT subject=%s attachment=%s bytes=%d", subject, pdfFileName, pdfBytes.length);
      return new EmailSendResult("SENT", SMTP_OK, null, cfg.from(), cfg.to());
    } catch (Exception e) {
      LOG.errorf(e, "email.smtp_exception subject=%s attachment=%s", subject, pdfFileName);
      return new EmailSendResult("SMTP_FAILED", null, e.getMessage(), cfg.from(), cfg.to());
    }
  }

  private static MimeMessage buildPlainMessage(SmtpConfig cfg, String subject, String bodyText)
      throws Exception {
    var message = baseMessage(cfg, subject);
    message.setText(bodyText);
    return message;
  }

  private static MimeMessage buildPdfMessage(
      SmtpConfig cfg, String subject, String bodyText, byte[] pdfBytes, String pdfFileName)
      throws Exception {
    var textPart = new MimeBodyPart();
    textPart.setText(bodyText);

    var pdfPart = new MimeBodyPart();
    pdfPart.setDataHandler(new DataHandler(new ByteArrayDataSource(pdfBytes, "application/pdf")));
    pdfPart.setFileName(pdfFileName);

    var multipart = new MimeMultipart();
    multipart.addBodyPart(textPart);
    multipart.addBodyPart(pdfPart);

    var message = baseMessage(cfg, subject);
    message.setContent(multipart);
    return message;
  }

  private static MimeMessage baseMessage(SmtpConfig cfg, String subject) throws Exception {
    var message = new MimeMessage(cfg.session());
    message.setFrom(new InternetAddress(cfg.from()));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(cfg.to()));
    message.setSubject(subject);
    return message;
  }

  private static void sendMessage(SmtpConfig cfg, MimeMessage message) throws Exception {
    Transport.send(message);
  }

  private record SmtpConfig(String from, String to, String apiKey) {
    Session session() {
      var props = new Properties();
      props.put("mail.smtp.host", smtpHost());
      props.put("mail.smtp.port", smtpPort());
      props.put("mail.smtp.auth", "true");
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.connectiontimeout", "15000");
      props.put("mail.smtp.timeout", "15000");
      props.put("mail.smtp.writetimeout", "15000");
      var user = envOrDefault("SMTP_USERNAME", "apikey");
      return Session.getInstance(
          props,
          new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
              return new PasswordAuthentication(user, apiKey);
            }
          });
    }
  }

  private static SmtpConfig smtpConfig() {
    var apiKey = getenv("SENDGRID_API_KEY");
    var to = getenv("ADMIN_NOTIFY_EMAIL");
    var from = getenv("NOTIFY_FROM_EMAIL");
    if (apiKey == null || to == null || from == null) {
      return null;
    }
    return new SmtpConfig(from, to, apiKey);
  }

  private static EmailSendResult simulated(String detail) {
    return new EmailSendResult("SIMULATED", null, missingParts(), null, null);
  }

  private static String missingParts() {
    var b = new StringBuilder();
    if (getenv("SENDGRID_API_KEY") == null) {
      b.append("SENDGRID_API_KEY;");
    }
    if (getenv("ADMIN_NOTIFY_EMAIL") == null) {
      b.append("ADMIN_NOTIFY_EMAIL;");
    }
    if (getenv("NOTIFY_FROM_EMAIL") == null) {
      b.append("NOTIFY_FROM_EMAIL;");
    }
    return b.toString();
  }

  private static String buildCriticalPlainBody(String descricao, Urgencia urgencia, String dataEnvioIso) {
    return "Feedback com urgência elevada\n\n"
        + "Descrição: "
        + descricao
        + "\nUrgência: "
        + urgencia.name()
        + "\nData de envio: "
        + dataEnvioIso
        + "\n";
  }

  private static String smtpHost() {
    return envOrDefault("SMTP_HOST", "smtp.sendgrid.net");
  }

  private static String smtpPort() {
    return envOrDefault("SMTP_PORT", "587");
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
