package br.com.fiap.tc.feedback.domain.policy;

import br.com.fiap.tc.feedback.domain.model.Urgencia;

public final class UrgenciaPolicy {

  private UrgenciaPolicy() {}

  public static Urgencia classify(int nota) {
    if (nota <= 3) return Urgencia.CRITICA;
    if (nota <= 6) return Urgencia.ATENCAO;
    return Urgencia.OK;
  }
}
