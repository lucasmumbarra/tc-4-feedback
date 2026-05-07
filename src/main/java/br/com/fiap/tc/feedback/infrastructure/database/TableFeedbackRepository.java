package br.com.fiap.tc.feedback.infrastructure.database;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class TableFeedbackRepository {
  private static final String DEFAULT_TABLE = "feedbacks";

  private final TableClient table;

  public TableFeedbackRepository() {
    var conn = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    Objects.requireNonNull(conn, "AZURE_STORAGE_CONNECTION_STRING is required");
    var tableName = envOrDefault("FEEDBACK_TABLE_NAME", DEFAULT_TABLE);
    var httpClient = azureHttpClient();
    new TableServiceClientBuilder().connectionString(conn).httpClient(httpClient).buildClient().createTableIfNotExists(tableName);
    this.table = new TableClientBuilder().connectionString(conn).httpClient(httpClient).tableName(tableName).buildClient();
  }

  public FeedbackRow save(String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var id = java.util.UUID.randomUUID().toString();
    var day = LocalDate.ofInstant(createdAt, ZoneOffset.UTC).toString(); // yyyy-MM-dd

    var e = new TableEntity(day, id);
    e.addProperty("descricao", descricao);
    e.addProperty("nota", nota);
    e.addProperty("urgencia", urgencia.name());
    e.addProperty("createdAt", createdAt.toString()); // ISO-8601 UTC
    table.createEntity(e);

    return new FeedbackRow(id, day, descricao, nota, urgencia.name(), createdAt.toString());
  }

  public List<FeedbackRow> listBetweenInclusive(LocalDate startUtc, LocalDate endUtc) {
    var start = startUtc.toString();
    var end = endUtc.toString();
    // PartitionKey is yyyy-MM-dd so lexical range works.
    var filter =
        "(PartitionKey ge '" + start + "') and (PartitionKey le '" + end + "')";

    var out = new ArrayList<FeedbackRow>();
    var opts = new ListEntitiesOptions().setFilter(filter);
    for (var entity : table.listEntities(opts, null, null)) {
      out.add(toRow(entity));
    }
    return out;
  }

  private static FeedbackRow toRow(TableEntity e) {
    var day = Objects.toString(e.getPartitionKey(), "");
    var id = Objects.toString(e.getRowKey(), "");
    var descricao = Objects.toString(e.getProperty("descricao"), "");
    var nota = ((Number) e.getProperty("nota")).intValue();
    var urgencia = Objects.toString(e.getProperty("urgencia"), "");
    var createdAt = Objects.toString(e.getProperty("createdAt"), "");
    return new FeedbackRow(id, day, descricao, nota, urgencia, createdAt);
  }

  public record FeedbackRow(String id, String day, String descricao, int nota, String urgencia, String createdAt) {}

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }

  private static HttpClient azureHttpClient() {
    var timeoutSeconds = envOrDefaultInt("AZURE_HTTP_TIMEOUT_SECONDS", 10);
    var timeout = Duration.ofSeconds(timeoutSeconds);
    return new NettyAsyncHttpClientBuilder().responseTimeout(timeout).build();
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
}

