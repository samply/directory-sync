package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import com.google.gson.Gson;
import de.samply.directory_sync.directory.model.Biobank;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {

    public static final Function<Organization, Optional<String>> BBMRI_ERIC_IDENTIFIER = o ->
        o.getIdentifier().stream().filter(i -> "http://www.bbmri-eric.eu/".equals(i.getSystem()))
                .findFirst().map(Identifier::getValue);
    ;

    public static void main(String[] args) throws IOException {
        
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));
        Map<String, Integer> collectionSize = fetchCollectionSize(client);
        System.out.println("collectionSize = " + collectionSize);
        
        // System.out.println(getDirectory());
    }


    static class LoginResponse {
        String username, token;

        LoginResponse() {
        }

    }


    static String getDirectory() throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:8081/api/v1/login");

        //TODO Build via Object and Gson
        String json = "{\n" +
                "  \"username\": \"admin\",\n" +
                "  \"password\": \"admin\"\n" +
                "}";
        StringEntity entity = new StringEntity(json);
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse tokenResponse = client.execute(httpPost);
        String body = EntityUtils.toString(tokenResponse.getEntity());
        Gson gson = new Gson();
        LoginResponse loginResponse = gson.fromJson(body, LoginResponse.class);
        String token = loginResponse.token;

        HttpGet httpGet = new HttpGet("http://localhost:8081/api/v2/eu_bbmri_eric_collections");
        httpGet.setHeader("x-molgenis-token", token);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = client.execute(httpGet);
        String result = EntityUtils.toString(response.getEntity());
        client.close();

        return result;
    }


    static Map<String,Integer> fetchCollectionSize(IGenericClient client) {
        MeasureReport report = getMeasureReport(client, "https://fhir.bbmri.de/Measure/size");
        Map<String, Integer> counts = extractStratifierCounts(report);
        List<Organization> collections = fetchCollections(client, counts.keySet());
        return mapToCounts(counts, collections);
    }


    private static MeasureReport getMeasureReport(IGenericClient client, String url) {
        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
        inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));
        inParams.addParameter().setName("measure").setValue(new StringType(url));

        Parameters outParams = client
                .operation()
                .onType(Measure.class)
                .named("$evaluate-measure")
                .withParameters(inParams)
                .useHttpGet()
                .execute();


        return (MeasureReport) outParams.getParameter().get(0).getResource();
    }


    private static Map<String, Integer> extractStratifierCounts(MeasureReport report) {
       return report.getGroupFirstRep().getStratifierFirstRep().getStratum().stream()
               .filter( s -> s.getValue().getText().split("/").length == 2)
               .collect(Collectors.toMap(s -> s.getValue().getText().split("/")[1],
                       s -> s.getPopulationFirstRep().getCount()));
    }

    private static List<Organization> fetchCollections(IGenericClient client, Collection<String> ids) {
        Bundle response = (Bundle) client.search().forResource(Organization.class)
                .where(Organization.RES_ID.exactly().codes(ids)).execute();

        return response.getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
                .map(e -> (Organization) e.getResource())
                .collect(Collectors.toList());
    }
    

    static Map<String, Integer> mapToCounts(Map<String, Integer> counts, List<Organization> collections) {
        return collections.stream()
                .filter(o -> BBMRI_ERIC_IDENTIFIER.apply(o).isPresent())
                .filter(o -> counts.containsKey(o.getIdElement().getIdPart()))
                .collect(Collectors.toMap(o -> BBMRI_ERIC_IDENTIFIER.apply(o).get(),
                        o -> counts.get(o.getIdElement().getIdPart()), Integer::sum));
    }

    static void updateBiobankNameIfChanged(IGenericClient fhirClient, CloseableHttpClient httpClient, String directoryToken) throws IOException {
        //TODO Maybe return List of updated Biobanks?
        Bundle response = (Bundle) fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute();
        for(Bundle.BundleEntryComponent entry : response.getEntry()){
            Organization fhirBiobank = (Organization) entry.getResource();
            Optional<String> optBbmriId = BBMRI_ERIC_IDENTIFIER.apply(fhirBiobank);
            String bbmriId = null;
            if(optBbmriId.isEmpty()){
                continue;
            }else {
                bbmriId = optBbmriId.get();
            }

            //TODO Check if this works
            HttpGet httpGet = new HttpGet("http://localhost:8081/api/v2/eu_bbmri_eric_biobanks/"+bbmriId);
            httpGet.setHeader("x-molgenis-token", directoryToken);
            httpGet.setHeader("Accept", "application/json");
            httpGet.setHeader("Content-type", "application/json");

            CloseableHttpResponse directoryResponse = httpClient.execute(httpGet);
            String biobankJson = EntityUtils.toString(directoryResponse.getEntity());
            httpClient.close();

            Gson gson = new Gson();
            Biobank dirBiobank = gson.fromJson(biobankJson, Biobank.class);
            if(!dirBiobank.getName().trim().equalsIgnoreCase(fhirBiobank.getName().trim())){
                fhirBiobank.setName(dirBiobank.getName());
                try{
                    fhirClient.update().resource(fhirBiobank).execute();
                }catch (FhirClientConnectionException e){
                    //TODO Smthg useful
                }
            }




        }



    }

}
