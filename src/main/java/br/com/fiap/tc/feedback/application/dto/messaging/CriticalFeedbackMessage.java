package br.com.fiap.tc.feedback.application.dto.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CriticalFeedbackMessage {
  public String id;
  public String descricao;
  public int nota;
  public String urgencia;
  public String createdAt;
}
