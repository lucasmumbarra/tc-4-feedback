package br.com.fiap.tc.feedback.application.dto.message;

public record CriticalFeedbackMessage(String descricao, String urgencia, String dataEnvioUtc) {}

