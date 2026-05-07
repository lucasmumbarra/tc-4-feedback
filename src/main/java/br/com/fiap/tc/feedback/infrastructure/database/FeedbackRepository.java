package br.com.fiap.tc.feedback.infrastructure.database;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class FeedbackRepository implements PanacheRepositoryBase<FeedbackEntity, String> {
  public List<FeedbackEntity> listBetweenInclusive(Instant startInclusive, Instant endInclusive) {
    return list("createdAt >= ?1 AND createdAt <= ?2 ORDER BY createdAt ASC", startInclusive, endInclusive);
  }
}

