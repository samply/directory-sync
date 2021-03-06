package de.samply.directory_sync.fhir;

import de.samply.directory_sync.SyncTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FhirReportingTest {

    @Mock
    FhirApi api;
    private FhirReporting reporting;

    @BeforeEach
    void setup() {
        this.reporting = new FhirReporting(api);
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
        orgA.addIdentifier(SyncTest.createBbmriIdentifier("bbmri:A"));
        when(api.evaluateMeasure("https://fhir.bbmri.de/Measure/size")).thenReturn(Either.right(report));
        when(api.fetchCollections(Collections.singleton("A"))).thenReturn(Either.right(Collections.singletonList(orgA)));

        Either<OperationOutcome, Map<String, Integer>> result = reporting.fetchCollectionSizes();

        assertTrue(result.isRight());
        assertEquals(100, result.get().get("bbmri:A"));
    }

    @Test
    void testFetchCollectionSizes_serverFailureMeasureReport() {
        OperationOutcome error = new OperationOutcome();
        when(api.evaluateMeasure("https://fhir.bbmri.de/Measure/size")).thenReturn(Either.left(error));

        Either<OperationOutcome, Map<String, Integer>> result = reporting.fetchCollectionSizes();

        assertTrue(result.isLeft());
        assertEquals(error, result.getLeft());
    }

    @Test
    void testFetchCollectionSizes_serverFailureFetchCollections() {
        MeasureReport report = new MeasureReport();
        OperationOutcome error = new OperationOutcome();
        when(api.evaluateMeasure("https://fhir.bbmri.de/Measure/size")).thenReturn(Either.right(report));
        when(api.fetchCollections(Collections.EMPTY_SET)).thenReturn(Either.left(error));

        Either<OperationOutcome, Map<String, Integer>> result = reporting.fetchCollectionSizes();

        assertTrue(result.isLeft());
        assertEquals(error, result.getLeft());
    }
}
