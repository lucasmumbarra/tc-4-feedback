package com.fiap.tc.infra;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import com.fiap.tc.avaliacao.Urgencia;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CosmosFeedbackRepository {
  public static final String DEFAULT_DATABASE = "feedbackdb";
  public static final String DEFAULT_CONTAINER = "feedbacks";

  private final CosmosContainer container;

  public CosmosFeedbackRepository(CosmosContainer container) {
    this.container = container;
  }

  public static CosmosFeedbackRepository fromEnv() {
    var endpoint = System.getenv("COSMOS_ENDPOINT");
    var key = System.getenv("COSMOS_KEY");
    Objects.requireNonNull(endpoint, "COSMOS_ENDPOINT is required");
    Objects.requireNonNull(key, "COSMOS_KEY is required");

    var databaseName = envOrDefault("COSMOS_DATABASE", DEFAULT_DATABASE);
    var containerName = envOrDefault("COSMOS_CONTAINER", DEFAULT_CONTAINER);

    CosmosClient client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
    client.createDatabaseIfNotExists(databaseName);
    CosmosDatabase db = client.getDatabase(databaseName);

    // Partition by day (yyyy-MM-dd) to make weekly queries cheap.
    var props = new CosmosContainerProperties(containerName, "/day");
    db.createContainerIfNotExists(props, ThroughputProperties.createManualThroughput(400));
    CosmosContainer container = db.getContainer(containerName);
    return new CosmosFeedbackRepository(container);
  }

  public FeedbackDoc save(String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var id = UUID.randomUUID().toString();
    var day = LocalDate.ofInstant(createdAt, ZoneOffset.UTC).toString(); // yyyy-MM-dd
    var doc = new FeedbackDoc(id, day, descricao, nota, urgencia.name(), createdAt.toString());
    container.createItem(doc, new PartitionKey(day), null);
    return doc;
  }

  public List<FeedbackDoc> listBetweenInclusive(LocalDate startUtc, LocalDate endUtc) {
    // Query by createdAt ISO string; plus restrict by day partition range via IN list for 7 days.
    var days = new ArrayList<String>();
    for (var d = startUtc; !d.isAfter(endUtc); d = d.plusDays(1)) {
      days.add(d.toString());
    }

    // Build "IN (@d0,@d1,...)".
    var inParams = new StringBuilder();
    for (int i = 0; i < days.size(); i++) {
      if (i > 0) inParams.append(",");
      inParams.append("@d").append(i);
    }

    var query =
        "SELECT * FROM c WHERE c.day IN (" + inParams + ") AND c.createdAt >= @start AND c.createdAt <= @end";
    var q = new com.azure.cosmos.models.SqlQuerySpec(query);
    q.getParameters().add(new com.azure.cosmos.models.SqlParameter("@start", startUtc.atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
    q.getParameters().add(new com.azure.cosmos.models.SqlParameter("@end", endUtc.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()));
    for (int i = 0; i < days.size(); i++) {
      q.getParameters().add(new com.azure.cosmos.models.SqlParameter("@d" + i, days.get(i)));
    }

    var out = new ArrayList<FeedbackDoc>();
    for (var page : container.queryItems(q, new com.azure.cosmos.models.CosmosQueryRequestOptions(), FeedbackDoc.class).iterableByPage()) {
      out.addAll(page.getResults());
    }
    return out;
  }

  public record FeedbackDoc(String id, String day, String descricao, int nota, String urgencia, String createdAt) {}

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }
}

