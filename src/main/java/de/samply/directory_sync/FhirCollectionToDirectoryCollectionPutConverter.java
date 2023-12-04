package de.samply.directory_sync;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.samply.directory_sync.directory.model.DirectoryCollectionPut;
import de.samply.directory_sync.fhir.model.FhirCollection;

public class FhirCollectionToDirectoryCollectionPutConverter {
  private static final Logger logger = LoggerFactory.getLogger(FhirCollectionToDirectoryCollectionPutConverter.class);

  public static DirectoryCollectionPut convert(List<FhirCollection> fhirCollections) {
      DirectoryCollectionPut directoryCollectionPut = new DirectoryCollectionPut();

      for (FhirCollection fhirCollection: fhirCollections)
        if (convert(directoryCollectionPut, fhirCollection) == null) {
            directoryCollectionPut = null;
            break;
        }

      return directoryCollectionPut;
  }

  private static DirectoryCollectionPut convert(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
    try {
      convertSize(directoryCollectionPut, fhirCollection);
      convertNumberOfDonors(directoryCollectionPut, fhirCollection);
      convertSex(directoryCollectionPut, fhirCollection);
      convertAgeLow(directoryCollectionPut, fhirCollection);
      convertAgeHigh(directoryCollectionPut, fhirCollection);
      convertMaterials(directoryCollectionPut, fhirCollection);
      convertStorageTemperatures(directoryCollectionPut, fhirCollection);
      convertDiagnosisAvailable(directoryCollectionPut, fhirCollection);
    } catch(Exception e) {
        logger.error("Problem converting FHIR attributes to Directory attributes. " + Util.traceFromException(e));
        return null;
    }

    return directoryCollectionPut;
  }

  private static void convertSize(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
      String id = fhirCollection.getId();
      Integer size = fhirCollection.getSize();
      directoryCollectionPut.setSize(id, size);
      // Order of magnitude is mandatory in the Directory and can be derived from size
      directoryCollectionPut.setOrderOfMagnitude(id, (int) Math.floor(Math.log10(size)));
  }

  public static void convertNumberOfDonors(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
      String id = fhirCollection.getId();
      Integer size = fhirCollection.getNumberOfDonors();
      directoryCollectionPut.setNumberOfDonors(id, size);
      // Order of magnitude is mandatory in the Directory and can be derived from size
      directoryCollectionPut.setOrderOfMagnitudeDonors(id, (int) Math.floor(Math.log10(size)));
  }

  public static void convertSex(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
      String id = fhirCollection.getId();
      List<String> sex = fhirCollection.getSex();

      // Signifiers for sex largely overlap between FHIR and Directory, but Directory likes
      // upper case
      List<String> ucSex = sex.stream()
              .map(s -> s.toUpperCase())
              .collect(Collectors.toList());

      directoryCollectionPut.setSex(id, ucSex);
  }

  public static void convertAgeLow(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
      String id = fhirCollection.getId();
      Integer ageLow = fhirCollection.getAgeLow();
      // No conversion needed
      directoryCollectionPut.setAgeLow(id, ageLow);
  }

  public static void convertAgeHigh(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
      String id = fhirCollection.getId();
      Integer ageHigh = fhirCollection.getAgeHigh();
      // No conversion needed
      directoryCollectionPut.setAgeHigh(id, ageHigh);
  }

  public static void convertMaterials(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
      String id = fhirCollection.getId();
      List<String> materials = fhirCollection.getMaterials();

      if (materials == null)
          materials = new ArrayList<String>();

      List<String> directoryMaterials = materials.stream()
              // Basic conversion: make everything upper case, replace - with _
              .map(s -> s.toUpperCase())
              .map(s -> s.replaceAll("-", "_"))
              // Some names are different between FHIR and Directory, so convert those.
              .map(s -> s.replaceAll("_VITAL", ""))
              .map(s -> s.replaceAll("^TISSUE_FORMALIN$", "TISSUE_PARAFFIN_EMBEDDED"))
              .map(s -> s.replaceAll("^TISSUE$", "TISSUE_FROZEN"))
              .map(s -> s.replaceAll("^CF_DNA$", "CDNA"))
              .map(s -> s.replaceAll("^BLOOD_SERUM$", "SERUM"))
              .map(s -> s.replaceAll("^STOOL_FAECES$", "FECES"))
              .map(s -> s.replaceAll("^BLOOD_PLASMA$", "SERUM"))
              // Some names are present in FHIR but not in Directory. Use "OTHER" as a placeholder.
              .map(s -> s.replaceAll("^.*_OTHER$", "OTHER"))
              .map(s -> s.replaceAll("^DERIVATIVE$", "OTHER"))
              .map(s -> s.replaceAll("^CSF_LIQUOR$", "OTHER"))
              .map(s -> s.replaceAll("^LIQUID$", "OTHER"))
              .map(s -> s.replaceAll("^ASCITES$", "OTHER"))
              .map(s -> s.replaceAll("^TISSUE_PAXGENE_OR_ELSE$", "OTHER"))
              .distinct()  // Remove duplicate elements
              .collect(Collectors.toList());
 
      directoryCollectionPut.setMaterials(id, directoryMaterials);
  }

  public static void convertStorageTemperatures(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
    String id = fhirCollection.getId();
    List<String> storageTemperatures = fhirCollection.getStorageTemperatures();

    if (storageTemperatures == null)
        storageTemperatures = new ArrayList<String>();

    // The Directory understands most of the FHIR temperature codes, but it doesn't
    // know about gaseous nitrogen.
    List<String> directoryStorageTemperatures = storageTemperatures.stream()
        .map(s -> s.replaceAll("temperatureGN", "temperatureOther"))
        .distinct()  // Remove duplicate elements
        .collect(Collectors.toList());

    directoryCollectionPut.setStorageTemperatures(id, directoryStorageTemperatures);
  }

  public static void convertDiagnosisAvailable(DirectoryCollectionPut directoryCollectionPut, FhirCollection fhirCollection) {
       String id = fhirCollection.getId();
    //   List<String> diagnoses = fhirCollection.getDiagnosisAvailable();

    // if (diagnoses == null)
    //     diagnoses = new ArrayList<String>();

    // List<String> miriamDiagnoses = diagnoses.stream()
    //         .map(icd -> {
    //             if (icd.startsWith("urn:miriam:icd:")) { return icd; }
    //             else if (icd.length() == 3 || icd.length() == 5) {  // E.g. C75 or E23.1
    //                 return "urn:miriam:icd:" + icd;
    //             } else {
    //                 logger.warn("Entities.setDiagnosisAvailable: invalid diagnosis code " + icd); return null;
    //             } })
    //         .filter(icd -> icd != null)
    //         .distinct()  // Remove duplicate diagnoses
    //         .collect(Collectors.toList());
    //
    // directoryCollectionPut.setDiagnosisAvailable(id, miriamDiagnoses);




    // The Directory is very picky about which ICD10 codes it will accept, and some
    // of the codes that are in our test data are not known to the Directory and
    // give rise to errors, which lead to the entire PUT to the Directory being
    // rejected. So, for the time being, I am turning off the diagnosis conversion.
    directoryCollectionPut.setDiagnosisAvailable(id, new ArrayList<String>());
  }
}
