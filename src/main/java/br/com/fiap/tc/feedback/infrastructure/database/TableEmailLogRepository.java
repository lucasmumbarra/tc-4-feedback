package br.com.fiap.tc.feedback.infrastructure.database;

import br.com.fiap.tc.feedback.infrastructure.email.EmailSendResult;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TableEmailLogRepository {
  private static final Logger LOG = Logger.getLogger(TableEmailLogRepository.class);
  private static final String DEFAULT_TABLE = "emaillogs";

  private final TableClient table;

  public TableEmailLogRepository() {
    var conn = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    Objects.requireNonNull(conn, "AZURE_STORAGE_CONNECTION_STRING is required");

    var tableName = envOrDefault("EMAIL_LOG_TABLE_NAME", DEFAULT_TABLE);
    LOG.infof("emailLogRepo.init table=%s", tableName);
    new TableServiceClientBuilder().connectionString(conn).buildClient().createTableIfNotExists(tableName);
    this.table = new TableClientBuilder().connectionString(conn).tableName(tableName).buildClient();
  }

  public EmailLogRow save(
      String feedbackId,
      String descricao,
      String urgencia,
      String feedbackCreatedAt,
      EmailSendResult result,
      Instant loggedAt) {
    var day = LocalDate.ofInstant(loggedAt, ZoneOffset.UTC).toString();
    var id = UUID.randomUUID().toString();

    var e = new TableEntity(day, id);
    e.addProperty("feedbackId", feedbackId);
    e.addProperty("descricao", descricao);
    e.addProperty("urgencia", urgencia);
    e.addProperty("feedbackCreatedAt", feedbackCreatedAt);
    e.addProperty("mode", result.mode());
    if (result.statusCode() != null) {
      e.addProperty("statusCode", result.statusCode());
    }
    if (result.errorDetail() != null) {
      e.addProperty("errorDetail", result.errorDetail());
    }
    if (result.fromEmail() != null) {
      e.addProperty("fromEmail", result.fromEmail());
    }
    if (result.toEmail() != null) {
      e.addProperty("toEmail", result.toEmail());
    }
    e.addProperty("loggedAt", loggedAt.toString());

    table.createEntity(e);
    LOG.infof(
        "emailLogRepo.save feedbackId=%s mode=%s partitionKey=%s rowKey=%s",
        feedbackId,
        result.mode(),
        day,
        id);
    return new EmailLogRow(
        id, day, feedbackId, descricao, urgencia, feedbackCreatedAt, result.mode(), loggedAt.toString());
  }

  public record EmailLogRow(
      String id,
      String day,
      String feedbackId,
      String descricao,
      String urgencia,
      String feedbackCreatedAt,
      String mode,
      String loggedAt) {}

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }
}
