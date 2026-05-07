package br.com.fiap.tc.feedback.application.dto.message;

public record FeedbackIngestMessage(
    String id, String descricao, int nota, String urgencia, String dataEnvioUtc) {}

