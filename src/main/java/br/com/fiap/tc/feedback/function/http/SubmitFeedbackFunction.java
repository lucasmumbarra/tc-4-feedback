package br.com.fiap.tc.feedback.function.http;

import br.com.fiap.tc.feedback.application.dto.request.AvaliacaoRequest;
import br.com.fiap.tc.feedback.application.dto.response.AvaliacaoResponse;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.domain.policy.UrgenciaPolicy;
import br.com.fiap.tc.feedback.infrastructure.database.TableFeedbackRepository;
import br.com.fiap.tc.feedback.infrastructure.email.CriticalEmailFunctionClient;
import jakarta.inject.Inject;
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

  @Inject TableFeedbackRepository repo;
  @Inject CriticalEmailFunctionClient criticalEmailClient;

  @POST
  public Response criar(AvaliacaoRequest req) {
    final var traceId = UUID.randomUUID().toString();
    final var startNs = System.nanoTime();

    LOG.infof(
        "submitFeedback.start traceId=%s hasBody=%s descricaoLen=%s nota=%s",
        traceId,
        (req != null),
        (req == null || req.descricao == null) ? "null" : String.valueOf(req.descricao.length()),
        (req == null) ? "null" : String.valueOf(req.nota));

    if (req == null || req.descricao == null || req.descricao.isBlank()) {
      LOG.warnf("submitFeedback.validation_failed traceId=%s reason=descricao_required", traceId);
      return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse("descricao is required")).build();
    }
    if (req.nota < 0 || req.nota > 10) {
      LOG.warnf("submitFeedback.validation_failed traceId=%s reason=nota_range nota=%d", traceId, req.nota);
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("nota must be between 0 and 10"))
          .build();
    }

    var createdAt = Instant.now();
    var urgencia = UrgenciaPolicy.classify(req.nota);
    LOG.infof("submitFeedback.classified traceId=%s urgencia=%s", traceId, urgencia.name());

    try {
      var row = repo.save(req.descricao, req.nota, urgencia, createdAt);

      var resp =
          new AvaliacaoResponse(row.id(), row.descricao(), row.nota(), row.urgencia(), TS.format(createdAt));

      if (urgencia == Urgencia.CRITICA) {
        criticalEmailClient.invokeAsync(row.id(), row.descricao(), urgencia, row.createdAt());
        LOG.infof("submitFeedback.email_dispatched traceId=%s id=%s", traceId, row.id());
      }

      var elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
      LOG.infof("submitFeedback.success traceId=%s id=%s elapsedMs=%d", traceId, row.id(), elapsedMs);
      return Response.status(Response.Status.CREATED).entity(resp).build();
    } catch (Exception e) {
      var elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
      LOG.errorf(e, "submitFeedback.error traceId=%s elapsedMs=%d", traceId, elapsedMs);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new ErrorResponse("internal error"))
          .build();
    }
  }

  record ErrorResponse(String message) {}
}
