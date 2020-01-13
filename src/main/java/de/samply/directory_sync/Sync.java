package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.fhir.FhirApi;
import io.vavr.control.Either;
import io.vavr.control.Option;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;


public class Sync {

    private static Function<BiobankTuple, BiobankTuple> UPDATE_BIOBANK_NAME = t -> {
        t.fhirBiobank.setName(t.dirBiobank.getName());
        return t;
    };

    private FhirApi fhirApi;

    private DirectoryApi directoryApi;

    public Sync(FhirApi fhirApi, DirectoryApi directoryApi) {
        this.fhirApi = fhirApi;
        this.directoryApi = directoryApi;
    }

    static OperationOutcome missigIdentifierOperationOutcome(){
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics("No BBMRI Identifier for Organization");
        return outcome;
    }

    static OperationOutcome noUpdateNecessaryOperationOutcome(){
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION).setDiagnostics("No Update necessary");
        return outcome;
    }

    /**
     * Updates all biobanks from the FHIR server with information from the Directory.
     *
     * @return the individual {@link OperationOutcome}s from each update
     */
    List<OperationOutcome> updateBiobanksIfNecessary(){
        Bundle response = getFhirApi().listAllBiobanks();

        return response.getEntry().stream()
                .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
                .map(e -> (Organization) e.getResource())
                .filter(o -> o.getMeta().hasProfile("https://fhir.bbmri" +
                        ".de/StructureDefinition/Biobank"))
                .map(this::updateBiobankIfNecessary)
                .collect(Collectors.toList());
    }

    public FhirApi getFhirApi() {
        return fhirApi;
    }

    /**
     * Takes a biobank from FHIR and updates it with current information from the Directory.
     *
     * @param fhirBiobank the biobank to update.
     * @return the {@link OperationOutcome} from the FHIR server update
     */
    OperationOutcome updateBiobankIfNecessary(Organization fhirBiobank) {
        return Option.ofOptional(Main.BBMRI_ERIC_IDENTIFIER.apply(fhirBiobank))
                        .toEither(missigIdentifierOperationOutcome())
                        .flatMap(directoryApi::fetchBiobank)
                        .map(dirBiobank -> new BiobankTuple(fhirBiobank, dirBiobank))
                        .map(UPDATE_BIOBANK_NAME)
                        .filterOrElse(BiobankTuple::hasChanged, tuple -> noUpdateNecessaryOperationOutcome())
                        .map(tuple -> fhirApi.updateResource(tuple.fhirBiobank))
                        .fold(Function.identity(), Function.identity());
    }


    private static class BiobankTuple {

        private Organization fhirBiobank;
        private Organization fhirBiobankCopy;
        private Biobank dirBiobank;

        private BiobankTuple(Organization fhirBiobank,Biobank dirBiobank) {
            this.fhirBiobank = fhirBiobank;
            this.fhirBiobankCopy = fhirBiobank.copy();
            this.dirBiobank = dirBiobank;
        }

        private boolean hasChanged(){
            return !fhirBiobank.equalsDeep(fhirBiobankCopy);
        }
    }

    public static void main(String[] args) throws IOException {

        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));
        FhirApi fhirApi = new FhirApi(client);

        DirectoryApi dirApi = DirectoryApi.createWithLogin(HttpClients.createDefault(),"https://molgenis39.gcc.rug.nl",args[0],args[1]);

/*        Either<OperationOutcome, Biobank> biobank = dirApi.fetchBiobank("bbmri-eric:ID:de_12345");*/

           Sync sync = new Sync(fhirApi,dirApi);

           List<OperationOutcome> operationOutcomes = sync.updateBiobanksIfNecessary();

                  System.out.println(operationOutcomes.stream()
                          .map(o -> o.getIssueFirstRep().getSeverity()+" "+o.getIssueFirstRep().getDiagnostics())
                          .collect(Collectors.toList()));

    }
}
