package br.com.fiap.tc.feedback.function.http;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
  private static final Logger LOG = Logger.getLogger(BadRequestExceptionMapper.class);

  @Override
  public Response toResponse(BadRequestException exception) {
    var cause = exception.getCause();
    var msg = (cause != null && cause.getMessage() != null) ? cause.getMessage() : exception.getMessage();
    LOG.warnf(exception, "http.bad_request %s", msg);
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON)
        .entity(new ErrorResponse(msg == null ? "bad request" : msg))
        .build();
  }

  record ErrorResponse(String message) {}
}

