package com.fiap.tc.avaliacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.tc.infra.AzureQueuePublisher;
import com.fiap.tc.infra.CosmosFeedbackRepository;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Path("/avaliacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AvaliacaoResource {
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @POST
  public Response criar(AvaliacaoRequest req) {
    if (req == null || req.descricao == null || req.descricao.isBlank()) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("descricao is required")).build();
    }
    if (req.nota < 0 || req.nota > 10) {
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("nota must be between 0 and 10")).build();
    }

    var now = Instant.now();
    var urgencia = classificar(req.nota);

    var saved = CosmosFeedbackRepository.fromEnv().save(req.descricao, req.nota, urgencia, now);

    if (urgencia == Urgencia.CRITICA) {
      tentarEnfileirarCritico(req.descricao, urgencia, now);
    }

    return Response.status(Response.Status.CREATED)
        .entity(
            new AvaliacaoResponse(
                saved.id(), saved.descricao(), saved.nota(), saved.urgencia(), saved.createdAt()))
        .build();
  }

  private static Urgencia classificar(int nota) {
    if (nota <= 3) return Urgencia.CRITICA;
    if (nota <= 6) return Urgencia.ATENCAO;
    return Urgencia.OK;
  }

  private static void tentarEnfileirarCritico(String descricao, Urgencia urgencia, Instant createdAt) {
    var queueName = envOrDefault("CRITICAL_FEEDBACK_QUEUE_NAME", AzureQueuePublisher.DEFAULT_CRITICAL_QUEUE);
    try {
      var payload = new CriticalFeedbackMessage(descricao, urgencia.name(), TS.format(createdAt));
      var json = MAPPER.writeValueAsString(payload);
      AzureQueuePublisher.fromConnectionString(System.getenv("AZURE_STORAGE_CONNECTION_STRING"), queueName)
          .sendBase64(json);
    } catch (Exception ignored) {
      // Intencional: fila não deve derrubar a ingestão.
    }
  }

  private static String envOrDefault(String name, String def) {
    var v = System.getenv(name);
    return (v == null || v.isBlank()) ? def : v;
  }

  record AvaliacaoResponse(String id, String descricao, int nota, String urgencia, String dataEnvioUtc) {}

  record ErrorResponse(String message) {}
}

