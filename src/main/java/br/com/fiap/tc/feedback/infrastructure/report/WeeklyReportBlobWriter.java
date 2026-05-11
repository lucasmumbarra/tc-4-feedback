package br.com.fiap.tc.feedback.infrastructure.report;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WeeklyReportBlobWriter {
  private static final Logger LOG = Logger.getLogger(WeeklyReportBlobWriter.class);
  private static final String DEFAULT_CONTAINER = "relatorios";

  public void uploadUtf8(String blobName, String content) {
    var conn = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
    Objects.requireNonNull(conn, "AZURE_STORAGE_CONNECTION_STRING is required");
    var container = envOrDefault("WEEKLY_REPORT_CONTAINER", DEFAULT_CONTAINER);
    new BlobServiceClientBuilder().connectionString(conn).buildClient().getBlobContainerClient(container).createIfNotExists();
    BlockBlobClient blob =
        new BlobClientBuilder()
            .connectionString(conn)
            .containerName(container)
            .blobName(blobName)
            .buildClient()
            .getBlockBlobClient();
    blob.upload(BinaryData.fromString(content), true);
    LOG.infof("weeklyReport.blob_uploaded container=%s blob=%s bytes=%d", container, blobName, content.length());
  }

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }
}
