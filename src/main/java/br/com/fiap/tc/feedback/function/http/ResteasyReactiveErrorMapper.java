package br.com.fiap.tc.feedback.function.http;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ResteasyReactiveErrorMapper {
  private static final Logger LOG = Logger.getLogger(ResteasyReactiveErrorMapper.class);

  @ServerExceptionMapper
  public Response mapBadRequest(BadRequestException e) {
    var cause = e.getCause();
    var msg = (cause != null && cause.getMessage() != null) ? cause.getMessage() : e.getMessage();
    LOG.warnf(e, "http.bad_request %s", msg);
    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON)
        .entity(new ErrorResponse(msg == null ? "bad request" : msg))
        .build();
  }

  @ServerExceptionMapper
  public Response mapWebApplication(WebApplicationException e) {
    var status = e.getResponse() != null ? e.getResponse().getStatus() : 500;
    var cause = e.getCause();
    var msg = (cause != null && cause.getMessage() != null) ? cause.getMessage() : e.getMessage();
    if (status >= 500) {
      LOG.errorf(e, "http.web_application_error status=%d %s", status, msg);
    } else {
      LOG.warnf(e, "http.web_application_error status=%d %s", status, msg);
    }
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .entity(new ErrorResponse(msg == null ? "request failed" : msg))
        .build();
  }

  @ServerExceptionMapper
  public Response mapThrowable(Throwable e) {
    LOG.error("http.unhandled_error", e);
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .type(MediaType.APPLICATION_JSON)
        .entity(new ErrorResponse("internal error"))
        .build();
  }

  record ErrorResponse(String message) {}
}

