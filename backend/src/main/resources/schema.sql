CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  password VARCHAR(160) NOT NULL,
  nickname VARCHAR(80),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  content CLOB NOT NULL,
  record_type VARCHAR(30) NOT NULL,
  mood VARCHAR(30),
  summary CLOB,
  keywords VARCHAR(500),
  graph_status VARCHAR(20) DEFAULT 'SUCCESS',
  graph_stage VARCHAR(40),
  graph_error CLOB,
  graph_updated_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE record ADD COLUMN IF NOT EXISTS graph_status VARCHAR(20) DEFAULT 'SUCCESS';
ALTER TABLE record ADD COLUMN IF NOT EXISTS graph_stage VARCHAR(40);
ALTER TABLE record ADD COLUMN IF NOT EXISTS graph_error CLOB;
ALTER TABLE record ADD COLUMN IF NOT EXISTS graph_updated_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(60) NOT NULL,
  UNIQUE(user_id, name)
);

CREATE TABLE IF NOT EXISTS record_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS record_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content CLOB NOT NULL,
  embedding CLOB NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  node_type VARCHAR(30) NOT NULL,
  name VARCHAR(200) NOT NULL,
  normalized_name VARCHAR(220) NOT NULL,
  description CLOB,
  source_record_id BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_node_id BIGINT NOT NULL,
  target_node_id BIGINT NOT NULL,
  relation_type VARCHAR(40) NOT NULL,
  weight DOUBLE DEFAULT 0.5,
  confidence DOUBLE DEFAULT 0.5,
  evidence_text CLOB,
  source_record_id BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_node_user_name
  ON knowledge_node (user_id, normalized_name);
CREATE INDEX IF NOT EXISTS idx_knowledge_node_user_record
  ON knowledge_node (user_id, source_record_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_relation_user_source
  ON knowledge_relation (user_id, source_node_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_relation_user_target
  ON knowledge_relation (user_id, target_node_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_relation_user_record
  ON knowledge_relation (user_id, source_record_id);

CREATE TABLE IF NOT EXISTS companion_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  feedback_type VARCHAR(40) NOT NULL,
  content CLOB NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 保存问答本体
CREATE TABLE IF NOT EXISTS qa_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question CLOB NOT NULL,
  answer CLOB NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--保存答案来源
CREATE TABLE IF NOT EXISTS qa_reference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  --属于哪条问答
  qa_record_id BIGINT NOT NULL,
  --来源笔记ID
  record_id BIGINT NOT NULL,
  chunk_id BIGINT,
  --展示给前端的来源片段。
  snippet CLOB NOT NULL,
  --本次检索排序后的分数
  similarity DOUBLE NOT NULL
);
