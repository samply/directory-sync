package de.samply.directory_sync.fhir;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.model.primitive.DateTimeDt;
import de.samply.directory_sync.StarModelData;
import de.samply.directory_sync.Util;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;

/**
 * Pull data about Patients, Specimens and Dieseases from the FHIR store and
 * use the information they contain to fill a StarModelInputData object.
 */
public class PopulateStarModelInputData {
  private static final Logger logger = LoggerFactory.getLogger(PopulateStarModelInputData.class);
  private FhirApi fhirApi;

  public PopulateStarModelInputData(FhirApi fhirApi) {
    this.fhirApi = fhirApi;
  }

  public StarModelData populate(BbmriEricId defaultBbmriEricCollectionId) {
    // Group specimens according to collection.
    Either<OperationOutcome, Map<String, List<Specimen>>> specimensByCollectionOutcome = fhirApi.fetchSpecimensByCollection(defaultBbmriEricCollectionId);
    if (specimensByCollectionOutcome.isLeft()) {
      logger.error("Problem finding specimens");
      return null;
    }
    Map<String, List<Specimen>> specimensByCollection = specimensByCollectionOutcome.get();

    StarModelData starModelInputData = new StarModelData();
    for (String collectionId: specimensByCollection.keySet())
      populateCollection(starModelInputData, collectionId, specimensByCollection.get(collectionId));

    return starModelInputData;
  }

  private void populateCollection(StarModelData starModelInputData, String collectionId, List<Specimen> specimens) {
    for (Specimen specimen: specimens)
      populateSpecimen(starModelInputData, collectionId, specimen);
  }

  private void populateSpecimen(StarModelData starModelInputData, String collectionId, Specimen specimen) {
    // Get the Patient who donated the sample
    Patient patient = fhirApi.extractPatientFromSpecimen(specimen);

    String material = extractMaterialFromSpecimen(specimen);
    String patientId = patient.getIdElement().getIdPart();
    String sex = patient.getGender().getDisplay();
    String age = determinePatientAgeAtCollection(patient, specimen);

    // Create a new Row object to hold data extracted from patient and specimen
    StarModelData.InputRow row = starModelInputData.newInputRow(collectionId, material, patientId, sex, age);

    List<String> diagnoses = extractDiagnosesFromPatientAndSpecimen(patient, specimen);

    for (String diagnosis: diagnoses)
      starModelInputData.addInputRow(collectionId, starModelInputData.newInputRow(row, diagnosis));
  }

  private String determinePatientAgeAtCollection(Patient patient, Specimen specimen) {
    String age = null;

    try {
      // Get the patient's birth date as a LocalDate object
      LocalDate birthDate = patient.getBirthDate().toInstant()
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate();

      LocalDate collectionDate = extractCollectionLocalDateFromSpecimen(specimen);

      // Calculate the patient's age in years using the Period class
      int ageInYears = Period.between(birthDate, collectionDate).getYears();

      if (ageInYears < 0) {
        logger.warn("determinePatientAgeAtCollection: age at collection is negative, substituting null");
        age = null;
      } else
        age = Integer.toString(ageInYears);
    } catch (Exception e) {
      logger.warn(Util.traceFromException(e));
    }

    return age;
  }
  
  private LocalDate extractCollectionLocalDateFromSpecimen(Specimen specimen) {
    // Check if the specimen is null or has no collection date
    if (specimen == null || !specimen.hasCollection()) {
      return null; // Return null if so
    }

    Specimen.SpecimenCollectionComponent collection = specimen.getCollection();
    if (collection.hasCollectedDateTimeType()) {
      DateTimeType collected = collection.getCollectedDateTimeType();
      Date date = collected.getValue(); // Get the java.util.Date object
      LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

      return localDate;
    } else 
        return null;
  }

  private List<String> extractDiagnosesFromPatientAndSpecimen(Patient patient, Specimen specimen) {
    // Find any diagnoses associated with this patient
    List<String> patientConditionCodes = fhirApi.extractConditionCodesFromPatient(patient);

    // Find any diagnoses associated with this specimen
    List<String> diagnosesFromSpecimen = fhirApi.extractDiagnosesFromSpecimen(specimen);

    // Combine diagnosis lists
    return Stream.concat(patientConditionCodes.stream(), diagnosesFromSpecimen.stream())
      .distinct()
      .collect(Collectors.toList());
  }

    /**
     * Extracts the material from a Specimen object.
     * <p>
     * This method returns the text or the code of the type element of the Specimen object,
     * or null if the type element is missing or empty.
     * </p>
     * @param specimen the Specimen object to extract the material from
     * @return the material as a String, or null if not available
     */
    private String extractMaterialFromSpecimen(Specimen specimen) {
        String material = null;

        CodeableConcept type = specimen.getType();
        if (type.hasText())
            material = type.getText();
        else {
            List<Coding> coding = type.getCoding();
            if (coding.size() > 0)
                material = coding.get(0).getCode();
        }

        return material;
    }
}
