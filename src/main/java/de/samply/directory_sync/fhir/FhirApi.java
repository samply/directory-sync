package de.samply.directory_sync.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FhirApi {

    public static final Function<Organization, Optional<String>> BBMRI_ERIC_IDENTIFIER = o ->
        o.getIdentifier().stream().filter(i -> "http://www.bbmri-eric.eu/".equals(i.getSystem()))
                .findFirst().map(Identifier::getValue);

    private final IGenericClient fhirClient;

    public FhirApi(IGenericClient fhirClient) {
        this.fhirClient = fhirClient;
    }

    public IGenericClient getFhirClient() {
        return fhirClient;
    }

    public OperationOutcome updateResource(IBaseResource theResource) {
        try {
            return (OperationOutcome) fhirClient.update().resource(theResource).prefer(PreferReturnEnum.OPERATION_OUTCOME).execute().getOperationOutcome();
        } catch (Exception e) {
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(e.getMessage());
            return outcome;
        }
    }

    public Bundle listAllBiobanks() {
        return (Bundle) fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute();
    }

    public Map<String,Integer> fetchCollectionSizes() {
        MeasureReport report = getMeasureReport("https://fhir.bbmri.de/Measure/size");
        Map<String, Integer> counts = extractStratifierCounts(report);
        List<Organization> collections = fetchCollections(counts.keySet());
        return mapToCounts(counts, collections);
    }


    private MeasureReport getMeasureReport(String url) {
        // Create the input parameters to pass to the server
        Parameters inParams = new Parameters();
        inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
        inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));
        inParams.addParameter().setName("measure").setValue(new StringType(url));

        Parameters outParams = fhirClient
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
                .filter( stratum -> 2 == stratum.getValue().getText().split("/").length)
                .collect(Collectors.toMap(stratum -> stratum.getValue().getText().split("/")[1],
                        stratum -> stratum.getPopulationFirstRep().getCount()));
    }

    private  List<Organization> fetchCollections(Collection<String> ids) {
        Bundle response = (Bundle) fhirClient.search().forResource(Organization.class)
                .where(Organization.RES_ID.exactly().codes(ids)).execute();

        return response.getEntry().stream()
                .filter(e -> ResourceType.Organization == e.getResource().getResourceType())
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

    public static void main(String[] args) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));
        FhirApi fhirApi = new FhirApi(client);
        System.out.println(fhirApi.fetchCollectionSizes());
    }
}
