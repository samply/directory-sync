package de.samply.directory_sync.directory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.lang.reflect.Type;
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
        this.baseUrl = baseUrl;
        this.directoryToken = directoryToken;
    }

    public static DirectoryApi createWithLogin(CloseableHttpClient httpClient, String baseUrl, String username,
                                               String password) throws IOException {
        HttpPost httpPost = new HttpPost(baseUrl + "/api/v1/login");

        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setEntity(new StringEntity(new Gson().toJson(new LoginCredential(username, password)), "utf-8"));

        CloseableHttpResponse tokenResponse = httpClient.execute(httpPost);
        String body = EntityUtils.toString(tokenResponse.getEntity(), "utf-8");
        LoginResponse loginResponse = new Gson().fromJson(body, LoginResponse.class);

        return new DirectoryApi(httpClient, baseUrl, loginResponse.token);
    }

    public static DirectoryApi createWithToken(CloseableHttpClient httpClient, String baseUrl, String token) throws IOException {
        return new DirectoryApi(httpClient, baseUrl, token);
    }

    static OperationOutcome errorInDirectoryResponseOperationOutcome(String id, String message) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(String.format("Error in " +
                "BBMRI Directory response for %s, cause: %s", id, message));
        return outcome;
    }

    static OperationOutcome biobankNotFoundOperationOutcome(String id) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION).setCode(OperationOutcome.IssueType.NOTFOUND).setDiagnostics(String.format("No Biobank in Directory for %s", id));
        return outcome;
    }

    static OperationOutcome updateSuccessfullOperationOutcome(String updatedAttribute, int number) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION).setDiagnostics(String.format(
                "Successful update of %d %s values", number, updatedAttribute));
        return outcome;
    }

    public static void main(String[] args) throws IOException {
        DirectoryApi api = createWithToken(HttpClients.createDefault(), "https://molgenis39.gcc.rug.nl",
                "<token>");
//        Either<OperationOutcome, Biobank> biobank = api.fetchBiobank("bbmri-eric:ID:DE_LMB");
//        System.out.println("biobank = " + biobank);
        System.out.println(api.listAllCollectionIds());
    }

    public Either<OperationOutcome, Biobank> fetchBiobank(String id) {
        //TODO Check if this works
        HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/eu_bbmri_eric_biobanks/" + id);
        httpGet.setHeader("x-molgenis-token", directoryToken);
        httpGet.setHeader("Accept", "application/json");

        try (CloseableHttpResponse directoryResponse = httpClient.execute(httpGet)) {
            String json = EntityUtils.toString(directoryResponse.getEntity());
            if (directoryResponse.getStatusLine().getStatusCode() == 200) {
                return Either.right(gson.fromJson(json, Biobank.class));
            } else if (directoryResponse.getStatusLine().getStatusCode() == 404) {
                return Either.left(biobankNotFoundOperationOutcome(id));
            } else {
                return Either.left(errorInDirectoryResponseOperationOutcome(id,
                        EntityUtils.toString(directoryResponse.getEntity())));
            }
        } catch (IOException e) {
            return Either.left(errorInDirectoryResponseOperationOutcome(id, e.getMessage()));
        }
    }

    public OperationOutcome updateCollectionSizes(Map<String, Integer> collectionSizes) {
        Either<OperationOutcome, Set<String>> result = listAllCollectionIds();
        if (result.isLeft()) {
            return result.getLeft();
        }

        Set<String> existingCollectionIds = result.get();

        List<CollectionSizeDto> collectionSizeDtos = collectionSizes.entrySet().stream()
                .filter(e -> existingCollectionIds.contains(e.getKey()))
                .map(e -> new CollectionSizeDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        String payload = gson.toJson(new EntitiesDto<>(collectionSizeDtos));

        HttpPut httpPut = new HttpPut(baseUrl + "/api/v2/eu_bbmri_eric_collections/size");
        httpPut.setHeader("x-molgenis-token", directoryToken);
        httpPut.setHeader("Accept", "application/json");
        httpPut.setHeader("Content-type", "application/json");
        httpPut.setEntity(new StringEntity(payload, "utf-8"));

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

    Either<OperationOutcome, Set<String>> listAllCollectionIds() {
        HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/eu_bbmri_eric_collections?attrs=id");
        httpGet.setHeader("x-molgenis-token", directoryToken);
        httpGet.setHeader("Accept", "application/json");

        try (CloseableHttpResponse directoryResponse = httpClient.execute(httpGet)) {
            String json = EntityUtils.toString(directoryResponse.getEntity());
            if (directoryResponse.getStatusLine().getStatusCode() == 200) {
                ItemsDto<IdDto> items = gson.fromJson(json, new TypeToken<ItemsDto<IdDto>>() {}.getType());
                return Either.right(items.items.stream().map(e -> e.id).collect(Collectors.toSet()));
            } else {
                return Either.left(errorInDirectoryResponseOperationOutcome("list collection ids",
                        EntityUtils.toString(directoryResponse.getEntity())));
            }
        } catch (IOException e) {
            return Either.left(errorInDirectoryResponseOperationOutcome("list collection ids", e.getMessage()));
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

    private static class CollectionSizeDto {
        public CollectionSizeDto(String id, int size) {
            this.id = id;
            this.size = size;
        }

        String id;
        int size;
    }

    private static class ItemsDto<T> {
        List<T> items;
    }

    private static class IdDto {
        String id;
    }
}
