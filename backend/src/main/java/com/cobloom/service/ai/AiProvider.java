package com.cobloom.service.ai;

public interface AiProvider {
  String name();

  String complete(String systemPrompt, String userPrompt);
}
