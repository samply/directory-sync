package de.samply.directory_sync.directory;

import com.google.gson.Gson;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;

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

    public static DirectoryApi createWithLogin(CloseableHttpClient httpClient, String baseUrl, String username, String password) throws IOException {
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
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(String.format("Error in BBMRI Directory response for %s, cause: %s", id, message));
        return outcome;
    }

    public static void main(String[] args) throws IOException {
        DirectoryApi api = createWithToken(HttpClients.createDefault(), "https://molgenis39.gcc.rug.nl", "<token>");
        Either<OperationOutcome, Biobank> biobank = api.fetchBiobank("bbmri-eric:ID:DE_LMB");
        System.out.println("biobank = " + biobank);
    }

    public Either<OperationOutcome, Biobank> fetchBiobank(String id) {
        //TODO Check if this works
        HttpGet httpGet = new HttpGet(baseUrl + "/api/v2/eu_bbmri_eric_biobanks/" + id);
        httpGet.setHeader("x-molgenis-token", directoryToken);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");

        try {
            CloseableHttpResponse directoryResponse = httpClient.execute(httpGet);
            String biobankJson = EntityUtils.toString(directoryResponse.getEntity());
            httpClient.close();
            return Either.right(gson.fromJson(biobankJson, Biobank.class));
        } catch (IOException e) {
            return Either.left(errorInDirectoryResponseOperationOutcome(id, e.getMessage()));
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
}
