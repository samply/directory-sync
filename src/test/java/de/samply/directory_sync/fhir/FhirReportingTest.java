package de.samply.directory_sync.fhir;

import de.samply.directory_sync.SyncTest;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FhirReportingTest {

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static final BbmriEricId BBMRI_ERIC_ID = BbmriEricId.valueOf("bbmri-eric:ID:AT_MUG").get();

    @Mock
    FhirApi api;
    private FhirReporting reporting;

    @BeforeEach
    void setup() {
        this.reporting = new FhirReporting(fhirContext, api);
    }

    @Test
    void testFetchCollectionSizes() {
        MeasureReport report = new MeasureReport();
        MeasureReport.StratifierGroupComponent stratumA = new MeasureReport.StratifierGroupComponent();
        stratumA.setValue(new CodeableConcept().setText("Organization/A"));
        stratumA.getPopulationFirstRep().setCount(100);
        report.getGroupFirstRep().getStratifierFirstRep().addStratum(stratumA);
        Organization orgA = new Organization();
        orgA.setId("A");
        orgA.addIdentifier(SyncTest.createBbmriIdentifier(BBMRI_ERIC_ID));
        when(api.evaluateMeasure("https://fhir.bbmri.de/Measure/collection-size")).thenReturn(Either.right(report));
        when(api.fetchCollections(Collections.singleton("A"))).thenReturn(Either.right(Collections.singletonList(orgA)));

        Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

        assertTrue(result.isRight());
        assertEquals(100, result.get().get(BBMRI_ERIC_ID));
    }

    @Test
    void testFetchCollectionSizes_serverFailureMeasureReport() {
        OperationOutcome error = new OperationOutcome();
        when(api.evaluateMeasure("https://fhir.bbmri.de/Measure/collection-size")).thenReturn(Either.left(error));

        Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

        assertTrue(result.isLeft());
        assertEquals(error, result.getLeft());
    }

    @Test
    void testFetchCollectionSizes_serverFailureFetchCollections() {
        MeasureReport report = new MeasureReport();
        OperationOutcome error = new OperationOutcome();
        when(api.evaluateMeasure("https://fhir.bbmri.de/Measure/collection-size")).thenReturn(Either.right(report));
        when(api.fetchCollections(emptySet())).thenReturn(Either.left(error));

        Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

        assertTrue(result.isLeft());
        assertEquals(error, result.getLeft());
    }
}
