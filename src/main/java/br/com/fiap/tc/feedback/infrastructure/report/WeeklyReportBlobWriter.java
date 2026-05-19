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

  public void uploadPdf(String blobName, byte[] content) {
    uploadBytes(blobName, content, "application/pdf");
  }

  public void uploadUtf8(String blobName, String content) {
    uploadBytes(blobName, content.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain; charset=UTF-8");
  }

  private void uploadBytes(String blobName, byte[] content, String contentType) {
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
    blob.upload(BinaryData.fromBytes(content), true);
    LOG.infof(
        "weeklyReport.blob_uploaded container=%s blob=%s bytes=%d type=%s",
        container,
        blobName,
        content.length,
        contentType);
  }

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }
}
