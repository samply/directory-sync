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
  private final String token;
  private final Gson gson = new Gson();

  private DirectoryApi(CloseableHttpClient httpClient, String baseUrl, String token) {
    this.httpClient = Objects.requireNonNull(httpClient);
    this.baseUrl = Objects.requireNonNull(baseUrl);
    this.token = Objects.requireNonNull(token);
  }

  public static Either<OperationOutcome, DirectoryApi> createWithLogin(
      CloseableHttpClient httpClient,
      String baseUrl, String username, String password) {
    return login(httpClient, baseUrl.replaceFirst("/*$", ""), username, password)
        .map(response -> createWithToken(httpClient, baseUrl, response.token));
  }

  private static Either<OperationOutcome, LoginResponse> login(CloseableHttpClient httpClient,
      String baseUrl,
      String username, String password) {
    HttpPost request = loginRequest(baseUrl, username, password);
    try (CloseableHttpResponse response = httpClient.execute(request)) {
      return Either.right(decodeLoginResponse(response));
    } catch (IOException e) {
      return Either.left(error("login", e.getMessage()));
    }
  }

  private static HttpPost loginRequest(String baseUrl, String username, String password) {
    HttpPost request = new HttpPost(baseUrl + "/api/v1/login");
    request.setHeader("Accept", "application/json");
    request.setHeader("Content-type", "application/json");
    request.setEntity(encodeLoginCredentials(username, password));
    return request;
  }

  private static StringEntity encodeLoginCredentials(String username, String password) {
    return new StringEntity(new Gson().toJson(new LoginCredentials(username, password)), UTF_8);
  }

  private static LoginResponse decodeLoginResponse(CloseableHttpResponse tokenResponse)
      throws IOException {
    String body = EntityUtils.toString(tokenResponse.getEntity(), UTF_8);
    return new Gson().fromJson(body, LoginResponse.class);
  }

  public static DirectoryApi createWithToken(CloseableHttpClient httpClient, String baseUrl,
      String token) {
    return new DirectoryApi(httpClient, baseUrl.replaceFirst("/*$", ""), token);
  }

  private static OperationOutcome error(String action, String message) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(ERROR).setDiagnostics(errorMsg(action, message));
    return outcome;
  }

  private static String errorMsg(String action, String message) {
    return String.format("Error in BBMRI Directory response for %s, cause: %s", action,
        message);
  }

  private static OperationOutcome biobankNotFound(BbmriEricId id) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(INFORMATION)
        .setCode(NOTFOUND)
        .setDiagnostics(String.format("No Biobank in Directory with id `%s`.", id));
    return outcome;
  }

  private static OperationOutcome updateSuccessful(int number) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(INFORMATION)
        .setDiagnostics(String.format("Successful update of %d collection size values.", number));
    return outcome;
  }

  /**
   * Fetches the Biobank with the given {@code id}.
   *
   * @param id the ID of the Biobank to fetch.
   * @return either the Biobank or an error
   */
  public Either<OperationOutcome, Biobank> fetchBiobank(BbmriEricId id) {
    try (CloseableHttpResponse response = httpClient.execute(fetchBiobankRequest(id))) {
      if (response.getStatusLine().getStatusCode() == 200) {
        String payload = EntityUtils.toString(response.getEntity(), UTF_8);
        return Either.right(gson.fromJson(payload, Biobank.class));
      } else if (response.getStatusLine().getStatusCode() == 404) {
        return Either.left(biobankNotFound(id));
      } else {
        String message = EntityUtils.toString(response.getEntity(), UTF_8);
        return Either.left(error(id.toString(), message));
      }
    } catch (IOException e) {
      return Either.left(error(id.toString(), e.getMessage()));
    }
  }

  private HttpGet fetchBiobankRequest(BbmriEricId id) {
    HttpGet request = new HttpGet(
        baseUrl + "/api/v2/eu_bbmri_eric_biobanks/" + id.toString());
    request.setHeader("x-molgenis-token", token);
    request.setHeader("Accept", "application/json");
    return request;
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

    HttpPut request = updateCollectionSizesRequest(countryCode, collectionSizeDtos);

    try (CloseableHttpResponse response = httpClient.execute(request)) {
      if (response.getStatusLine().getStatusCode() < 300) {
        return updateSuccessful(collectionSizeDtos.size());
      } else {
        return error("collection size update", EntityUtils.toString(response.getEntity(), UTF_8));
      }
    } catch (IOException e) {
      return error("collection size update", e.getMessage());
    }
  }

  private HttpPut updateCollectionSizesRequest(String countryCode,
      List<CollectionSizeDto> collectionSizeDtos) {
    HttpPut request = new HttpPut(
        baseUrl + "/api/v2/eu_bbmri_eric_collections/size");
    request.setHeader("x-molgenis-token", token);
    request.setHeader("Accept", "application/json");
    request.setHeader("Content-type", "application/json");
    request.setEntity(new StringEntity(gson.toJson(new EntitiesDto<>(collectionSizeDtos)), UTF_8));
    return request;
  }

  /**
   * Make a call to the Directory to get all Collection IDs for the supplied {@code countryCode}.
   *
   * @param countryCode the country code of the endpoint of the national node, e.g. Germany
   * @return all the Collections for the national node. E.g. "DE" will return all German collections
   */
  public Either<OperationOutcome, Set<BbmriEricId>> listAllCollectionIds(String countryCode) {
    return fetchIdItems(listAllCollectionIdsRequest(countryCode), "list collection ids")
        .map(i -> i.items.stream()
            .map(e -> e.id)
            .map(BbmriEricId::valueOf)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet()));
  }

  private HttpGet listAllCollectionIdsRequest(String countryCode) {
    // If you simply specify "attrs=id", you will only get the first 100
    // IDs. Setting "start" to 0 and "num" its maximum allowed value
    // gets them all. Note that in the current Directory implementation
    // (12.10.2021), the maximum allowed value of "num" is 10000.
    // TODO: to really get all collections, we have to implement paging
    HttpGet request = new HttpGet(
        baseUrl + "/api/v2/eu_bbmri_eric_collections?attrs=id&start=0&num=10000&q=country=="
            + countryCode);
    request.setHeader("x-molgenis-token", token);
    request.setHeader("Accept", "application/json");
    return request;
  }

  private Either<OperationOutcome, ItemsDto<IdDto>> fetchIdItems(HttpGet request, String action) {
    try (CloseableHttpResponse response = httpClient.execute(request)) {
      if (response.getStatusLine().getStatusCode() == 200) {
        return Either.right(decodeIdItems(response));
      } else {
        return Either.left(error(action, EntityUtils.toString(response.getEntity(), UTF_8)));
      }
    } catch (IOException e) {
      return Either.left(error(action, e.getMessage()));
    }
  }

  private ItemsDto<IdDto> decodeIdItems(CloseableHttpResponse response) throws IOException {
    String payload = EntityUtils.toString(response.getEntity(), UTF_8);
    return gson.fromJson(payload, new TypeToken<ItemsDto<IdDto>>() {
    }.getType());
  }

  private String urlPrefix(String countryCode) {
    return baseUrl + "/api/v2/eu_bbmri_eric_" + countryCode;
  }

  static class LoginCredentials {

    String username, password;

    LoginCredentials(String username, String password) {
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
