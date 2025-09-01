import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CriteriaApiExample {

  @Transactional
  private List<ProjectLight> findByDateInterval(RequestParams params) {
    LOG.trace("Get project list by parameters: %s", params);
    Specification<Project> spec = (root, criteriaQuery, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (!params.getSelectedObjects().isEmpty()) {
        List<Predicate> selectedObjectsPredicates = new ArrayList<>();
        if (!params.getSelectedObjects().getEnterprises().isEmpty()) {
          selectedObjectsPredicates.add(root.get("enterprise").get("id").in(params.getSelectedObjects().getEnterprises()));
        }
        if (!params.getSelectedObjects().getFields().isEmpty()) {
          selectedObjectsPredicates.add(root.get("field").get("id").in(params.getSelectedObjects().getFields()));
        }
        if (!params.getSelectedObjects().getClusters().isEmpty()) {
          selectedObjectsPredicates.add(root.get("wellName").in(wellRepository.findAllById(params.getSelectedObjects().getWells())));
        }
        if (!params.getSelectedObjects().getWells().isEmpty()) {
          selectedObjectsPredicates.add(root.get("clusterName").in(clusterRepository.findAllById(params.getSelectedObjects().getClusters())));
        }
        predicates.add(criteriaBuilder.or(selectedObjectsPredicates.toArray(new Predicate[0])));
      }
      if (params.getStart() != null && params.getEnd() != null) {
        predicates.add(params.isAddUnaccounted ?
          criteriaBuilder.or(
            criteriaBuilder.equal(root.get("state"), ProjectState.GRANTED_NO_NK),
            criteriaBuilder.isNull(root.get("factDate")),
            criteriaBuilder.between(root.get("factDate"), params.getStart(), params.getEnd())
          )
          : criteriaBuilder.between(root.get("factDate"), params.getStart(), params.getEnd())
        );
      }
      if (predicates.isEmpty()) {
        throw new WIRuntimeException("Predicates list is empty");
      }
      List<Order> orderList = new ArrayList<>();
      orderList.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
        .when(root.get("state").in(List.of(ProjectState.GRANTED_NO_NK, ProjectState.GRANTED)), 2)
        .when(root.get("state").in(inWorkStages), 0).otherwise(1)));
      orderList.add(criteriaBuilder.desc(
        criteriaBuilder.selectCase()
          .when(criteriaBuilder.isNotNull(root.get("factDate")), root.get("factDate"))
          .when(criteriaBuilder.isNull(root.get("planDate")), criteriaBuilder.literal(LocalDate.of(2100, 12, 31)))
          .otherwise(root.get("planDate"))
      ));
      criteriaQuery.orderBy(orderList.toArray(new Order[0]));
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
    List<Project> projects = projectRepository.findAll(spec);
    return projects.stream().map(ProjectLight::new).collect(Collectors.toList());
  }


  public boolean checkRemastered(Set<AdditionalObject> additionalObjects, long id) {
    Specification<PreDesignWork> spec = (root, criteriaQuery, criteriaBuilder) -> {
      Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
      Root<AdditionalObject> subRoot = subquery.from(AdditionalObject.class);
      subquery.select(subRoot.get("preDesignWork").get("id"));
      List<Predicate> combinedPredicates = new ArrayList<>();
      for (AdditionalObject additionalObject : additionalObjects.stream().filter(item -> item.typeEquals(ObjectType.CLUSTER)).collect(Collectors.toSet())) {
        List<Predicate> exactMatchPredicates = new ArrayList<>();
        exactMatchPredicates.add(criteriaBuilder.equal(subRoot.get("enterprise").get("id"), additionalObject.getEnterprise().getId()));
        exactMatchPredicates.add(criteriaBuilder.equal(subRoot.get("field").get("id"), additionalObject.getField().getId()));
        exactMatchPredicates.add(criteriaBuilder.equal(subRoot.get("clusterName"), additionalObject.getClusterName()));
        combinedPredicates.add(criteriaBuilder.and(exactMatchPredicates.toArray(new Predicate[0])));
      }
      Predicate idNotEqualPredicate = criteriaBuilder.notEqual(subRoot.get("preDesignWork").get("id"), id);
      Predicate isWorkComplete = criteriaBuilder.isNotNull(subRoot.get("preDesignWork").get("closingMark"));
      Predicate combinedPredicate = criteriaBuilder.and(
        idNotEqualPredicate,
        isWorkComplete,
        criteriaBuilder.or(combinedPredicates.toArray(new Predicate[0]))
      );
      subquery.where(combinedPredicate);
      return criteriaBuilder.in(root.get("id")).value(subquery);
    };
    return preDesignWorkRepo.exists(spec);
  }

  public Long getLatestWorkIdByWell(LatestPdwBody latestPdwBody) {
    Specification<PreDesignWork> spec = (root, criteriaQuery, criteriaBuilder) -> {
      Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
      Root<AdditionalObject> subRoot = subquery.from(AdditionalObject.class);
      subquery.select(subRoot.get("preDesignWork").get("id"));
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(criteriaBuilder.equal(subRoot.get("wellName"), latestPdwBody.getWellName()));
      predicates.add(criteriaBuilder.equal(subRoot.get("clusterName"), latestPdwBody.getClusterName()));
      predicates.add(criteriaBuilder.equal(subRoot.get("field").get("id"), latestPdwBody.getFieldId()));
      predicates.add(criteriaBuilder.equal(subRoot.get("enterprise").get("id"), latestPdwBody.getEnterpriseId()));
      subquery.where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));
      return criteriaBuilder.in(root.get("id")).value(subquery);
    };
    Sort sort = Sort.by(Sort.Direction.DESC, "closingMark");
    List<PreDesignWork> latestWorks = preDesignWorkRepo.findAll(spec, sort);
    return latestWorks.isEmpty() ? null : latestWorks.get(0).getId();
  }
}
