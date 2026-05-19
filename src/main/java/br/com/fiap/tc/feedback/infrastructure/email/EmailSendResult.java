package br.com.fiap.tc.feedback.infrastructure.email;

/** Resultado do envio (ou simulação) via SendGrid. */
public record EmailSendResult(
    String mode, Integer statusCode, String errorDetail, String fromEmail, String toEmail) {}
