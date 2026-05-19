package br.com.fiap.tc.feedback.infrastructure.email;

import br.com.fiap.tc.feedback.application.dto.email.SendCriticalEmailRequest;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jboss.logging.Logger;

/**
 * Dispara a Azure Function {@code sendCriticalEmail} via HTTP sem aguardar conclusão (fire-and-forget).
 */
@ApplicationScoped
public class CriticalEmailFunctionClient {
  private static final Logger LOG = Logger.getLogger(CriticalEmailFunctionClient.class);
  private static final String ROUTE = "/api/send-critical-email";

  @Inject ObjectMapper mapper;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public void invokeAsync(
      String feedbackId, String descricao, Urgencia urgencia, String feedbackCreatedAtIso) {
    CompletableFuture.runAsync(
        () -> {
          try {
            var dto = new SendCriticalEmailRequest();
            dto.feedbackId = feedbackId;
            dto.descricao = descricao;
            dto.urgencia = urgencia.name();
            dto.feedbackCreatedAt = feedbackCreatedAtIso;
            var body = mapper.writeValueAsString(dto);
            var url = resolveUrl();
            var requestBuilder =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            var functionKey = getenv("SEND_CRITICAL_EMAIL_FUNCTION_KEY");
            if (functionKey != null) {
              requestBuilder.header("x-functions-key", functionKey);
            }
            httpClient
                .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete(
                    (response, error) -> {
                      if (error != null) {
                        LOG.warnf(
                            error,
                            "criticalEmail.invoke_async_failed feedbackId=%s (e-mail pode ainda processar na Function)",
                            feedbackId);
                        return;
                      }
                      if (response != null && response.statusCode() >= 300) {
                        LOG.warnf(
                            "criticalEmail.invoke_async_http status=%d feedbackId=%s",
                            response.statusCode(),
                            feedbackId);
                      } else {
                        LOG.infof("criticalEmail.invoke_async_accepted feedbackId=%s", feedbackId);
                      }
                    });
          } catch (Exception e) {
            LOG.warnf(
                e,
                "criticalEmail.invoke_async_setup_failed feedbackId=%s",
                feedbackId);
          }
        });
  }

  private static String resolveUrl() {
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

  private static String getenv(String name) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? null : v.trim();
  }
}
