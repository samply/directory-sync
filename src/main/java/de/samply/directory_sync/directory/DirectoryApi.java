package de.samply.directory_sync.directory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueType.NOTFOUND;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Returns true if the stored token from the Directory is null.
     *
     * The token will be null if either:
     * 1. Authorization has not yet been carried out, or
     * 2. Authorization has failed.
     *
     * @return
     */
    public boolean isNullToken() {
        return directoryToken == null;
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
        String directoryBiobankEntity = generateBiobanksEntityFromDirectoryId(id);
        HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/" + directoryBiobankEntity + "/" + id);
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

    /**
     * Send the collection sizes to the Directory.
     *
     * @param collectionSizes
     * @return
     */
    public OperationOutcome updateCollectionSizes(Map<String, Integer> collectionSizes) {
        if (collectionSizes.size() == 0)
            return errorInDirectoryResponseOperationOutcome("collection size update", "Empty list of collection sizes");
        OperationOutcome returnVal = errorInDirectoryResponseOperationOutcome("collection size update", "Unknown error");
        for (String collectionID : collectionSizes.keySet()) {
            returnVal = updateCollectionSize(collectionID, collectionSizes.get(collectionID));
            if (returnVal.hasIssue() && returnVal.getIssue().get(0).hasSeverity() && returnVal.getIssue().get(0).getSeverity().equals(OperationOutcome.IssueSeverity.ERROR))
                break;
        }

        // TODO:
        // This function will only return a message from one collection.
        // This may never be a problem, because we anticipate that most sites
        // will only have a single collection. But in the future, message handling
        // may need to be improved.
        return returnVal;
    }

    /**
     * Send the size of the supplied collection to the Directory.
     * @param collectionId a valid Directory Collection ID, e.g. "bbmri-eric:ID:DE_12345:collection:0"
     * @param collectionSize the number of samples in the collection
     * @return
     */
    public OperationOutcome updateCollectionSize(String collectionId, Integer collectionSize) {
        String directoryCollectionEntity = generateCollectionsEntityFromDirectoryId(collectionId);

        // Pull a list of all collection IDs from the Directory
        Either<OperationOutcome, Set<String>> result = listAllCollectionIds(directoryCollectionEntity);
        if (result.isLeft()) {
            return result.getLeft();
        }

        Set<String> existingCollectionIds = result.get();

        if ( ! existingCollectionIds.contains(collectionId))
            return errorInDirectoryResponseOperationOutcome("collection size update", "Collection ID " + collectionId + " was not found in Directory entity " + directoryCollectionEntity);

        List<CollectionSizeDto> collectionSizeDtos = new ArrayList<CollectionSizeDto>();
        collectionSizeDtos.add(new CollectionSizeDto(collectionId, collectionSize));

    String payload = gson.toJson(new EntitiesDto<>(collectionSizeDtos));

        // Push the counts back to the Directory. You need 'update data' permission
        // on entity type 'Collections' at the Directory in order for this to work.
        HttpPut httpPut = new HttpPut(baseUrl + "/api/v2/" + directoryCollectionEntity + "/size");
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

    /**
     * Takes a Collection ID or a Biobank ID and extracts the country code from it.
     * Returns an empty string if no country code can be found in the ID (e.g. if
     * the ID is not a valid Directory ID).
     *
     * @param biobankOrCollectionId e.g. "bbmri-eric:ID:DE_12345:collection:0"
     * @return country code, e.g. "DE".
     */
    private String extractCountryCodeFromBiobankOrCollectionId(String biobankOrCollectionId) {
        String[] parts = biobankOrCollectionId.split(":");
        if (parts.length < 3)
            return "";
        String id = parts[2];
        String[] subParts = id.split("_");
        if (subParts.length < 2)
            return "";
        String countryCode = subParts[0].toUpperCase();
        if (countryCode.length() > 3)
            return "";
        if ( ! countryCode.matches("^[A-Z]+$"))
            return "";

        return countryCode;
    }

    /**
     * Generate a Directory Biobank entity for the national node responsible
     * for this biobank.
     **/
    private String generateBiobanksEntityFromDirectoryId(String directoryId) {
        String countryCode = extractCountryCodeFromBiobankOrCollectionId(directoryId);

        return "eu_bbmri_eric_" + countryCode + "_biobanks";
    }

    /**
     * Generate a Directory Collection entity for the national node responsible
     * for the collections at this biobank.
     **/
    private String generateCollectionsEntityFromDirectoryId(String directoryId) {
        String countryCode = extractCountryCodeFromBiobankOrCollectionId(directoryId);

        return "eu_bbmri_eric_" + countryCode + "_collections";
    }

    /**
     * Make a call to the Directory to get all Collection IDs for the supplied entity.
     * The entity will correspond to a national node, e.g. Germany.
     *
     * @param directoryCollectionEntity a Directory entity that contains Collections,
     *                                  e.g. "eu_bbmri_eric_DE_collections".
     * @return all the Collections for this entity. E.g. for "eu_bbmri_eric_DE_collections", this
     * will return all German collections.
     */
    public Either<OperationOutcome, Set<String>> listAllCollectionIds(String directoryCollectionEntity) {
        // Call the Directory to get a list of all European collection IDs.
        // If you simply specify "attrs=id", you will only get the first 100
        // IDs. Setting "start" to 0 and "num" its maximum allowed value
        // gets them all. Note that in the current Directory implementation
        // (12.10.2021), the maximum allowed value of "num" is 10000.
        HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/" + directoryCollectionEntity + "?attrs=id&start=0&num=10000");
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
