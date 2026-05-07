CREATE TABLE feedback (
  id VARCHAR(36) NOT NULL,
  descricao TEXT NOT NULL,
  nota INT NOT NULL,
  urgencia VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_feedback_created_at (created_at)
);

