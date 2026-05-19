package br.com.fiap.tc.feedback.function.http;

import br.com.fiap.tc.feedback.application.dto.email.SendCriticalEmailRequest;
import br.com.fiap.tc.feedback.function.email.SendCriticalEmailFunction;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/send-critical-email")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SendCriticalEmailResource {

  @Inject SendCriticalEmailFunction sendCriticalEmailFunction;

  @POST
  public Response send(SendCriticalEmailRequest dto) {
    try {
      sendCriticalEmailFunction.process(dto);
      return Response.status(Response.Status.ACCEPTED).entity("accepted").build();
    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }
  }
}
