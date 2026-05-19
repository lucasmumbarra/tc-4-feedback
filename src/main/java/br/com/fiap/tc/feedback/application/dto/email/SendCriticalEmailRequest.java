package br.com.fiap.tc.feedback.application.dto.email;

/** Payload do POST {@code /api/send-critical-email}. */
public class SendCriticalEmailRequest {
  public String feedbackId;
  public String descricao;
  public String urgencia;
  public String feedbackCreatedAt;
}
