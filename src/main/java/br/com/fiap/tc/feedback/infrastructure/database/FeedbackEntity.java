package br.com.fiap.tc.feedback.infrastructure.database;

import br.com.fiap.tc.feedback.domain.model.Urgencia;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "feedback")
public class FeedbackEntity extends PanacheEntityBase {
  @Id
  @Column(length = 36, nullable = false)
  public String id;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String descricao;

  @Column(nullable = false)
  public int nota;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public Urgencia urgencia;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}

