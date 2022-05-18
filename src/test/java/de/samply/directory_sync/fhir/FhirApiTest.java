package de.samply.directory_sync.fhir;

import static ca.uhn.fhir.rest.api.PreferReturnEnum.OPERATION_OUTCOME;
import static ca.uhn.fhir.rest.api.SummaryEnum.COUNT;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.ICriterionInternal;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUpdateExecutable;
import com.google.common.collect.ImmutableSet;
import io.vavr.control.Either;
import java.util.Collections;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FhirApiTest {

  private static final String URI = "uri-160626";
  private static final String ERROR_MESSAGE = "msg-172631";
  private static final String BIOBANK_PROFILE_URI = "https://fhir.bbmri.de/StructureDefinition/Biobank";
  private static final String COLLECTION_PROFILE_URI = "https://fhir.bbmri.de/StructureDefinition/Collection";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private IGenericClient fhirClient;

  @InjectMocks
  private FhirApi fhirApi;

  private static Bundle singletonBundle(Resource r) {
    Bundle bundle = new Bundle();
    bundle.addEntry().setResource(r);
    return bundle;
  }

  @Test
  void testUpdateResource() {
    Patient resource = new Patient();
    OperationOutcome answer = new OperationOutcome();
    when(resourceUpdate(resource).execute().getOperationOutcome()).thenReturn(answer);

    OperationOutcome result = fhirApi.updateResource(resource);

    assertEquals(answer, result);
  }

  @Test
  void testUpdateResource_serverFailure() {
    Patient resource = new Patient();
    when(resourceUpdate(resource).execute()).thenThrow(new RuntimeException(ERROR_MESSAGE));

    OperationOutcome result = fhirApi.updateResource(resource);

    assertEquals(ERROR, result.getIssueFirstRep().getSeverity());
    assertEquals(ERROR_MESSAGE, result.getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testCreateResource() {
    Patient resource = new Patient();
    when(resourceCreate(resource).execute()).thenReturn(new MethodOutcome(null, true));

    Either<String, Void> result = fhirApi.createResource(resource);

    assertTrue(result.isRight(), "the result is right");
  }

  @Test
  void testCreateResource_notCreated() {
    Patient resource = new Patient();
    when(resourceCreate(resource).execute()).thenReturn(new MethodOutcome(null, false));

    Either<String, Void> result = fhirApi.createResource(resource);

    assertTrue(result.isLeft(), "the result is left");
    assertEquals("error while creating a resource", result.getLeft());
  }

  @Test
  void testCreateResource_serverFailure() {
    Patient resource = new Patient();
    when(resourceCreate(resource).execute()).thenThrow(new RuntimeException(ERROR_MESSAGE));

    Either<String, Void> result = fhirApi.createResource(resource);

    assertTrue(result.isLeft(), "the result is left");
    assertEquals(ERROR_MESSAGE, result.getLeft());
  }

  @Test
  void testFetchAllBiobanks() {
    Organization org = new Organization();
    org.getMeta().addProfile(BIOBANK_PROFILE_URI);
    when(organizationQuery(BIOBANK_PROFILE_URI).execute()).thenReturn(singletonBundle(org));

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

    assertTrue(res.isRight());
    assertEquals(Collections.singletonList(org), res.get());
  }

  @Test
  void testUpdateBiobanksIfIfNecessary_emptyBundle() {
    when(organizationQuery(BIOBANK_PROFILE_URI).execute()).thenReturn(new Bundle());

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

    assertTrue(res.isRight());
    assertTrue(res.get().isEmpty());
  }

  @Test
  void testUpdateBiobanksIfIfNecessary_noProfile() {
    Organization org = new Organization();
    when(organizationQuery(BIOBANK_PROFILE_URI).execute()).thenReturn(singletonBundle(org));

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

    assertTrue(res.isRight());
    assertTrue(res.get().isEmpty());
  }

  @Test
  void testUpdateBiobanksIfIfNecessary_serverFailure() {
    when(organizationQuery(BIOBANK_PROFILE_URI).execute())
        .thenThrow(new RuntimeException(ERROR_MESSAGE));

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllBiobanks();

    assertTrue(res.isLeft());
    assertEquals(ERROR, res.getLeft().getIssueFirstRep().getSeverity());
    assertEquals(ERROR_MESSAGE, res.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testFetchAllCollections() {
    Organization org = new Organization();
    org.getMeta().addProfile(COLLECTION_PROFILE_URI);
    when(organizationQuery(COLLECTION_PROFILE_URI).execute()).thenReturn(singletonBundle(org));

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllCollections();

    assertTrue(res.isRight());
    assertEquals(Collections.singletonList(org), res.get());
  }

  @Test
  void testUpdateCollectionsIfIfNecessary_emptyBundle() {
    when(organizationQuery(COLLECTION_PROFILE_URI).execute()).thenReturn(new Bundle());

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllCollections();

    assertTrue(res.isRight());
    assertTrue(res.get().isEmpty());
  }

  @Test
  void testUpdateCollectionsIfIfNecessary_noProfile() {
    Organization org = new Organization();
    when(organizationQuery(COLLECTION_PROFILE_URI).execute()).thenReturn(singletonBundle(org));

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllCollections();

    assertTrue(res.isRight());
    assertTrue(res.get().isEmpty());
  }

  @Test
  void testUpdateCollectionsIfIfNecessary_serverFailure() {
    when(organizationQuery(COLLECTION_PROFILE_URI).execute())
        .thenThrow(new RuntimeException(ERROR_MESSAGE));

    Either<OperationOutcome, List<Organization>> res = fhirApi.listAllCollections();

    assertTrue(res.isLeft());
    assertEquals(ERROR, res.getLeft().getIssueFirstRep().getSeverity());
    assertEquals(ERROR_MESSAGE, res.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void resourceExists_Yes() {
    Bundle bundle = new Bundle();
    bundle.setTotal(1);
    IQuery<IBaseBundle> query = mock(IQuery.class);
    when(fhirClient.search().forResource(Measure.class).where(any(ICriterion.class))).thenReturn(
        query);
    IQuery<IBaseBundle> query1 = mock(IQuery.class);
    when(query.summaryMode(COUNT)).thenReturn(query1);
    IQuery<Bundle> query2 = mock(IQuery.class);
    when(query1.returnBundle(Bundle.class)).thenReturn(query2);
    when(query2.execute()).thenReturn(bundle);

    Either<String, Boolean> result = fhirApi.resourceExists(Measure.class, URI);

    assertEquals(Either.right(true), result);
  }

  @Test
  void resourceExists_No() {
    Bundle bundle = new Bundle();
    bundle.setTotal(0);
    IQuery<IBaseBundle> query = mock(IQuery.class);
    when(fhirClient.search().forResource(Measure.class).where(any(ICriterion.class))).thenReturn(
        query);
    IQuery<IBaseBundle> query1 = mock(IQuery.class);
    when(query.summaryMode(COUNT)).thenReturn(query1);
    IQuery<Bundle> query2 = mock(IQuery.class);
    when(query1.returnBundle(Bundle.class)).thenReturn(query2);
    when(query2.execute()).thenReturn(bundle);

    Either<String, Boolean> result = fhirApi.resourceExists(Measure.class, URI);

    assertEquals(Either.right(false), result);
  }

  @Test
  void resourceExists_serverError() {
    IQuery<IBaseBundle> query = mock(IQuery.class);
    when(fhirClient.search().forResource(Measure.class).where(any(ICriterion.class))).thenReturn(
        query);
    IQuery<IBaseBundle> query1 = mock(IQuery.class);
    when(query.summaryMode(COUNT)).thenReturn(query1);
    IQuery<Bundle> query2 = mock(IQuery.class);
    when(query1.returnBundle(Bundle.class)).thenReturn(query2);
    when(query2.execute()).thenThrow(new RuntimeException(ERROR_MESSAGE));

    Either<String, Boolean> result = fhirApi.resourceExists(Measure.class, URI);

    assertEquals(Either.left(ERROR_MESSAGE), result);
  }

  @Test
  void testEvaluateMeasure() {
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

    assertTrue(result.isRight(), "the result is right");
    assertEquals(report, result.get());
  }

  @Test
  void testEvaluateMeasure_serverFailure() {
    when(fhirClient.operation().onType(Measure.class).named("$evaluate-measure")
        .withParameters(any()).useHttpGet().execute())
        .thenThrow(new RuntimeException("msg-171726"));

    Either<OperationOutcome, MeasureReport> result = fhirApi.evaluateMeasure("test-145732");

    assertTrue(result.isLeft(), "the result is left");
    assertEquals(ERROR, result.getLeft().getIssueFirstRep().getSeverity());
    assertEquals("msg-171726", result.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testFetchCollections() {
    Organization a = new Organization();
    a.setId("orgId-152540");
    when(fhirClient.search().forResource(Organization.class)
        .where(argThat(new CriterionArgumentMatcher("_id", "orgId-152540"))).execute())
        .thenReturn(singletonBundle(a));

    Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(
        Collections.singleton("orgId" +
            "-152540"));

    assertTrue(result.isRight(), "the result is right");
    assertEquals("orgId-152540", result.get().get(0).getId());
  }

  @Test
  void testFetchCollections_searchForMultiple() {
    Organization a = new Organization();
    a.setId("orgId-152540");
    when(fhirClient.search().forResource(Organization.class)
        .where(argThat(new CriterionArgumentMatcher("_id", "orgId-152540,org"))).execute())
        .thenReturn(singletonBundle(a));

    Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(
        ImmutableSet.of("orgId-152540"
            , "org"));

    assertTrue(result.isRight(), "the result is right");
    assertEquals("orgId-152540", result.get().get(0).getId());
  }

  @Test
  void testFetchCollections_searchForNone() {
    Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(
        Collections.EMPTY_SET);

    assertTrue(result.isRight(), "the result is right");
    assertTrue(result.get().isEmpty());
  }

  @Test
  void testFetchCollections_serverFailure() {
    when(fhirClient.search().forResource(Organization.class)
        .where(any(ICriterion.class)).execute())
        .thenThrow(new RuntimeException(ERROR_MESSAGE));

    Either<OperationOutcome, List<Organization>> result = fhirApi.fetchCollections(
        ImmutableSet.of("orgId-152540"
            , "org"));

    assertTrue(result.isLeft(), "the result is left");
    assertEquals(ERROR, result.getLeft().getIssueFirstRep().getSeverity());
    assertEquals(ERROR_MESSAGE, result.getLeft().getIssueFirstRep().getDiagnostics());
  }

  private IUpdateExecutable resourceUpdate(IBaseResource resource) {
    return fhirClient.update().resource(resource).prefer(OPERATION_OUTCOME);
  }

  private ICreateTyped resourceCreate(IBaseResource resource) {
    return fhirClient.create().resource(resource).prefer(OPERATION_OUTCOME);
  }

  private IQuery<IBaseBundle> organizationQuery(String profileUri) {
    return fhirClient.search().forResource(Organization.class).withProfile(profileUri);
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
      return name.equals(crit.getParameterName()) && value.equals(
          crit.getParameterValue(FhirContext.forR4()));
    }
  }
}
