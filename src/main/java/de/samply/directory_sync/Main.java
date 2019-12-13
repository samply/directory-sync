package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {


    public static final Function<Resource, String> GET_ID_PART = ((Function<IdType, String>) IdType::getIdPart).compose(Resource::getIdElement);

    public static void main(String[] args) throws IOException {
        getBlaze();
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


    static void getBlaze() {

        // Create a client
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));
        MeasureReport report = getMeasureReport(client, "5df2282b-9c51-4be9-88c9-dd9ddbe1024f");


        Map<String, Integer> counts = extractStratifierCounts(report);

        Map<String, Organization> collections = fetchCollections(client, counts.keySet());

        Map<String, Integer> bbmriCounts = mergeById(counts, collections);

        System.out.println("bbmriCounts = " + bbmriCounts);

        /*// Print the response bundle
        return (ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(response));*/
    }



    private static Map<String, Organization> fetchCollections(IGenericClient client, Collection<String> ids) {
        Bundle response = (Bundle) client.search().forResource(Organization.class).where(Organization.RES_ID.exactly().codes(ids)).execute();

        return response.getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
                .map(e -> (Organization) e.getResource())
                .collect(Collectors.toMap(GET_ID_PART, Function.identity()));
    }

    private static Map<String, Integer> extractStratifierCounts(MeasureReport report) {
        Map<String, Integer> counts = new HashMap<>();

        for (MeasureReport.StratifierGroupComponent stratum : report.getGroupFirstRep().getStratifierFirstRep().getStratum()) {

            String reference = stratum.getValue().getText();
            String[] referenceParts = reference.split("/");
            if (referenceParts.length == 2) {
                counts.put(referenceParts[1], stratum.getPopulationFirstRep().getCount());
            } else {
                throw new IllegalArgumentException(String.format("Invalid collection reference `%s`", reference));
            }

        }
        return counts;
    }

    private static MeasureReport getMeasureReport(IGenericClient client, String id) {
        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
        inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));
        //TODO add Measure canonicalUrl as Parameter


        Parameters outParams = client
                .operation()
                .onInstance(new IdDt("Measure", id))
                //TODO use onType instead of onInstance
                .named("$evaluate-measure")
                .withParameters(inParams)
                .useHttpGet()
                .execute();


        return (MeasureReport) outParams.getParameter().get(0).getResource();
    }

    private static Map<String, Integer> mergeById(Map<String, Integer> counts, Map<String, Organization> collections) {
        Map<String, Integer> bbmriCounts = new HashMap<>();

        for (Map.Entry<String, Organization> collectionEntry : collections.entrySet()) {

            Organization collection = collectionEntry.getValue();

            for (Identifier identifier : collection.getIdentifier()) {
                if ("http://www.bbmri-eric.eu/".equals(identifier.getSystem())) {
                    String bbmriId = identifier.getValue();
                    if(bbmriId != null){
                        Integer count = counts.get(collectionEntry.getKey());
                        if(count != null){
                            bbmriCounts.put(bbmriId, count);
                        }

                    }

                }
            }

        }
        return bbmriCounts;
    }

}
