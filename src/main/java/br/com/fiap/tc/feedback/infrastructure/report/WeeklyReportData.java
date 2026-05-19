package br.com.fiap.tc.feedback.infrastructure.report;

import br.com.fiap.tc.feedback.infrastructure.database.TableFeedbackRepository.FeedbackRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklyReportData(
    LocalDate start,
    LocalDate end,
    List<FeedbackRow> rows,
    double average,
    Map<String, Integer> countByDay,
    Map<String, Integer> countByUrgency) {

  public int total() {
    return rows.size();
  }

  public boolean hasRows() {
    return !rows.isEmpty();
  }
}
