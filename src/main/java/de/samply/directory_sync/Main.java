package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.Parameters;

public class Main {
    public static void main(String[] args) {
        // Create a client to talk to the HeathIntersections server
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));

        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
        inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));

    // Invoke $everything on "Patient/1"
        Parameters outParams = client
                .operation()
                .onInstance(new IdDt("Measure", "5ddac8ad-371c-4e25-924d-46281964b348"))
                .named("$evaluate-measure")
                .withParameters(inParams)
                .execute();

        /*
         * Note that the $everything operation returns a Bundle instead
         * of a Parameters resource. The client operation methods return a
         * Parameters instance however, so HAPI creates a Parameters object
         * with a single parameter containing the value.
         */
        MeasureReport response = (MeasureReport) outParams.getParameter().get(0).getResource();

// Print the response bundle
        System.out.println(ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(response));
    }
}
