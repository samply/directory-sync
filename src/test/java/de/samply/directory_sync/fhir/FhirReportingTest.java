package de.samply.directory_sync.fhir;

import static de.samply.directory_sync.TestUtil.createBbmriEricId;
import static de.samply.directory_sync.TestUtil.createBbmriIdentifier;
import static de.samply.directory_sync.Util.mapOf;
import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import java.util.ArrayList;
import java.util.Map;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FhirReportingTest {

  private static final BbmriEricId BBMRI_ERIC_ID = createBbmriEricId("bbmri-eric:ID:AT_MUG");
  private static final String LIBRARY_URI = "https://fhir.bbmri.de/Library/collection-size";
  private static final String MEASURE_URI = "https://fhir.bbmri.de/Measure/collection-size";
  private static final String COLLECTION_ID = "collection-id-102302";
  private static final String ERROR_MSG = "error-msg-140825";

  @Mock
  private FhirApi fhirApi;

  private FhirReporting reporting;

  @BeforeEach
  void setUp() {
    reporting = new FhirReporting(FhirContext.forR4(), fhirApi);
  }

  @Test
  void testInitLibrary_createNonExisting() {
    when(fhirApi.resourceExists(Library.class, LIBRARY_URI)).thenReturn(right(false));
    when(fhirApi.createResource(argThat(libraryMatcher()))).thenReturn(right(null));

    Either<String, Void> result = reporting.initLibrary();

    assertEquals(right(null), result);
  }

  @Test
  void testInitLibrary_skipExisting() {
    when(fhirApi.resourceExists(Library.class, LIBRARY_URI)).thenReturn(right(true));

    Either<String, Void> result = reporting.initLibrary();

    assertEquals(right(null), result);
  }

  @Test
  void testInitLibrary_existenceCheckError() {
    when(fhirApi.resourceExists(Library.class, LIBRARY_URI)).thenReturn(left(ERROR_MSG));

    Either<String, Void> result = reporting.initLibrary();

    assertEquals(left(ERROR_MSG), result);
  }

  @Test
  void testInitLibrary_createError() {
    when(fhirApi.resourceExists(Library.class, LIBRARY_URI)).thenReturn(right(false));
    when(fhirApi.createResource(argThat(libraryMatcher()))).thenReturn(left(ERROR_MSG));

    Either<String, Void> result = reporting.initLibrary();

    assertEquals(left(ERROR_MSG), result);
  }

  @Test
  void testInitMeasure_createNonExisting() {
    when(fhirApi.resourceExists(Measure.class, MEASURE_URI)).thenReturn(right(false));
    when(fhirApi.createResource(argThat(measureMatcher()))).thenReturn(
        right(null));

    Either<String, Void> result = reporting.initMeasure();

    assertEquals(right(null), result);
  }

  @Test
  void testInitMeasure_skipExisting() {
    when(fhirApi.resourceExists(Measure.class, MEASURE_URI)).thenReturn(right(true));

    Either<String, Void> result = reporting.initMeasure();

    assertEquals(right(null), result);
  }

  @Test
  void testInitMeasure_existenceCheckError() {
    when(fhirApi.resourceExists(Measure.class, MEASURE_URI)).thenReturn(left(ERROR_MSG));

    Either<String, Void> result = reporting.initMeasure();

    assertEquals(left(ERROR_MSG), result);
  }

  @Test
  void testInitMeasure_createError() {
    when(fhirApi.resourceExists(Measure.class, MEASURE_URI)).thenReturn(right(false));
    when(fhirApi.createResource(argThat(measureMatcher()))).thenReturn(
        left(ERROR_MSG));

    Either<String, Void> result = reporting.initMeasure();

    assertEquals(left(ERROR_MSG), result);
  }

  @Test
  void testFetchCollectionSizes_existingCollection() {
    MeasureReport report = new MeasureReport();
    report.getGroupFirstRep().getStratifierFirstRep()
        .addStratum(createStratum("Organization/" + COLLECTION_ID, 100));
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(right(report));
    when(fhirApi.fetchCollections(singleton(COLLECTION_ID)))
        .thenReturn(right(singletonList(createCollection(COLLECTION_ID, BBMRI_ERIC_ID))));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf(BBMRI_ERIC_ID, 100)), result);
  }

  @Test
  void testFetchCollectionSizes_nonExistingCollection() {
    MeasureReport report = new MeasureReport();
    report.getGroupFirstRep().getStratifierFirstRep()
        .addStratum(createStratum("Organization/" + COLLECTION_ID, 100));
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(right(report));
    when(fhirApi.fetchCollections(singleton(COLLECTION_ID))).thenReturn(right(emptyList()));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf()), result);
  }

  @Test
  void testFetchCollectionSizes_unassignedSpecimenAndNoCollection() {
    MeasureReport report = new MeasureReport();
    report.getGroupFirstRep().getStratifierFirstRep().addStratum(createStratum("null", 200));
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(right(report));
    when(fhirApi.listAllCollections()).thenReturn(right(emptyList()));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf()), result);
  }

  @Test
  void testFetchCollectionSizes_unassignedSpecimenAndOneCollection() {
    MeasureReport report = new MeasureReport();
    report.getGroupFirstRep().getStratifierFirstRep().addStratum(createStratum("null", 200));
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(right(report));
    when(fhirApi.listAllCollections())
        .thenReturn(right(singletonList(createCollection(COLLECTION_ID, BBMRI_ERIC_ID))));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf(BBMRI_ERIC_ID, 200)), result);
  }

  @Test
  void testFetchCollectionSizes_unassignedSpecimenAndTwoCollections() {
    MeasureReport report = new MeasureReport();
    report.getGroupFirstRep().getStratifierFirstRep().addStratum(createStratum("null", 200));
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(right(report));
    ArrayList<Organization> organizations = new ArrayList<>();
    organizations.add(new Organization());
    organizations.add(new Organization());
    when(fhirApi.listAllCollections()).thenReturn(right(organizations));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf()), result);
  }

  @Test
  void testFetchCollectionSizes_evaluateMeasureError() {
    OperationOutcome error = new OperationOutcome();
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(left(error));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(left(error), result);
  }

  @Test
  void testFetchCollectionSizes_fetchCollectionsError() {
    OperationOutcome error = new OperationOutcome();
    when(fhirApi.evaluateMeasure(MEASURE_URI)).thenReturn(right(new MeasureReport()));
    when(fhirApi.fetchCollections(emptySet())).thenReturn(left(error));

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(left(error), result);
  }

  private ArgumentMatcher<Library> libraryMatcher() {
    return argument -> argument != null && LIBRARY_URI.equals(argument.getUrl());
  }

  private ArgumentMatcher<Measure> measureMatcher() {
    return argument -> argument != null && MEASURE_URI.equals(argument.getUrl());
  }

  private static StratifierGroupComponent createStratum(String reference, int count) {
    StratifierGroupComponent stratum = new StratifierGroupComponent();
    stratum.setValue(new CodeableConcept().setText(reference));
    stratum.getPopulationFirstRep().setCount(count);
    return stratum;
  }

  @SuppressWarnings("SameParameterValue")
  private static Organization createCollection(String id, BbmriEricId bbmriEricId) {
    Organization collection = new Organization();
    collection.setId(id);
    collection.addIdentifier(createBbmriIdentifier(bbmriEricId));
    return collection;
  }
}
