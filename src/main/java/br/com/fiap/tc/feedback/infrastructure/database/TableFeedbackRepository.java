package br.com.fiap.tc.feedback.infrastructure.database;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import com.azure.core.util.Context;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    LOG.infof("tableRepo.init table=%s", tableName);
    new TableServiceClientBuilder().connectionString(conn).buildClient().createTableIfNotExists(tableName);

    this.table = new TableClientBuilder().connectionString(conn).tableName(tableName).buildClient();
  }

  public FeedbackRow save(String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var id = java.util.UUID.randomUUID().toString();
    return saveWithId(id, descricao, nota, urgencia, createdAt);
  }

  public FeedbackRow saveWithId(String id, String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var day = LocalDate.ofInstant(createdAt, ZoneOffset.UTC).toString();

    var e = new TableEntity(day, id);
    e.addProperty("descricao", descricao);
    e.addProperty("nota", nota);
    e.addProperty("urgencia", urgencia.name());
    e.addProperty("createdAt", createdAt.toString());

    LOG.infof("tableRepo.save start partitionKey=%s rowKey=%s urgencia=%s", day, id, urgencia.name());
    table.createEntity(e);
    LOG.infof("tableRepo.save success partitionKey=%s rowKey=%s", day, id);

    return new FeedbackRow(id, day, descricao, nota, urgencia.name(), createdAt.toString());
  }

  public List<FeedbackRow> listBetweenInclusive(LocalDate start, LocalDate end) {
    var startPk = start.toString();
    var endPk = end.toString();
    var filter = String.format("(PartitionKey ge '%s') and (PartitionKey le '%s')", startPk, endPk);
    var opts = new ListEntitiesOptions().setFilter(filter);
    var out = new ArrayList<FeedbackRow>();
    LOG.infof("tableRepo.listBetween start=%s end=%s", startPk, endPk);
    for (var e : table.listEntities(opts, Duration.ofSeconds(60), Context.NONE)) {
      out.add(fromEntity(e));
    }
    LOG.infof("tableRepo.listBetween count=%d", out.size());
    return out;
  }

  private static FeedbackRow fromEntity(TableEntity e) {
    var descricao = Objects.toString(e.getProperty("descricao"), "");
    var notaProp = e.getProperty("nota");
    var nota = notaProp instanceof Number n ? n.intValue() : Integer.parseInt(Objects.toString(notaProp, "0"));
    var urgencia = Objects.toString(e.getProperty("urgencia"), "");
    var createdAt = Objects.toString(e.getProperty("createdAt"), "");
    return new FeedbackRow(e.getRowKey(), e.getPartitionKey(), descricao, nota, urgencia, createdAt);
  }

  public record FeedbackRow(String id, String day, String descricao, int nota, String urgencia, String createdAt) {}

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }

}

