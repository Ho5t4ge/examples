import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class ExecutorService {

  /**
   * Реализовал многопоточную обработку и экспорт данных с использованием ExecutorService и Future для параллельного выполнения задач.
   * Обеспечил обработку ошибок с возможностью игнорирования частичных сбоев и генерацией отчетов по неуспешным операциям.
   * Автоматизировал упаковку результата в архив и очистку временных директорий.
   * Использовал Spring ApplicationContext для динамического получения компонентов.
   * **/

  @Value("${export.ignoreErrors:false}")
  private boolean ignoreErrors;

  public String export(List<Long> ids, String tempPath, Long taskId, boolean ignoreHierarchy) throws Exception {
    List<Future<?>> futures = new ArrayList<>();
    List<String> brokenZakMessages = new ArrayList<>();
    for (Long id : ids) {
      ZakExecutor zakExecutor = applicationContext.getBean(ZakExecutor.class);
      zakExecutor.setTempPath(tempPath);
      zakExecutor.setZakFileId(id);
      zakExecutor.setIgnoreHierarchy(ignoreHierarchy);
      Future<?> future = executorService.submit(zakExecutor);
      futures.add(future);
    }
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (Exception e) {
        if (ignoreErrors) {
          brokenZakMessages.add(e.getMessage());
        } else {
          throw e;
        }
      }
    }
    if (!brokenZakMessages.isEmpty()) {
      if (brokenZakMessages.size() == ids.size()) {
        throw new EmptyZakException("Cannot find any files with ids: %s", ids);
      }
      saveReportForBrokenZak(brokenZakMessages, tempPath);
    }
    try {
      CompressUtils.compressDirectory(tempPath);
    } catch (IOException e) {
      throw new WIRuntimeException(e, "Error during compressing report directory for task %s", taskId);
    }
    ZakOperationUtils.deleteWithoutZIP(new File(tempPath));
    return tempPath + "archive.zip";
  }
}
