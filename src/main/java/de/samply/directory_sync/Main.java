package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
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
import org.hl7.fhir.r4.model.*;
import org.w3c.dom.html.HTMLImageElement;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {

    static final Function<Organization, Optional<String>> BBMRI_ERIC_IDENTIFIER = o ->
        o.getIdentifier().stream().filter(i -> "http://www.bbmri-eric.eu/".equals(i.getSystem()))
                .findFirst().map(Identifier::getValue);


    /**
     *
     * @param args username password
     */
    public static void main(String[] args) throws IOException {

        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));
        Map<String, Integer> collectionSize = fetchCollectionSize(client);
        System.out.println("collectionSize = " + collectionSize);

        System.out.println(getDirectory(args[0], args[1]));
    }


    static class LoginResponse {
        String username, token;

        LoginResponse() {
        }

    }

    static class LoginCredential {
        String username, password;

        LoginCredential(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }


    static String getDirectory(String username, String password) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://molgenis39.gcc.rug.nl/api/v1/login");

        httpPost.setEntity(new StringEntity(new Gson().toJson(new LoginCredential(username, password))));
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");

        CloseableHttpResponse tokenResponse = client.execute(httpPost);
        String body = EntityUtils.toString(tokenResponse.getEntity());
        LoginResponse loginResponse = new Gson().fromJson(body, LoginResponse.class);
        String token = loginResponse.token;
        System.out.println(token);

        HttpGet httpGet = new HttpGet("https://molgenis39.gcc.rug.nl/api/v2/eu_bbmri_eric_collections");
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


}
