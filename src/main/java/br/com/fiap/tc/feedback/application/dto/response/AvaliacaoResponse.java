package br.com.fiap.tc.feedback.application.dto.response;

public record AvaliacaoResponse(
    String id, String descricao, int nota, String urgencia, String dataEnvioUtc) {}

