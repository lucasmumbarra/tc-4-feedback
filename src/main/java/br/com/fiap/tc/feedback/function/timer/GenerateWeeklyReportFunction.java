package br.com.fiap.tc.feedback.function.timer;

import br.com.fiap.tc.feedback.infrastructure.email.AdminEmailNotifier;
import br.com.fiap.tc.feedback.infrastructure.report.WeeklyReportBlobWriter;
import br.com.fiap.tc.feedback.infrastructure.report.WeeklyReportPdfBuilder;
import br.com.fiap.tc.feedback.infrastructure.report.WeeklyReportService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Relatório semanal (segundas, 09:00 UTC): lê feedbacks da Table Storage (últimos 7 dias UTC),
 * calcula média e agregações, gera PDF, envia por e-mail ao admin e grava cópia no Blob.
 */
@ApplicationScoped
public class GenerateWeeklyReportFunction {
  private static final Logger LOG = Logger.getLogger(GenerateWeeklyReportFunction.class);

  @Inject WeeklyReportService reportService;
  @Inject WeeklyReportPdfBuilder pdfBuilder;
  @Inject AdminEmailNotifier emailNotifier;
  @Inject WeeklyReportBlobWriter blobWriter;

  @FunctionName("generateWeeklyReport")
  public void run(
      @TimerTrigger(name = "weekly", schedule = "0 0 9 * * MON") String timerInfo, final ExecutionContext context) {
    LOG.infof("generateWeeklyReport.start invocationId=%s timer=%s", context.getInvocationId(), timerInfo);

    var data = reportService.buildLastSevenDays();
    var pdfBytes = pdfBuilder.build(data);
    var pdfName = reportService.pdfFileName(data);

    blobWriter.uploadPdf(pdfName, pdfBytes);

    var emailResult = emailNotifier.sendWeeklyReportPdf(data, pdfBytes, pdfName);
    LOG.infof(
        "generateWeeklyReport.done rows=%d avg=%.2f blob=%s emailMode=%s",
        data.total(),
        data.average(),
        pdfName,
        emailResult.mode());

    if ("SIMULATED".equals(emailResult.mode())) {
      LOG.warnf(
          "generateWeeklyReport.email_simulated missing=%s — PDF gravado no Blob, e-mail não enviado",
          emailResult.errorDetail());
    }
  }
}
