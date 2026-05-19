package br.com.fiap.tc.feedback.infrastructure.email;

public record EmailSendResult(
    String mode, Integer statusCode, String errorDetail, String fromEmail, String toEmail) {}
