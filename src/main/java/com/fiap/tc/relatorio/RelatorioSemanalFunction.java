package com.fiap.tc.relatorio;

import com.fiap.tc.infra.AcsEmailSender;
import com.fiap.tc.infra.CosmosFeedbackRepository;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;

public class RelatorioSemanalFunction {
  // NCRONTAB: {second} {minute} {hour} {day} {month} {day-of-week}
  // Segunda-feira às 09:00 UTC.
  @FunctionName("relatorioSemanal")
  public void run(
      @TimerTrigger(name = "timer", schedule = "0 0 9 * * 1") String timerInfo, final ExecutionContext context) {
    var to = System.getenv("ADMIN_EMAIL_TO");
    var from = System.getenv("EMAIL_FROM");
    var acs = System.getenv("ACS_EMAIL_CONNECTION_STRING");
    if (to == null || to.isBlank() || from == null || from.isBlank() || acs == null || acs.isBlank()) {
      context.getLogger().warning("Relatório semanal: variáveis de e-mail não configuradas.");
      return;
    }

    var end = LocalDate.now(ZoneOffset.UTC);
    var start = end.minusDays(6);
    var feedbacks = CosmosFeedbackRepository.fromEnv().listBetweenInclusive(start, end);

    var porDia = new java.util.TreeMap<LocalDate, Integer>();
    var porUrgencia = new EnumMap<com.fiap.tc.avaliacao.Urgencia, Integer>(com.fiap.tc.avaliacao.Urgencia.class);
    for (var u : com.fiap.tc.avaliacao.Urgencia.values()) porUrgencia.put(u, 0);

    long somaNotas = 0;
    for (var f : feedbacks) {
      var dia = LocalDate.parse(f.day());
      porDia.merge(dia, 1, Integer::sum);
      porUrgencia.merge(com.fiap.tc.avaliacao.Urgencia.valueOf(f.urgencia()), 1, Integer::sum);
      somaNotas += f.nota();
    }
    double media = feedbacks.isEmpty() ? 0.0 : (double) somaNotas / (double) feedbacks.size();

    var now = Instant.now();
    var subject = "Relatório semanal de feedbacks";
    var body = buildBody(start, end, media, porDia, porUrgencia, now);

    try {
      AcsEmailSender.fromEnv(acs, from).sendPlainText(to, subject, body);
      context.getLogger().info("Relatório semanal enviado com sucesso.");
    } catch (Exception e) {
      context.getLogger().severe("Falha ao enviar relatório semanal: " + e.getMessage());
    }
  }

  private static String buildBody(
      LocalDate start,
      LocalDate end,
      double media,
      java.util.Map<LocalDate, Integer> porDia,
      java.util.Map<com.fiap.tc.avaliacao.Urgencia, Integer> porUrgencia,
      Instant dataEnvio) {
    var sb = new StringBuilder();
    sb.append("Período (UTC): ").append(start).append(" a ").append(end).append("\n");
    sb.append("Data de envio (UTC): ").append(dataEnvio).append("\n");
    sb.append("Média de avaliações: ").append(String.format(java.util.Locale.US, "%.2f", media)).append("\n\n");

    sb.append("Quantidade de avaliações por dia:\n");
    if (porDia.isEmpty()) {
      sb.append("- (sem dados)\n");
    } else {
      for (var e : porDia.entrySet()) {
        sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
      }
    }
    sb.append("\nQuantidade de avaliações por urgência:\n");
    for (var e : porUrgencia.entrySet()) {
      sb.append("- ").append(e.getKey().name()).append(": ").append(e.getValue()).append("\n");
    }
    return sb.toString();
  }

}

