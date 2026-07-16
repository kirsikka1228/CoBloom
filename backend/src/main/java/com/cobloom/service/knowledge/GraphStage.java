package com.cobloom.service.knowledge;

public enum GraphStage {
  QUEUED,
  EXTRACTING_STRUCTURE,
  EXTRACTING_RELATIONS,
  PERSISTING,
  COMPLETE,
  FAILED
}
