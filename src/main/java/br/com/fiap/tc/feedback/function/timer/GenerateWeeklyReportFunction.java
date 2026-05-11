package br.com.fiap.tc.feedback.function.timer;

import br.com.fiap.tc.feedback.infrastructure.database.TableFeedbackRepository;
import br.com.fiap.tc.feedback.infrastructure.report.WeeklyReportBlobWriter;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.jboss.logging.Logger;

/**
 * Relatório semanal (segunda-feiras 09:00 UTC): janela dos últimos 7 dias corridos terminando no dia
 * anterior à execução, com média das notas, contagens por dia e por urgência, e lista de avaliações.
 */
@ApplicationScoped
public class GenerateWeeklyReportFunction {
  private static final Logger LOG = Logger.getLogger(GenerateWeeklyReportFunction.class);

  @Inject TableFeedbackRepository repo;
  @Inject WeeklyReportBlobWriter blobWriter;

  @FunctionName("generateWeeklyReport")
  public void run(
      @TimerTrigger(name = "weekly", schedule = "0 0 9 * * MON") String timerInfo, final ExecutionContext context) {
    LOG.infof("generateWeeklyReport.start invocationId=%s timer=%s", context.getInvocationId(), timerInfo);
    var end = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    var start = end.minusDays(6);
    var rows = repo.listBetweenInclusive(start, end);

    var byDay = new TreeMap<String, Integer>();
    var byUrg = new HashMap<String, Integer>();
    long sumNota = 0;
    for (var r : rows) {
      byDay.merge(r.day(), 1, Integer::sum);
      byUrg.merge(r.urgencia(), 1, Integer::sum);
      sumNota += r.nota();
    }

    var sb = new StringBuilder(4096);
    sb.append("Relatório semanal de feedbacks\n");
    sb.append("Período (UTC): ").append(start).append(" a ").append(end).append("\n\n");
    sb.append("Média das notas: ");
    if (rows.isEmpty()) {
      sb.append("n/d (sem avaliações)\n");
    } else {
      sb.append(String.format(Locale.US, "%.2f", sumNota / (double) rows.size())).append("\n");
    }
    sb.append("\nQuantidade de avaliações por dia (UTC):\n");
    if (byDay.isEmpty()) {
      sb.append("  (nenhuma)\n");
    } else {
      byDay.forEach((d, c) -> sb.append("  ").append(d).append(": ").append(c).append("\n"));
    }
    sb.append("\nQuantidade de avaliações por urgência:\n");
    if (byUrg.isEmpty()) {
      sb.append("  (nenhuma)\n");
    } else {
      byUrg.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
    }
    sb.append("\nDetalhe das avaliações (descrição; urgência; data de envio):\n");
    for (var r : rows) {
      sb.append("---\n");
      sb.append(r.descricao()).append("\n");
      sb.append("Urgência: ").append(r.urgencia()).append("\n");
      sb.append("Data de envio: ").append(r.createdAt()).append("\n");
    }

    var blobName = String.format("relatorio-semana-%s-a-%s.txt", start, end);
    blobWriter.uploadUtf8(blobName, sb.toString());
    LOG.infof("generateWeeklyReport.done rows=%d blob=%s", rows.size(), blobName);
  }
}
