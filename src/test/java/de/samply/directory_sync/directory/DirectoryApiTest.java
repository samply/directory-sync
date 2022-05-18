package de.samply.directory_sync.directory;

import static de.samply.directory_sync.TestUtil.createBbmriEricId;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.NOTFOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.samply.directory_sync.directory.DirectoryApi.CollectionSizeDto;
import de.samply.directory_sync.directory.model.BbmriEricId;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectoryApiTest {

  private static final String BASE_URL = "base-url-110950";
  private static final String TOKEN = "token-111037";
  private static final BbmriEricId AT_BIOBANK_ID = createBbmriEricId("bbmri-eric:ID:AT_MUG");
  private static final String ERROR_MESSAGE = "error-message-132848";
  private static final BbmriEricId COLLECTION_ID = createBbmriEricId(
      "bbmri-eric:ID:AT_MUG:collection:0");
  private static final int COLLECTION_SIZE = 135807;

  @Mock
  private CloseableHttpClient httpClient;

  private DirectoryApi api;

  @BeforeEach
  void setUp() {
    api = DirectoryApi.createWithToken(httpClient, BASE_URL, TOKEN);
  }

  @Test
  void fetchBiobank_Successful() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_AT_biobanks/" + AT_BIOBANK_ID;
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    when(httpClient.execute(argThat(httpGetMatcher(uri)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(200));
    when(response.getEntity()).thenReturn(httpEntity("{\"id\":\"" + AT_BIOBANK_ID + "\"}"));

    Either<OperationOutcome, Biobank> result = api.fetchBiobank(AT_BIOBANK_ID);

    assertTrue(result.isRight(), "the result is right");
    assertEquals(AT_BIOBANK_ID, result.get().getId());
  }

  @Test
  void fetchBiobank_NotFound() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_AT_biobanks/" + AT_BIOBANK_ID;
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    when(httpClient.execute(argThat(httpGetMatcher(uri)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(404));

    Either<OperationOutcome, Biobank> result = api.fetchBiobank(AT_BIOBANK_ID);

    assertTrue(result.isLeft(), "the result is left");
    assertSame(NOTFOUND, result.getLeft().getIssueFirstRep().getCode());
  }

  @Test
  void fetchBiobank_ServerError() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_AT_biobanks/" + AT_BIOBANK_ID;
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    when(httpClient.execute(argThat(httpGetMatcher(uri)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(500));
    when(response.getEntity()).thenReturn(httpEntity(ERROR_MESSAGE));

    Either<OperationOutcome, Biobank> biobank = api.fetchBiobank(AT_BIOBANK_ID);

    assertTrue(biobank.isLeft(), "the result is left");
    assertEquals("Error in BBMRI Directory response for " + AT_BIOBANK_ID + ", cause: " +
        ERROR_MESSAGE, biobank.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void fetchBiobank_IOException() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_AT_biobanks/" + AT_BIOBANK_ID;
    when(httpClient.execute(argThat(httpGetMatcher(uri))))
        .thenThrow(new IOException(ERROR_MESSAGE));

    Either<OperationOutcome, Biobank> biobank = api.fetchBiobank(AT_BIOBANK_ID);

    assertTrue(biobank.isLeft(), "the result is left");
    assertEquals("Error in BBMRI Directory response for " + AT_BIOBANK_ID + ", cause: " +
        ERROR_MESSAGE, biobank.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void updateCollectionSizes_Successful() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_DE_collections/size";
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    String content = "{\"entities\":[{\"id\":\"" + COLLECTION_ID + "\",\"size\":" + COLLECTION_SIZE
        + "}]}";
    when(httpClient.execute(argThat(httpPutMatcher(uri, content)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(200));

    OperationOutcome outcome = api.updateCollectionSizes("DE",
        singletonList(new CollectionSizeDto(COLLECTION_ID, COLLECTION_SIZE)));

    assertSame(INFORMATION, outcome.getIssueFirstRep().getSeverity());
  }

  @Test
  void updateCollectionSizes_ServerError() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_DE_collections/size";
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    String content = "{\"entities\":[{\"id\":\"" + COLLECTION_ID + "\",\"size\":" + COLLECTION_SIZE
        + "}]}";
    when(httpClient.execute(argThat(httpPutMatcher(uri, content)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(500));
    when(response.getEntity()).thenReturn(httpEntity(ERROR_MESSAGE));

    OperationOutcome outcome = api.updateCollectionSizes("DE",
        singletonList(new CollectionSizeDto(COLLECTION_ID, COLLECTION_SIZE)));

    assertEquals("Error in BBMRI Directory response for collection size update, cause: " +
        ERROR_MESSAGE, outcome.getIssueFirstRep().getDiagnostics());
  }

  @Test
  void updateCollectionSizes_IOException() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_DE_collections/size";
    String content = "{\"entities\":[{\"id\":\"" + COLLECTION_ID + "\",\"size\":" + COLLECTION_SIZE
        + "}]}";
    when(httpClient.execute(argThat(httpPutMatcher(uri, content))))
        .thenThrow(new IOException(ERROR_MESSAGE));

    OperationOutcome outcome = api.updateCollectionSizes("DE",
        singletonList(new CollectionSizeDto(COLLECTION_ID, COLLECTION_SIZE)));

    assertEquals("Error in BBMRI Directory response for collection size update, cause: " +
        ERROR_MESSAGE, outcome.getIssueFirstRep().getDiagnostics());
  }

  @Test
  void listAllCollectionIds_Successful() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_DE_collections?attrs=id&start=0&num=10000";
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    when(httpClient.execute(argThat(httpGetMatcher(uri)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(200));
    when(response.getEntity()).thenReturn(
        httpEntity("{\"items\":[{\"id\":\"" + COLLECTION_ID + "\"}]}"));

    Either<OperationOutcome, Set<BbmriEricId>> ids = api.listAllCollectionIds("DE");

    assertTrue(ids.isRight(), "the result is right");
    assertEquals(Collections.singleton(COLLECTION_ID), ids.get());
  }

  @Test
  void listAllCollectionIds_ServerError() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_DE_collections?attrs=id&start=0&num=10000";
    CloseableHttpResponse response = mock(CloseableHttpResponse.class);
    when(httpClient.execute(argThat(httpGetMatcher(uri)))).thenReturn(response);
    when(response.getStatusLine()).thenReturn(statusLine(500));
    when(response.getEntity()).thenReturn(httpEntity(ERROR_MESSAGE));

    Either<OperationOutcome, Set<BbmriEricId>> ids = api.listAllCollectionIds("DE");

    assertTrue(ids.isLeft(), "the result is left");
    assertEquals("Error in BBMRI Directory response for list collection ids, cause: " +
        ERROR_MESSAGE, ids.getLeft().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void listAllCollectionIds_IOException() throws IOException {
    String uri = "/api/v2/eu_bbmri_eric_DE_collections?attrs=id&start=0&num=10000";
    when(httpClient.execute(argThat(httpGetMatcher(uri))))
        .thenThrow(new IOException(ERROR_MESSAGE));

    Either<OperationOutcome, Set<BbmriEricId>> ids = api.listAllCollectionIds("DE");

    assertTrue(ids.isLeft(), "the result is left");
    assertEquals("Error in BBMRI Directory response for list collection ids, cause: " +
        ERROR_MESSAGE, ids.getLeft().getIssueFirstRep().getDiagnostics());
  }

  private static ArgumentMatcher<HttpGet> httpGetMatcher(String uri) {
    return httpGet -> URI.create(BASE_URL + uri).equals(httpGet.getURI()) &&
        TOKEN.equals(httpGet.getFirstHeader("x-molgenis-token").getValue());
  }

  private static ArgumentMatcher<HttpPut> httpPutMatcher(String uri, String content) {
    return httpPut -> URI.create(BASE_URL + uri).equals(httpPut.getURI()) &&
        TOKEN.equals(httpPut.getFirstHeader("x-molgenis-token").getValue()) &&
        contentEquals(httpPut.getEntity(), content);
  }

  private static boolean contentEquals(HttpEntity entity, String content) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
      entity.writeTo(stream);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new String(stream.toByteArray(), UTF_8).equals(content);
  }

  private static BasicStatusLine statusLine(int statusCode) {
    return new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), statusCode, "");
  }

  private BasicHttpEntity httpEntity(String content) {
    BasicHttpEntity entity = new BasicHttpEntity();
    entity.setContent(new ByteArrayInputStream(content.getBytes(UTF_8)));
    return entity;
  }
}
