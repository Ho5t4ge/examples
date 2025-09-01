import java.util.function.Function;

public class StrategyPattern {
   /**
   * Реализовал получение данных из базы с использованием Spring Data JPA репозиториев с динамической загрузкой реализации специфической логики по коду системы.
   * Применял паттерн стратегий для выбора конкретной реализации интерфейса на основе кода документа, что обеспечивает расширяемость и адаптивность бизнес-логики.
   * Инкапсулировал преобразование данных в DTO для передачи между слоями приложения**/
  
  public UDocumentHdrDto getHdrById(long documentSystemId, long documentNameId, long hdrId) {
    UDocumentHdrSystem uDocumentHdrSystem = uDocumentHdrSystemRepository.findBySystemNameAndHdrId(documentSystemId, documentNameId, hdrId);
    IUDocumentSystemEditSpecific specific = (IUDocumentSystemEditSpecific) getSystemSpecific(uDocumentHdrSystem.getDocumentSystemName().getDocumentSystem().getCode());
    UDocumentHdr hdr;
    Function<AbstractDocumentSpecific, Object> convertor = null;
    if (specific != null) {
      hdr = specific.getHdrById(hdrId);
      convertor = specific.getConvertor();
    } else {
      hdr = uDocumentHdrRepository.findById(hdrId);
    }
    hdr.setStatus(uDocumentHdrSystem.getDocumentStatus());
    hdr.setState(uDocumentHdrSystem.getDocumentState());
    return new UDocumentHdrDto(hdr, documentSystemId, documentNameId, convertor);
  }

  @Nullable
  public IUDocumentSystemSpecific getSystemSpecific(String documentSystemCode) {
    for (IUDocumentSystemSpecific specific : systemSpecifics) {
      if (specific.isEnable(documentSystemCode)) {
        return specific;
      }
    }
    return null;
  }
}

