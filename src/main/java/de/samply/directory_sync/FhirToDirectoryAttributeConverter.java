package de.samply.directory_sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts FHIR attributes to Directory attributes.
 */
public class FhirToDirectoryAttributeConverter {
  private static final Logger logger = LoggerFactory.getLogger(FhirToDirectoryAttributeConverter.class);

  public static String convertSex(String sex) {
      // Signifiers for sex largely overlap between FHIR and Directory, but Directory likes
      // upper case
      return sex.toUpperCase();
  }

  public static Integer convertAge(Integer age) {
      // No conversion needed
      return age;
  }

  public static String convertMaterial(String material) {
      if (material == null)
          return null;

      String directoryMaterial = material
              // Basic conversion: make everything upper case, replace - with _
              .toUpperCase()
              .replaceAll("-", "_")
              // Some names are different between FHIR and Directory, so convert those.
              .replaceAll("_VITAL", "")
              .replaceAll("^TISSUE_FORMALIN$", "TISSUE_PARAFFIN_EMBEDDED")
              .replaceAll("^TISSUE$", "TISSUE_FROZEN")
              .replaceAll("^CF_DNA$", "CDNA")
              .replaceAll("^BLOOD_SERUM$", "SERUM")
              .replaceAll("^STOOL_FAECES$", "FECES")
              .replaceAll("^BLOOD_PLASMA$", "SERUM")
              // Some names are present in FHIR but not in Directory. Use "OTHER" as a placeholder.
              .replaceAll("^.*_OTHER$", "OTHER")
              .replaceAll("^DERIVATIVE$", "OTHER")
              .replaceAll("^CSF_LIQUOR$", "OTHER")
              .replaceAll("^LIQUID$", "OTHER")
              .replaceAll("^ASCITES$", "OTHER")
              .replaceAll("^TISSUE_PAXGENE_OR_ELSE$", "OTHER")
              ;
 
      return directoryMaterial;
  }

  public static String convertStorageTemperature(String storageTemperature) {
    if (storageTemperature == null)
        return null;

    // The Directory understands most of the FHIR temperature codes, but it doesn't
    // know about gaseous nitrogen.
    String directoryStorageTemperature = storageTemperature
        .replaceAll("temperatureGN", "temperatureOther");

    return directoryStorageTemperature;
  }

  public static String convertDiagnosis(String diagnosis) {
    if (diagnosis == null)
        return null;

    String miriamDiagnosis = null;
    if (diagnosis.startsWith("urn:miriam:icd:"))
        miriamDiagnosis = diagnosis;
    else if (diagnosis.length() == 3 || diagnosis.length() == 5)  // E.g. C75 or E23.1
        miriamDiagnosis = "urn:miriam:icd:" + diagnosis;
    else
        logger.warn("Entities.setDiagnosisAvailable: invalid diagnosis code " + diagnosis);
    
    return miriamDiagnosis;
  }
}
