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


    /**
     *
     * @param args username password
     */
    public static void main(String[] args) throws IOException {

//        FhirContext ctx = FhirContext.forR4();
//        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
//        client.registerInterceptor(new LoggingInterceptor(true));
//        Map<String, Integer> collectionSize = fetchCollectionSize(client);
//        System.out.println("collectionSize = " + collectionSize);
//
//        System.out.println(getDirectory(args[0], args[1]));
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





}
