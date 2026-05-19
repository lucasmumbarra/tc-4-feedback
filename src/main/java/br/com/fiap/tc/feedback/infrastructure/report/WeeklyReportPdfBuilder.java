package br.com.fiap.tc.feedback.infrastructure.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WeeklyReportPdfBuilder {
  private static final Logger LOG = Logger.getLogger(WeeklyReportPdfBuilder.class);
  private static final int MAX_DESC_LEN = 280;

  private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
  private final Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
  private final Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

  public byte[] build(WeeklyReportData data) {
    try (var out = new ByteArrayOutputStream()) {
      var doc = new Document(PageSize.A4, 36, 36, 48, 48);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(new Paragraph("Relatório semanal de feedbacks", titleFont));
      doc.add(spacer());
      doc.add(new Paragraph("Período (UTC): " + data.start() + " a " + data.end(), bodyFont));
      doc.add(
          new Paragraph(
              "Média das notas: "
                  + (data.hasRows()
                      ? String.format(Locale.US, "%.2f", data.average())
                      : "n/d (sem avaliações)"),
              bodyFont));
      doc.add(spacer());

      doc.add(new Paragraph("Quantidade de avaliações por dia", headingFont));
      doc.add(countByDayTable(data.countByDay()));
      doc.add(spacer());

      doc.add(new Paragraph("Quantidade de avaliações por urgência", headingFont));
      doc.add(countByUrgencyTable(data.countByUrgency()));
      doc.add(spacer());

      doc.add(new Paragraph("Avaliações do período", headingFont));
      doc.add(
          new Paragraph(
              "Para cada avaliação: descrição, urgência e data de envio.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));
      doc.add(feedbackDetailTable(data));

      doc.close();
      var bytes = out.toByteArray();
      LOG.infof("weeklyReport.pdf_built bytes=%d rows=%d", bytes.length, data.total());
      return bytes;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to build weekly report PDF", e);
    }
  }

  private PdfPTable countByDayTable(Map<String, Integer> counts) throws DocumentException {
    var table = new PdfPTable(2);
    table.setWidthPercentage(55);
    table.setSpacingBefore(4f);
    table.addCell(headerCell("Dia (UTC)"));
    table.addCell(headerCell("Quantidade de avaliações"));
    if (counts.isEmpty()) {
      table.addCell(bodyCell("(nenhuma)"));
      table.addCell(bodyCell("0"));
    } else {
      counts.forEach(
          (day, qty) -> {
            table.addCell(bodyCell(day));
            table.addCell(bodyCell(String.valueOf(qty)));
          });
    }
    return table;
  }

  private PdfPTable countByUrgencyTable(Map<String, Integer> counts) throws DocumentException {
    var table = new PdfPTable(2);
    table.setWidthPercentage(55);
    table.setSpacingBefore(4f);
    table.addCell(headerCell("Urgência"));
    table.addCell(headerCell("Quantidade de avaliações"));
    if (counts.isEmpty()) {
      table.addCell(bodyCell("(nenhuma)"));
      table.addCell(bodyCell("0"));
    } else {
      counts.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              e -> {
                table.addCell(bodyCell(e.getKey()));
                table.addCell(bodyCell(String.valueOf(e.getValue())));
              });
    }
    return table;
  }

  private PdfPTable feedbackDetailTable(WeeklyReportData data) throws DocumentException {
    var table = new PdfPTable(3);
    table.setWidthPercentage(100);
    table.setWidths(new float[] {45f, 18f, 37f});
    table.setSpacingBefore(6f);
    table.addCell(headerCell("Descrição"));
    table.addCell(headerCell("Urgência"));
    table.addCell(headerCell("Data de envio"));
    if (!data.hasRows()) {
      var cell = new PdfPCell(new Phrase("(nenhuma avaliação no período)", bodyFont));
      cell.setColspan(3);
      table.addCell(cell);
      return table;
    }
    for (var r : data.rows()) {
      table.addCell(bodyCell(truncate(r.descricao())));
      table.addCell(bodyCell(r.urgencia()));
      table.addCell(bodyCell(r.createdAt()));
    }
    return table;
  }

  private static PdfPCell headerCell(String text) {
    var cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    cell.setPadding(4f);
    return cell;
  }

  private static PdfPCell bodyCell(String text) {
    var cell = new PdfPCell(new Phrase(text == null ? "" : text, FontFactory.getFont(FontFactory.HELVETICA, 9)));
    cell.setPadding(3f);
    return cell;
  }

  private static Paragraph spacer() {
    var p = new Paragraph(" ");
    p.setSpacingAfter(8f);
    return p;
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= MAX_DESC_LEN ? s : s.substring(0, MAX_DESC_LEN) + "...";
  }
}
