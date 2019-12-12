package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class Main {


    public static void main(String[] args) throws IOException {
        //System.out.println(getBlaze());
        System.out.println(getDirectory());
    }


    static class LoginResponse {
        String username, token;

        LoginResponse() {
        }
    }


    static String getDirectory() throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("http://localhost:8081/api/v1/login");

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

        HttpGet httpGet = new HttpGet("http://localhost:8081/api/v2/sys_md_EntityType");
        httpGet.setHeader("x-molgenis-token", token);
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = client.execute(httpGet);
        String result = EntityUtils.toString(response.getEntity());
        client.close();

        return result;
    }


    static String getBlaze() {

        // Create a client to talk to the HeathIntersections server
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));

        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
        inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));
        //TODO add Measure canonicalUrl as Parameter

        // Invoke $everything on "Patient/1"
        Parameters outParams = client
                .operation()
                .onInstance(new IdDt("Measure", "5ddac8ad-371c-4e25-924d-46281964b348"))
                //TODO use onType istead of onInstance
                .named("$evaluate-measure")
                .withParameters(inParams)
                .useHttpGet()
                .execute();

        /*
         * Note that the $everything operation returns a Bundle instead
         * of a Parameters resource. The client operation methods return a
         * Parameters instance however, so HAPI creates a Parameters object
         * with a single parameter containing the value.
         */
        MeasureReport response = (MeasureReport) outParams.getParameter().get(0).getResource();

        // Print the response bundle
        return (ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(response));
    }
}
