package com.cobloom.service.rag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TextSimilarity {
  public double score(String a, String b) {
    Map<String, Integer> va = vectorize(a);
    Map<String, Integer> vb = vectorize(b);
    if (va.isEmpty() || vb.isEmpty()) return 0;
    Set<String> keys = new HashSet<>(va.keySet());
    keys.addAll(vb.keySet());
    double dot = 0, na = 0, nb = 0;
    for (String key : keys) {
      int x = va.getOrDefault(key, 0);
      int y = vb.getOrDefault(key, 0);
      dot += x * y;
      na += x * x;
      nb += y * y;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  private Map<String, Integer> vectorize(String text) {
    Map<String, Integer> map = new HashMap<>();
    String clean = text == null ? "" : text.toLowerCase().replaceAll("[^\\p{IsHan}a-z0-9]+", " ");
    for (String token : clean.split("\\s+")) {
      if (token.length() >= 2) map.merge(token, 1, Integer::sum);
      if (token.length() > 4 && token.matches(".*\\p{IsHan}.*")) {
        for (int i = 0; i < token.length() - 1; i++) map.merge(token.substring(i, i + 2), 1, Integer::sum);
      }
    }
    return map;
  }
}
