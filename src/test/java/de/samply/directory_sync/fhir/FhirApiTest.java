package de.samply.directory_sync.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.ICriterionInternal;
import com.google.common.collect.ImmutableSet;
import io.vavr.control.Either;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FhirApiTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IGenericClient fhirClient;

    private FhirApi fhirApi;

    static Bundle singletonBundle(Resource r) {
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(r);
        return bundle;
    }

    @BeforeEach
    void setup() {
        fhirApi = new FhirApi(fhirClient);
    }

    @Test
    void testUpdateResource() {
        IBaseResource resource = new Patient();
        OperationOutcome answer = new OperationOutcome();
        when(fhirClient.update().resource(resource).prefer(PreferReturnEnum.OPERATION_OUTCOME).execute().getOperationOutcome()).thenReturn(answer);

        OperationOutcome result = fhirApi.updateResource(resource);

        assertEquals(answer, result);
    }

    @Test
    void testUpdateResource_serverFailure() {
        IBaseResource resource = new Patient();
        when(fhirClient.update().resource(resource).prefer(PreferReturnEnum.OPERATION_OUTCOME).execute()).thenThrow(new RuntimeException("msg-161148"));

        OperationOutcome result = fhirApi.updateResource(resource);

        assertEquals(OperationOutcome.IssueSeverity.ERROR, result.getIssueFirstRep().getSeverity());
        assertEquals("msg-161148", result.getIssueFirstRep().getDiagnostics());
    }

    @Test
    void testFetchAllBiobanks() {
        Organization org = new Organization();
        org.getMeta().addProfile("https://fhir.bbmri.de/StructureDefinition/Biobank");
        when(fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute())
                .thenReturn(singletonBundle(org));

        Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

        assertTrue(res.isRight());
        assertEquals(Collections.singletonList(org), res.get());
    }

    @Test
    void testUpdateBiobanksIfIfNecessary_emptyBundle() {
        Bundle bundle = new Bundle();
        when(fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute()).thenReturn(bundle);

        Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

        assertTrue(res.isRight());
        assertTrue(res.get().isEmpty());
    }

    @Test
    void testUpdateBiobanksIfIfNecessary_noProfile() {
        Organization org = new Organization();
        when(fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute()).thenReturn(singletonBundle(org));

        Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

        assertTrue(res.isRight());
        assertTrue(res.get().isEmpty());
    }

    @Test
    void testGetMeasureReport() {
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
        inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));
        inParams.addParameter().setName("measure").setValue(new StringType("test-145732"));
        MeasureReport report = new MeasureReport();
        Parameters outParams = new Parameters();
        outParams.addParameter().setResource(report);
        when(fhirClient.operation().onType(Measure.class).named("$evaluate-measure")
                .withParameters(argThat(new ParametersArgumentMatcher(inParams))).useHttpGet().execute())
                .thenReturn(outParams);

        Either<OperationOutcome, MeasureReport> result = fhirApi.evaluateMeasure("test-145732");

        assertTrue(result.isRight());
        assertEquals(report, result.get());
    }

    @Test
    void testGetMeasureReport_serverFailure() {
        when(fhirClient.operation().onType(Measure.class).named("$evaluate-measure")
                .withParameters(any()).useHttpGet().execute())
                .thenThrow(new RuntimeException("msg-171726"));

        Either<OperationOutcome, MeasureReport> result = fhirApi.evaluateMeasure("test-145732");

        assertTrue(result.isLeft());
        assertEquals(OperationOutcome.IssueSeverity.ERROR, result.getLeft().getIssueFirstRep().getSeverity());
        assertEquals("msg-171726", result.getLeft().getIssueFirstRep().getDiagnostics());
    }

    @Test
    void testFetchCollections() {
        Organization a = new Organization();
        a.setId("orgId-152540");
        when(fhirClient.search().forResource(Organization.class)
                .where(argThat(new CriterionArgumentMatcher("_id", "orgId-152540"))).execute())
                .thenReturn(singletonBundle(a));

        Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(Collections.singleton("orgId" +
                "-152540"));

        assertTrue(result.isRight());
        assertEquals("orgId-152540", result.get().get(0).getId());
    }

    @Test
    void testFetchCollections_searchForMultiple() {
        Organization a = new Organization();
        a.setId("orgId-152540");
        when(fhirClient.search().forResource(Organization.class)
                .where(argThat(new CriterionArgumentMatcher("_id", "orgId-152540,org"))).execute())
                .thenReturn(singletonBundle(a));

        Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(ImmutableSet.of("orgId-152540"
                , "org"));

        assertTrue(result.isRight());
        assertEquals("orgId-152540", result.get().get(0).getId());
    }

    @Test
    void testFetchCollections_searchForNone() {
        Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(Collections.EMPTY_SET);

        assertTrue(result.isRight());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void testFetchCollections_serverFailure() {
        when(fhirClient.search().forResource(Organization.class)
                .where(any(ICriterion.class)).execute())
                .thenThrow(new RuntimeException("msg-172631"));

        Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(ImmutableSet.of("orgId-152540"
                , "org"));

        assertTrue(result.isLeft());
        assertEquals(OperationOutcome.IssueSeverity.ERROR, result.getLeft().getIssueFirstRep().getSeverity());
        assertEquals("msg-172631", result.getLeft().getIssueFirstRep().getDiagnostics());
    }

    private static class ParametersArgumentMatcher implements ArgumentMatcher<IBaseParameters> {
        private final Parameters expected;

        public ParametersArgumentMatcher(Parameters expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(IBaseParameters argument) {
            return expected.equalsDeep((Base) argument);
        }
    }

    private static class CriterionArgumentMatcher implements ArgumentMatcher<ICriterion<?>> {

        private final String name;
        private final String value;

        public CriterionArgumentMatcher(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean matches(ICriterion<?> argument) {
            ICriterionInternal crit = (ICriterionInternal) argument;
            return name.equals(crit.getParameterName()) && value.equals(crit.getParameterValue(FhirContext.forR4()));
        }
    }
}
