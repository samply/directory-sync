package de.samply.directory_sync.directory;

import com.google.gson.Gson;
import de.samply.directory_sync.directory.model.Biobank;
import io.vavr.control.Either;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.IOException;
import java.util.Optional;

public class DirectoryApi {

    private final CloseableHttpClient httpClient;

    private final String directoryToken;
    private final Gson gson = new Gson();

    public DirectoryApi(CloseableHttpClient httpClient,String directoryToken) {
        this.httpClient = httpClient;
        this.directoryToken = directoryToken;
    }

    public Either<OperationOutcome,Biobank> fetchBiobank(String id){
        //TODO Check if this works
        HttpGet httpGet = new HttpGet("http://localhost:8081/api/v2/eu_bbmri_eric_biobanks/"+id);
        httpGet.setHeader("x-molgenis-token", directoryToken);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");

        try {
            CloseableHttpResponse directoryResponse = httpClient.execute(httpGet);
            String biobankJson = EntityUtils.toString(directoryResponse.getEntity());
            httpClient.close();
            return Either.right(gson.fromJson(biobankJson, Biobank.class));
        }catch(IOException e){
            return Either.left(errorInDirectoryResponseOperationOutcome(id,e.getMessage()));
        }
    }

    static OperationOutcome errorInDirectoryResponseOperationOutcome(String id,String message){
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(String.format("Error in BBMRI Directory response for %s, cause: %s",id,message));
        return outcome;
    }

}
