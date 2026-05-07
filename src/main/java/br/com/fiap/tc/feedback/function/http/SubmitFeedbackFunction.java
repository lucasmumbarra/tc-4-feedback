package br.com.fiap.tc.feedback.function.http;

import br.com.fiap.tc.feedback.application.dto.message.FeedbackIngestMessage;
import br.com.fiap.tc.feedback.application.dto.request.AvaliacaoRequest;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.messaging.publisher.AzureQueuePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.jboss.logging.Logger;

@Path("/avaliacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SubmitFeedbackFunction {
  private static final Logger LOG = Logger.getLogger(SubmitFeedbackFunction.class);
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @POST
  public Response criar(AvaliacaoRequest req) {
    if (req == null || req.descricao == null || req.descricao.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("descricao is required")).build();
    }
    if (req.nota < 0 || req.nota > 10) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("nota must be between 0 and 10"))
          .build();
    }

    var now = Instant.now();
    var urgencia = classificar(req.nota);
    var id = UUID.randomUUID().toString();

    LOG.infof("feedback.received urgencia=%s nota=%d descricao_len=%d", urgencia.name(), req.nota, req.descricao.length());

    if (!enfileirarIngest(id, req.descricao, req.nota, urgencia, now)) {
      return Response.status(Response.Status.SERVICE_UNAVAILABLE)
          .entity(new ErrorResponse("unable to enqueue feedback"))
          .build();
    }

    return Response.status(Response.Status.CREATED)
        .entity(new AvaliacaoResponse(id, req.descricao, req.nota, urgencia.name(), TS.format(now)))
        .build();
  }

  private static Urgencia classificar(int nota) {
    if (nota <= 3) return Urgencia.CRITICA;
    if (nota <= 6) return Urgencia.ATENCAO;
    return Urgencia.OK;
  }

  private static boolean enfileirarIngest(String id, String descricao, int nota, Urgencia urgencia, Instant createdAt) {
    var queueName = envOrDefault("FEEDBACK_INGEST_QUEUE_NAME", "feedback-ingest");
    try {
      var payload = new FeedbackIngestMessage(id, descricao, nota, urgencia.name(), TS.format(createdAt));
      var json = MAPPER.writeValueAsString(payload);
      AzureQueuePublisher.fromConnectionString(System.getenv("AZURE_STORAGE_CONNECTION_STRING"), queueName)
          .sendBase64(json);
      LOG.infof("feedback.enqueued queue=%s id=%s urgencia=%s", queueName, id, urgencia.name());
      return true;
    } catch (Exception ignored) {
      LOG.warnf("feedback.enqueue_failed queue=%s id=%s", queueName, id);
      return false;
    }
  }

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }

  record AvaliacaoResponse(String id, String descricao, int nota, String urgencia, String dataEnvioUtc) {}

  record ErrorResponse(String message) {}
}

