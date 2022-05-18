package de.samply.directory_sync;

import de.samply.directory_sync.directory.model.BbmriEricId;
import org.hl7.fhir.r4.model.Identifier;

public interface TestUtil {

  static Identifier createBbmriIdentifier(BbmriEricId value) {
    Identifier identifier = new Identifier();
    identifier.setSystem("http://www.bbmri-eric.eu/").setValue(value.toString());
    return identifier;
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  static BbmriEricId createBbmriEricId(String s) {
    return BbmriEricId.valueOf(s).get();
  }
}
