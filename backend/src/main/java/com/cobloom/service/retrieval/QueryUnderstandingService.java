package com.cobloom.service.retrieval;
//查询扩展做了几件事：

//1. 保留原始问题 original。2. 分词生成 terms。3. 识别英文缩写，例如 ADC、GPIO、STM32。
// //4. 根据硬编码别名表补充 aliases。5. 对嵌入式相关术语补充 domainHints，例如 STM32 ADC。6. 拼接成 expandedText，用于向量检索。

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueryUnderstandingService {
  private static final Logger log = LoggerFactory.getLogger(QueryUnderstandingService.class);
  private static final Pattern ACRONYM = Pattern.compile("[A-Za-z][A-Za-z0-9]{1,}");
  private static final Map<String, List<String>> ALIASES = aliases();
  private static final List<String> STOP_WORDS = List.of(
      "什么", "是什么", "定义", "如何", "怎么", "以及", "这个", "那个", "我的", "我们",
      "the", "and", "for", "with", "what", "how", "why", "is", "are");

  public ExpandedQuery expand(String question) {
    String original = safe(question);
    Set<String> terms = new LinkedHashSet<>();
    Set<String> aliases = new LinkedHashSet<>();
    Set<String> domainHints = new LinkedHashSet<>();

    for (String token : tokens(original)) {
      if (!isStopWord(token))
        terms.add(token);
    }

    Matcher matcher = ACRONYM.matcher(original);
    while (matcher.find()) {
      String acronym = matcher.group().toUpperCase(Locale.ROOT);
      terms.add(acronym);
      List<String> knownAliases = ALIASES.get(acronym);
      if (knownAliases != null)
        aliases.addAll(knownAliases);
      if (isEmbeddedTerm(acronym))
        domainHints.add("STM32 " + acronym);
    }

    String compact = normalize(original).replace(" ", "");
    for (String key : ALIASES.keySet()) {
      if (compact.contains(key.toLowerCase(Locale.ROOT))) {
        terms.add(key);
        aliases.addAll(ALIASES.get(key));
        if (isEmbeddedTerm(key))
          domainHints.add("STM32 " + key);
      }
    }

    List<String> expandedParts = new ArrayList<>();
    expandedParts.add(original);
    expandedParts.addAll(terms);
    expandedParts.addAll(aliases);
    expandedParts.addAll(domainHints);
    String expandedText = String.join(" ", expandedParts);

    ExpandedQuery expandedQuery = new ExpandedQuery(
        original,
        List.copyOf(terms),
        List.copyOf(aliases),
        List.copyOf(domainHints),
        expandedText);
    log.debug("RAG query understanding original={}, terms={}, aliases={}, expanded={}",
        expandedQuery.original(),
        expandedQuery.terms(),
        expandedQuery.aliases(),
        expandedQuery.expandedText());
    return expandedQuery;
  }

  private List<String> tokens(String text) {
    String normalized = normalize(text);
    if (normalized.isBlank())
      return List.of();

    Set<String> result = new LinkedHashSet<>();
    for (String token : normalized.split("\\s+")) {
      if (token.length() >= 2)
        result.add(token);
    }
    String compact = normalized.replace(" ", "");
    for (int i = 0; i < compact.length() - 1; i++) {
      String gram = compact.substring(i, Math.min(compact.length(), i + 2));
      if (!isStopWord(gram))
        result.add(gram);
    }
    return List.copyOf(result);
  }

  private String normalize(String text) {
    return safe(text).toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{Punct}，。！？；：、“”‘’（）【】《》]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private boolean isStopWord(String token) {
    return STOP_WORDS.contains(safe(token).toLowerCase(Locale.ROOT));
  }

  private boolean isEmbeddedTerm(String term) {
    return List.of("ADC", "GPIO", "PWM", "UART", "SPI", "I2C", "DMA", "DAC", "TIM").contains(term);
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static Map<String, List<String>> aliases() {
    Map<String, List<String>> aliases = new LinkedHashMap<>();
    aliases.put("ADC", List.of("模数转换器", "Analog Digital Converter", "A/D Converter", "模拟量数字量转换", "模拟信号", "数字信号"));
    aliases.put("DAC", List.of("数模转换器", "Digital Analog Converter", "D/A Converter"));
    aliases.put("GPIO", List.of("通用输入输出", "General Purpose Input Output"));
    aliases.put("PWM", List.of("脉宽调制", "Pulse Width Modulation"));
    aliases.put("UART", List.of("串口", "Universal Asynchronous Receiver Transmitter"));
    aliases.put("SPI", List.of("串行外设接口", "Serial Peripheral Interface"));
    aliases.put("I2C", List.of("IIC", "Inter Integrated Circuit", "两线制总线"));
    aliases.put("DMA", List.of("直接存储器访问", "Direct Memory Access"));
    aliases.put("TIM", List.of("定时器", "Timer"));
    aliases.put("STM32", List.of("单片机", "MCU", "嵌入式"));
    aliases.put("ESP32", List.of("单片机", "MCU", "嵌入式"));
    return aliases;
  }
}
