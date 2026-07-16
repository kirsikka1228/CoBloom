package com.cobloom.service.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueryUnderstandingServiceTest {
  private final QueryUnderstandingService service = new QueryUnderstandingService();

  @Test
  void nullQuestionProducesSafeEmptyQuery() {
    ExpandedQuery query = service.expand(null);

    assertEquals("", query.original());
    assertTrue(query.allTerms().isEmpty());
  }

  @Test
  void knownAcronymAddsAliasesAndDomainHint() {
    ExpandedQuery query = service.expand("ADC 如何工作");

    assertTrue(query.terms().contains("ADC"));
    assertTrue(query.aliases().contains("模数转换器"));
    assertTrue(query.domainHints().contains("STM32 ADC"));
    assertTrue(query.expandedText().contains("Analog Digital Converter"));
  }

  @Test
  void stopWordOnlyQuestionDoesNotBecomeCoreTerm() {
    ExpandedQuery query = service.expand("如何");

    assertTrue(query.terms().isEmpty());
  }

  @Test
  void allTermsAreDistinctAcrossExpansionSources() {
    ExpandedQuery query = service.expand("STM32 ADC ADC");

    assertEquals(query.allTerms().size(), query.allTerms().stream().distinct().count());
  }
}
