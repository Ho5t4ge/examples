import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StrategyPatternJasper {

  protected JasperPrint compileAndFillReport(String template, Map<String, Object> mappedJobParams, List<Map<String, Object>> jdbcParams, long taskId) {
    try {
      JasperReport jasperReport = JasperCompileManager.compileReport(new ByteArrayInputStream(template.getBytes()));
      JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(jdbcParams);
      return JasperFillManager.fillReport(jasperReport, mappedJobParams, dataSource);
    } catch (Exception e) {
      throw new WIRuntimeException(e, "Error during compile and fill jasper report for task %d", taskId);
    }
  }

  protected void compressDirectory(String tempPath, long taskId) {
    try {
      CompressUtils.compressDirectory(tempPath);
    } catch (IOException e) {
      throw new WIRuntimeException(e, "Error during compressing report directory for task %d", taskId);
    }
  }

  public String export(Map<String, Object> mappedJobParams, List<Map<String, Object>> jdbcParams, String tempPath, Long taskId) {
    String extension = getExtension();
    for (Map<String, String> template : (List<Map<String, String>>) mappedJobParams.get("jasperreporttemplates")) {
      try {
        JasperPrint print = compileAndFillReport(template.get("jasperTemplate"), mappedJobParams, jdbcParams, taskId);
        JRAbstractExporter exporter = setExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(setExporterOutput(tempPath, String.format("%s.%s", template.get("title"), extension)));
        exporter.exportReport();
      } catch (Exception e) {
        throw new WIRuntimeException(e, "Error during creating %s jasper report for task %d", extension, taskId);
      }
    }
    compressDirectory(tempPath, taskId);
    JasperOperationUtils.deleteWithoutZIP(new File(tempPath));
    return tempPath + "archive.zip";
  }

  protected abstract JRAbstractExporter setExporter();

  protected abstract ExporterOutput setExporterOutput(String tempPath, String title);

  protected abstract String getExtension();

  public abstract FormatType getReportFormat();
}
