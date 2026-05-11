package br.com.fiap.tc.feedback.domain.policy;

import br.com.fiap.tc.feedback.domain.model.Urgencia;

public final class UrgenciaPolicy {

  private UrgenciaPolicy() {}

  /** Alinha com o enunciado: 0–3 crítica, 4–6 atenção, 7–10 ok. */
  public static Urgencia classify(int nota) {
    if (nota <= 3) return Urgencia.CRITICA;
    if (nota <= 6) return Urgencia.ATENCAO;
    return Urgencia.OK;
  }
}
