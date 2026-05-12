package br.com.fiap.tc.feedback.infrastructure.messaging;

/** Nome da fila de feedback crítico — tem de coincidir com o recurso Bicep e com o produtor. */
public final class FeedbackQueueNames {
  public static final String CRITICAL_FEEDBACK = "critical-feedback";

  private FeedbackQueueNames() {}
}
