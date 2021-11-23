package de.samply.directory_sync.directory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.NOTFOUND;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.samply.directory_sync.directory.model.BbmriEricId;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    this.baseUrl = baseUrl.replaceFirst("/*$", "");
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

  static OperationOutcome errorInDirectoryResponseOperationOutcome(String action, String message) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(ERROR)
        .setDiagnostics(String.format("Error in BBMRI Directory response for %s, cause: %s", action,
            message));
    return outcome;
  }

  static OperationOutcome biobankNotFoundOperationOutcome(BbmriEricId id) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(INFORMATION)
        .setCode(NOTFOUND)
        .setDiagnostics(String.format("No Biobank in Directory for %s", id));
    return outcome;
  }

  static OperationOutcome updateSuccessfullOperationOutcome(int number) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(INFORMATION)
        .setDiagnostics(String.format("Successful update of %d %s values", number,
            "collection size"));
    return outcome;
  }

  /**
   * Fetches the Biobank with the given {@code id}.
   *
   * @param id the ID of the Biobank to fetch.
   * @return either the Biobank or an error
   */
  public Either<OperationOutcome, Biobank> fetchBiobank(BbmriEricId id) {
    HttpGet httpGet = new HttpGet(urlPrefix(id.getCountryCode()) + "_biobanks/" + id);
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
        return Either.left(errorInDirectoryResponseOperationOutcome(id.toString(), message));
      }
    } catch (IOException e) {
      return Either.left(errorInDirectoryResponseOperationOutcome(id.toString(), e.getMessage()));
    }
  }

  /**
   * Send the collection sizes to the Directory.
   * <p>
   * Push the counts back to the Directory. You need 'update data' permission on entity type
   * 'Collections' at the Directory in order for this to work.
   *
   * @param countryCode        the country code of the endpoint of the national node, e.g. Germany
   * @param collectionSizeDtos the individual collection sizes. note that all collection must share
   *                           the given {@code countryCode}
   * @return an outcome, either successful or an error
   */
  public OperationOutcome updateCollectionSizes(String countryCode,
      List<CollectionSizeDto> collectionSizeDtos) {
    String payload = gson.toJson(new EntitiesDto<>(collectionSizeDtos));

    HttpPut httpPut = new HttpPut(urlPrefix(countryCode) + "_collections/size");
    httpPut.setHeader("x-molgenis-token", directoryToken);
    httpPut.setHeader("Accept", "application/json");
    httpPut.setHeader("Content-type", "application/json");
    httpPut.setEntity(new StringEntity(payload, UTF_8));

    try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
      if (response.getStatusLine().getStatusCode() < 300) {
        return updateSuccessfullOperationOutcome(collectionSizeDtos.size());
      } else {
        return errorInDirectoryResponseOperationOutcome("collection size update",
            EntityUtils.toString(response.getEntity()));
      }
    } catch (IOException e) {
      return errorInDirectoryResponseOperationOutcome("collection size update", e.getMessage());
    }
  }

  /**
   * Make a call to the Directory to get all Collection IDs for the supplied {@code countryCode}.
   *
   * @param countryCode the country code of the endpoint of the national node, e.g. Germany
   * @return all the Collections for the national node. E.g. "DE" will return all German collections
   */
  public Either<OperationOutcome, Set<BbmriEricId>> listAllCollectionIds(String countryCode) {
    // If you simply specify "attrs=id", you will only get the first 100
    // IDs. Setting "start" to 0 and "num" its maximum allowed value
    // gets them all. Note that in the current Directory implementation
    // (12.10.2021), the maximum allowed value of "num" is 10000.
    // TODO: to really get all collections, we have to implement paging
    HttpGet httpGet = new HttpGet(urlPrefix(countryCode) +
        "_collections?attrs=id&start=0&num=10000");
    httpGet.setHeader("x-molgenis-token", directoryToken);
    httpGet.setHeader("Accept", "application/json");

    try (CloseableHttpResponse directoryResponse = httpClient.execute(httpGet)) {
      if (directoryResponse.getStatusLine().getStatusCode() == 200) {
        String payload = EntityUtils.toString(directoryResponse.getEntity());
        ItemsDto<IdDto> items = gson.fromJson(payload, new TypeToken<ItemsDto<IdDto>>() {
        }.getType());
        return Either.right(items.items.stream()
            .map(e -> e.id)
            .map(BbmriEricId::valueOf)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet()));
      } else {
        return Either.left(errorInDirectoryResponseOperationOutcome("list collection ids",
            EntityUtils.toString(directoryResponse.getEntity())));
      }
    } catch (IOException e) {
      return Either.left(
          errorInDirectoryResponseOperationOutcome("list collection ids", e.getMessage()));
    }
  }

  private String urlPrefix(String countryCode) {
    return baseUrl + "/api/v2/eu_bbmri_eric_" + countryCode;
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

    public CollectionSizeDto(BbmriEricId id, int size) {
      this.id = id.toString();
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
