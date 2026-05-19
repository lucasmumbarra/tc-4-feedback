package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.application.dto.email.SendCriticalEmailRequest;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.jboss.logging.Logger;

/**
 * Dispara a Azure Function {@code sendCriticalEmail} via HTTP.
 *
 * <p>Importante: no Azure Functions o trabalho em background após o retorno do HTTP é cancelado. Por
 * isso a chamada é <strong>síncrona</strong> — o submit aguarda a function de e-mail concluir (invocação
 * separada visível no portal). Não use {@code runAsync} aqui.
 */
@ApplicationScoped
public class CriticalEmailFunctionClient {
  private static final Logger LOG = Logger.getLogger(CriticalEmailFunctionClient.class);
  private static final String ROUTE = "/api/send-critical-email";

  @Inject ObjectMapper mapper;
  @Inject CriticalEmailSender emailSender;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  /**
   * Invoca {@code sendCriticalEmail}. Em falha de HTTP (rede, 401, etc.) executa {@link
   * CriticalEmailSender} no mesmo processo para não perder o alerta.
   */
  public void invoke(
      String feedbackId, String descricao, Urgencia urgencia, String feedbackCreatedAtIso) {
    try {
      var dto = new SendCriticalEmailRequest();
      dto.feedbackId = feedbackId;
      dto.descricao = descricao;
      dto.urgencia = urgencia.name();
      dto.feedbackCreatedAt = feedbackCreatedAtIso;
      var body = mapper.writeValueAsString(dto);
      var url = buildUrl();

      var requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(120))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));

      var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        LOG.infof(
            "criticalEmail.invoke_ok status=%d feedbackId=%s url=%s",
            response.statusCode(),
            feedbackId,
            maskUrl(url));
        return;
      }
      LOG.warnf(
          "criticalEmail.invoke_http_failed status=%d feedbackId=%s body=%s url=%s — fallback in-process",
          response.statusCode(),
          feedbackId,
          response.body(),
          maskUrl(url));
    } catch (Exception e) {
      LOG.warnf(e, "criticalEmail.invoke_exception feedbackId=%s — fallback in-process", feedbackId);
    }
    emailSender.send(feedbackId, descricao, urgencia, feedbackCreatedAtIso);
  }

  private String buildUrl() {
    var base = resolveBaseUrl();
    var functionKey = getenv("SEND_CRITICAL_EMAIL_FUNCTION_KEY");
    if (functionKey == null) {
      return base;
    }
    var sep = base.contains("?") ? "&" : "?";
    return base + sep + "code=" + URLEncoder.encode(functionKey, StandardCharsets.UTF_8);
  }

  private static String resolveBaseUrl() {
    var explicit = getenv("SEND_CRITICAL_EMAIL_URL");
    if (explicit != null) {
      return explicit;
    }
    var host = getenv("WEBSITE_HOSTNAME");
    if (host != null) {
      return "https://" + host + ROUTE;
    }
    var port = getenv("FUNCTIONS_CUSTOMHANDLER_PORT");
    if (port == null) {
      port = "7071";
    }
    return "http://127.0.0.1:" + port + ROUTE;
  }

  private static String maskUrl(String url) {
    var idx = url.indexOf("code=");
    return idx < 0 ? url : url.substring(0, idx + 5) + "***";
  }

  private static String getenv(String name) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? null : v.trim();
  }
}
