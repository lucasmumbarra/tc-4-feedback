package br.com.fiap.tc.feedback.infrastructure.report;

import br.com.fiap.tc.feedback.infrastructure.database.TableFeedbackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.TreeMap;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WeeklyReportService {
  private static final Logger LOG = Logger.getLogger(WeeklyReportService.class);

  @Inject TableFeedbackRepository repo;

  public WeeklyReportData buildLastSevenDays() {
    var end = LocalDate.now(ZoneOffset.UTC);
    var start = end.minusDays(6);
    return buildForPeriod(start, end);
  }

  public WeeklyReportData buildForPeriod(LocalDate start, LocalDate end) {
    LOG.infof("weeklyReport.query partitionKey from=%s to=%s (inclusive)", start, end);
    var rows = repo.listBetweenInclusive(start, end);
    var byDay = new TreeMap<String, Integer>();
    var byUrg = new HashMap<String, Integer>();
    long sumNota = 0;
    for (var r : rows) {
      byDay.merge(r.day(), 1, Integer::sum);
      byUrg.merge(r.urgencia(), 1, Integer::sum);
      sumNota += r.nota();
    }
    var avg = rows.isEmpty() ? 0.0 : sumNota / (double) rows.size();
    LOG.infof("weeklyReport.aggregated start=%s end=%s rows=%d avg=%.2f", start, end, rows.size(), avg);
    return new WeeklyReportData(start, end, rows, avg, byDay, byUrg);
  }

  public String pdfFileName(WeeklyReportData data) {
    return String.format(Locale.US, "relatorio-semana-%s-a-%s.pdf", data.start(), data.end());
  }

  public String plainTextSummary(WeeklyReportData data) {
    var sb = new StringBuilder(1024);
    sb.append("Relatório semanal de feedbacks\n");
    sb.append("Período (UTC): ").append(data.start()).append(" a ").append(data.end()).append("\n\n");
    sb.append("Média das notas: ");
    if (data.hasRows()) {
      sb.append(String.format(Locale.US, "%.2f", data.average())).append("\n\n");
    } else {
      sb.append("n/d (sem avaliações)\n\n");
    }
    sb.append("Quantidade de avaliações por dia:\n");
    if (data.countByDay().isEmpty()) {
      sb.append("  (nenhuma)\n");
    } else {
      data.countByDay().forEach((d, c) -> sb.append("  ").append(d).append(": ").append(c).append("\n"));
    }
    sb.append("\nQuantidade de avaliações por urgência:\n");
    if (data.countByUrgency().isEmpty()) {
      sb.append("  (nenhuma)\n");
    } else {
      data.countByUrgency().entrySet().stream()
          .sorted(java.util.Map.Entry.comparingByKey())
          .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
    }
    sb.append("\nAvaliações (descrição; urgência; data de envio):\n");
    for (var r : data.rows()) {
      sb.append("---\n");
      sb.append("Descrição: ").append(r.descricao()).append("\n");
      sb.append("Urgência: ").append(r.urgencia()).append("\n");
      sb.append("Data de envio: ").append(r.createdAt()).append("\n");
    }
    return sb.toString();
  }
}
