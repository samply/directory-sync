package de.samply.directory_sync.directory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.NOTFOUND;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.OperationOutcome;

public class DirectoryApi {

  private final CloseableHttpClient httpClient;
  private final String baseUrl;
  private final String directoryToken;

  private final Gson gson = new Gson();

  private DirectoryApi(CloseableHttpClient httpClient, String baseUrl, String directoryToken) {
    this.httpClient = httpClient;
    this.baseUrl = baseUrl;
    this.directoryToken = directoryToken;
  }

  public static DirectoryApi createWithLogin(CloseableHttpClient httpClient, String baseUrl,
      String username,
      String password) throws IOException {
    HttpPost httpPost = new HttpPost(baseUrl + "/api/v1/login");

    httpPost.setHeader("Accept", "application/json");
    httpPost.setHeader("Content-type", "application/json");
    httpPost.setEntity(
        new StringEntity(new Gson().toJson(new LoginCredential(username, password)), "utf-8"));

    CloseableHttpResponse tokenResponse = httpClient.execute(httpPost);
    String body = EntityUtils.toString(tokenResponse.getEntity(), "utf-8");
    LoginResponse loginResponse = new Gson().fromJson(body, LoginResponse.class);

    return new DirectoryApi(httpClient, baseUrl, loginResponse.token);
  }

  public static DirectoryApi createWithToken(CloseableHttpClient httpClient, String baseUrl,
      String token) {
    return new DirectoryApi(httpClient, baseUrl, token);
  }

  static OperationOutcome errorInDirectoryResponseOperationOutcome(String id, String message) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(ERROR)
        .setDiagnostics(String.format("Error in BBMRI Directory response for %s, cause: %s", id,
            message));
    return outcome;
  }

  static OperationOutcome biobankNotFoundOperationOutcome(String id) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(INFORMATION)
        .setCode(NOTFOUND)
        .setDiagnostics(String.format("No Biobank in Directory for %s", id));
    return outcome;
  }

  static OperationOutcome updateSuccessfullOperationOutcome(String updatedAttribute, int number) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(INFORMATION)
        .setDiagnostics(String.format("Successful update of %d %s values", number,
            updatedAttribute));
    return outcome;
  }

  /**
   * Fetches the Biobank with the given {@code id}.
   *
   * @param id the ID of the Biobank to fetch.
   * @return either the Biobank or an error
   */
  public Either<OperationOutcome, Biobank> fetchBiobank(String id) {
    //TODO Check if this works
    HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/eu_bbmri_eric_biobanks/" + id);
    httpGet.setHeader("x-molgenis-token", directoryToken);
    httpGet.setHeader("Accept", "application/json");

    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
      if (response.getStatusLine().getStatusCode() == 200) {
        String payload = EntityUtils.toString(response.getEntity());
        return Either.right(gson.fromJson(payload, Biobank.class));
      } else if (response.getStatusLine().getStatusCode() == 404) {
        return Either.left(biobankNotFoundOperationOutcome(id));
      } else {
        String message = EntityUtils.toString(response.getEntity());
        return Either.left(errorInDirectoryResponseOperationOutcome(id, message));
      }
    } catch (IOException e) {
      return Either.left(errorInDirectoryResponseOperationOutcome(id, e.getMessage()));
    }
  }

  public OperationOutcome updateCollectionSizes(List<CollectionSizeDto> collectionSizeDtos) {
    String payload = gson.toJson(new EntitiesDto<>(collectionSizeDtos));

    HttpPut httpPut = new HttpPut(baseUrl + "/api/v2/eu_bbmri_eric_collections/size");
    httpPut.setHeader("x-molgenis-token", directoryToken);
    httpPut.setHeader("Accept", "application/json");
    httpPut.setHeader("Content-type", "application/json");
    httpPut.setEntity(new StringEntity(payload, UTF_8));

    try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
      if (response.getStatusLine().getStatusCode() < 300) {
        return updateSuccessfullOperationOutcome("collection size", collectionSizeDtos.size());
      } else {
        return errorInDirectoryResponseOperationOutcome("collection size update",
            EntityUtils.toString(response.getEntity()));
      }
    } catch (IOException e) {
      return errorInDirectoryResponseOperationOutcome("collection size update", e.getMessage());
    }
  }

  public Either<OperationOutcome, Set<String>> listAllCollectionIds() {
    HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/eu_bbmri_eric_collections?attrs=id");
    httpGet.setHeader("x-molgenis-token", directoryToken);
    httpGet.setHeader("Accept", "application/json");

    try (CloseableHttpResponse directoryResponse = httpClient.execute(httpGet)) {
      if (directoryResponse.getStatusLine().getStatusCode() == 200) {
        String payload = EntityUtils.toString(directoryResponse.getEntity());
        ItemsDto<IdDto> items = gson.fromJson(payload, new TypeToken<ItemsDto<IdDto>>() {
        }.getType());
        return Either.right(items.items.stream().map(e -> e.id).collect(Collectors.toSet()));
      } else {
        return Either.left(errorInDirectoryResponseOperationOutcome("list collection ids",
            EntityUtils.toString(directoryResponse.getEntity())));
      }
    } catch (IOException e) {
      return Either.left(
          errorInDirectoryResponseOperationOutcome("list collection ids", e.getMessage()));
    }
  }

  static class LoginCredential {

    String username, password;

    LoginCredential(String username, String password) {
      this.username = username;
      this.password = password;
    }
  }

  static class LoginResponse {

    String username, token;

    LoginResponse() {
    }
  }

  private static class EntitiesDto<T> {

    public EntitiesDto(List<T> entities) {
      this.entities = entities;
    }

    List<T> entities;
  }

  static class CollectionSizeDto {

    private final String id;
    private final int size;

    public CollectionSizeDto(String id, int size) {
      this.id = Objects.requireNonNull(id);
      this.size = size;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CollectionSizeDto that = (CollectionSizeDto) o;
      return size == that.size && id.equals(that.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, size);
    }
  }

  private static class ItemsDto<T> {

    List<T> items;
  }

  private static class IdDto {

    String id;
  }
}
