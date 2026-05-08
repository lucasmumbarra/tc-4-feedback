package br.com.fiap.tc.feedback.function.http;

import br.com.fiap.tc.feedback.application.dto.request.AvaliacaoRequest;
import br.com.fiap.tc.feedback.application.dto.response.AvaliacaoResponse;
import br.com.fiap.tc.feedback.domain.model.Urgencia;
import br.com.fiap.tc.feedback.infrastructure.database.TableFeedbackRepository;
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

@Path("/avaliacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SubmitFeedbackFunction {
  private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

  @Inject TableFeedbackRepository repo;

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

    var createdAt = Instant.now();
    var urgencia = classificar(req.nota);
    var row = repo.save(req.descricao, req.nota, urgencia, createdAt);

    var resp =
        new AvaliacaoResponse(row.id(), row.descricao(), row.nota(), row.urgencia(), TS.format(createdAt));
    return Response.status(Response.Status.CREATED).entity(resp).build();
  }

  private static Urgencia classificar(int nota) {
    if (nota <= 3) return Urgencia.CRITICA;
    if (nota <= 6) return Urgencia.ATENCAO;
    return Urgencia.OK;
  }

  record ErrorResponse(String message) {}
}

