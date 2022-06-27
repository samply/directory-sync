package de.samply.directory_sync.fhir;

import static ca.uhn.fhir.rest.api.EncodingEnum.JSON;
import static de.samply.directory_sync.TestUtil.createBbmriEricId;
import static de.samply.directory_sync.Util.mapOf;
import static io.vavr.control.Either.right;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.google.common.io.ByteStreams;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class FhirReportingIntegrationTest {

  private static final String LIBRARY_URI = "https://fhir.bbmri.de/Library/collection-size";
  private static final String MEASURE_URI = "https://fhir.bbmri.de/Measure/collection-size";

  @Container
  @SuppressWarnings("resource")
  private final GenericContainer<?> store = new GenericContainer<>("samply/blaze:0.17")
      .withImagePullPolicy(PullPolicy.alwaysPull())
      .withExposedPorts(8080)
      .waitingFor(Wait.forHttp("/health").forStatusCode(200))
      .withStartupAttempts(3);

  private String storeBaseUrl() {
    return String.format("http://%s:%d/fhir", store.getHost(), store.getFirstMappedPort());
  }

  private FhirContext fhirContext;
  private IGenericClient fhirClient;
  private FhirApi fhirApi;
  private FhirReporting reporting;

  @BeforeEach
  void setUp() {
    fhirContext = FhirContext.forR4();
    fhirClient = createFhirClient(fhirContext);
    fhirApi = new FhirApi(fhirClient);
    reporting = new FhirReporting(fhirContext, fhirApi);
  }

  @NotNull
  private IGenericClient createFhirClient(FhirContext fhirContext) {
    IGenericClient fhirClient = fhirContext.newRestfulGenericClient(storeBaseUrl());
    fhirClient.setEncoding(JSON);
    return fhirClient;
  }

  @Test
  void testInitLibrary() {
    Either<String, Void> result = reporting.initLibrary();

    assertTrue(result.isRight(), "the result is right");
    assertTrue(fhirApi.resourceExists(Library.class, LIBRARY_URI).get());
  }

  @Test
  void testInitMeasure() {
    Either<String, Void> result = reporting.initMeasure();

    assertTrue(result.isRight(), "the result is right");
    assertTrue(fhirApi.resourceExists(Measure.class, MEASURE_URI).get());
  }

  @Test
  void testFetchCollectionSizes_withoutLibraryAndMeasure() {
    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertTrue(result.isLeft(), "the result is left");
    assertEquals(IssueSeverity.ERROR, result.getLeft().getIssueFirstRep().getSeverity());
    assertEquals(
        "HTTP 400 Bad Request: The Measure resource with reference `https://fhir.bbmri.de/Measure/collection-size` was not found.",
        result.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testFetchCollectionSizes_withoutLibrary() {
    reporting.initMeasure();

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertTrue(result.isLeft(), "the result is left");
    assertEquals(IssueSeverity.ERROR, result.getLeft().getIssueFirstRep().getSeverity());
    assertEquals(
        "HTTP 400 Bad Request: The Library resource with canonical URI `https://fhir.bbmri.de/Library/collection-size` was not found.",
        result.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testFetchCollectionSizes_withoutAnyResources() {
    reporting.initLibrary();
    reporting.initMeasure();

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf()), result);
  }

  @Test
  void testFetchCollectionSizes() throws IOException {
    reporting.initLibrary();
    reporting.initMeasure();
    fhirClient.transaction().withBundle(parse(slurp("Bundle.json"), Bundle.class)).execute();

    Either<OperationOutcome, Map<BbmriEricId, Integer>> result = reporting.fetchCollectionSizes();

    assertEquals(right(mapOf(createBbmriEricId("bbmri-eric:ID:DE_185943"), 1)), result);
  }

  private <T> T parse(String s, Class<T> type) {
    IParser parser = fhirContext.newJsonParser();
    return type.cast(parser.parseResource(s));
  }

  private static String slurp(String name) throws IOException {
    try (InputStream in = FhirApi.class.getResourceAsStream(name)) {
      assumeFalse(in == null);
      return new String(ByteStreams.toByteArray(in), UTF_8);
    }
  }
}
