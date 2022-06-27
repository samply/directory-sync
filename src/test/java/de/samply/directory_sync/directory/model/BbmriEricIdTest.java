package de.samply.directory_sync.directory.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BbmriEricIdTest {

  @Test
  void valueOf_WithoutSuffix() {
    Optional<BbmriEricId> id = BbmriEricId.valueOf("bbmri-eric:ID:AT_MUG");

    assertTrue(id.isPresent());
    assertEquals("AT", id.get().getCountryCode());
    assertEquals("bbmri-eric:ID:AT_MUG", id.get().toString());
  }

  @Test
  void valueOf_WithSuffix() {
    Optional<BbmriEricId> id = BbmriEricId.valueOf("bbmri-eric:ID:AT_MUG:collection:ClinibilStudy");

    assertTrue(id.isPresent());
    assertEquals("AT", id.get().getCountryCode());
    assertEquals("bbmri-eric:ID:AT_MUG:collection:ClinibilStudy", id.get().toString());
  }

  @Test
  void valueOf_Invalid() {
    assertFalse(BbmriEricId.valueOf("foo:ID:DE_MUG").isPresent());
    assertFalse(BbmriEricId.valueOf("bbmri-eric:ID:GERMUG").isPresent());
    assertFalse(BbmriEricId.valueOf("bbmri-eric:ID:GER_MUG").isPresent());
    assertFalse(BbmriEricId.valueOf("bbmri-eric:ID:GER_").isPresent());
  }
}
