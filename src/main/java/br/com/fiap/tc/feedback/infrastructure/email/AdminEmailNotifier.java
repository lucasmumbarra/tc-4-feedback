package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.jboss.logging.Logger;

/**
 * Notifica administradores sobre feedback crítico. Se {@code SENDGRID_API_KEY} e {@code
 * ADMIN_NOTIFY_EMAIL} estiverem definidos, envia via API REST do SendGrid; caso contrário regista
 * o conteúdo nos logs (útil para demo/local).
 */
@ApplicationScoped
public class AdminEmailNotifier {
  private static final Logger LOG = Logger.getLogger(AdminEmailNotifier.class);
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final ObjectMapper mapper;

  @Inject
  public AdminEmailNotifier(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void notifyCritical(String descricao, Urgencia urgencia, String dataEnvioIso) {
    var bodyText = buildPlainBody(descricao, urgencia, dataEnvioIso);
    var apiKey = getenv("SENDGRID_API_KEY");
    var adminTo = getenv("ADMIN_NOTIFY_EMAIL");
    var from = getenv("NOTIFY_FROM_EMAIL");
    if (apiKey == null || adminTo == null || from == null) {
      LOG.warnf(
          "notifyCritical.simulated SENDGRID_API_KEY/ADMIN_NOTIFY_EMAIL/NOTIFY_FROM_EMAIL missing; body=%n%s",
          bodyText);
      return;
    }
    try {
      var payload = buildSendGridPayload(from, adminTo, bodyText);
      var req =
          HttpRequest.newBuilder(URI.create("https://api.sendgrid.com/v3/mail/send"))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
              .build();
      var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
        LOG.infof("notifyCritical.sendgrid_ok status=%d", resp.statusCode());
      } else {
        LOG.warnf("notifyCritical.sendgrid_failed status=%d body=%s", resp.statusCode(), resp.body());
      }
    } catch (Exception e) {
      LOG.errorf(e, "notifyCritical.sendgrid_error");
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

  private String buildSendGridPayload(String from, String to, String text) {
    try {
      ObjectNode root = mapper.createObjectNode();
      ArrayNode personalizations = root.putArray("personalizations");
      ObjectNode p0 = personalizations.addObject();
      ArrayNode toArr = p0.putArray("to");
      toArr.addObject().put("email", to);
      root.putObject("from").put("email", from);
      root.put("subject", "[Feedback] Avaliação crítica recebida");
      ArrayNode content = root.putArray("content");
      content.addObject().put("type", "text/plain").put("value", text);
      return mapper.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String getenv(String name) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? null : v.trim();
  }
}
