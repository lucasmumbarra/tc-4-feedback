package br.com.fiap.tc.feedback.infrastructure.database;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TableFeedbackRepository {
  private static final Logger LOG = Logger.getLogger(TableFeedbackRepository.class);
  private static final String DEFAULT_TABLE = "feedbacks";

  private final TableClient table;

  public TableFeedbackRepository() {
    var conn = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    Objects.requireNonNull(conn, "AZURE_STORAGE_CONNECTION_STRING is required");

    var tableName = envOrDefault("FEEDBACK_TABLE_NAME", DEFAULT_TABLE);
    var httpClient = azureHttpClient();

    LOG.infof("tableRepo.init table=%s timeoutSeconds=%d", tableName, envOrDefaultInt("AZURE_HTTP_TIMEOUT_SECONDS", 10));
    new TableServiceClientBuilder()
        .connectionString(conn)
        .httpClient(httpClient)
        .buildClient()
        .createTableIfNotExists(tableName);

    this.table =
        new TableClientBuilder()
            .connectionString(conn)
            .httpClient(httpClient)
            .tableName(tableName)
            .buildClient();
  }

  public FeedbackRow save(String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var id = java.util.UUID.randomUUID().toString();
    return saveWithId(id, descricao, nota, urgencia, createdAt);
  }

  public FeedbackRow saveWithId(String id, String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var day = LocalDate.ofInstant(createdAt, ZoneOffset.UTC).toString(); // yyyy-MM-dd

    var e = new TableEntity(day, id);
    e.addProperty("descricao", descricao);
    e.addProperty("nota", nota);
    e.addProperty("urgencia", urgencia.name());
    e.addProperty("createdAt", createdAt.toString()); // ISO-8601 UTC

    LOG.infof("tableRepo.save start partitionKey=%s rowKey=%s urgencia=%s", day, id, urgencia.name());
    table.createEntity(e);
    LOG.infof("tableRepo.save success partitionKey=%s rowKey=%s", day, id);

    return new FeedbackRow(id, day, descricao, nota, urgencia.name(), createdAt.toString());
  }

  public record FeedbackRow(String id, String day, String descricao, int nota, String urgencia, String createdAt) {}

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }

  private static int envOrDefaultInt(String name, int def) {
    var v = System.getenv(name);
    if (v == null || v.isBlank()) return def;
    try {
      return Integer.parseInt(v.trim());
    } catch (Exception ignored) {
      return def;
    }
  }

  private static HttpClient azureHttpClient() {
    var timeoutSeconds = envOrDefaultInt("AZURE_HTTP_TIMEOUT_SECONDS", 10);
    return new NettyAsyncHttpClientBuilder()
        .responseTimeout(Duration.ofSeconds(timeoutSeconds))
        .build();
  }
}

