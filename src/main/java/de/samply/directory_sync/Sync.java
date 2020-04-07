package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.fhir.FhirApi;
import de.samply.directory_sync.fhir.FhirReporting;
import io.vavr.control.Option;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides functionality to synchronize MOLGENIS directory and FHIR server in both directions.
 */
public class Sync {

    private static Function<BiobankTuple, BiobankTuple> UPDATE_BIOBANK_NAME = t -> {
        t.fhirBiobank.setName(t.dirBiobank.getName());
        return t;
    };

    private FhirApi fhirApi;

    private FhirReporting fhirReporting;

    private DirectoryApi directoryApi;

    public Sync(FhirApi fhirApi, FhirReporting fhirReporting, DirectoryApi directoryApi) {
        this.fhirApi = fhirApi;
        this.fhirReporting = fhirReporting;
        this.directoryApi = directoryApi;
    }

    private static OperationOutcome missigIdentifierOperationOutcome() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics("No BBMRI Identifier for " +
                "Organization");
        return outcome;
    }

    private static OperationOutcome noUpdateNecessaryOperationOutcome() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION).setDiagnostics("No Update " +
                "necessary");
        return outcome;
    }

    public static void main(String[] args) throws IOException {

        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient("https://blaze.life.uni-leipzig.de/fhir");
        client.registerInterceptor(new LoggingInterceptor(true));
        FhirApi fhirApi = new FhirApi(client);

        DirectoryApi dirApi = DirectoryApi.createWithLogin(HttpClients.createDefault(), "https://molgenis39.gcc.rug" +
                ".nl", args[0], args[1]);

        /*        Either<OperationOutcome, Biobank> biobank = dirApi.fetchBiobank("bbmri-eric:ID:de_12345");*/

        // Sync sync = new Sync(fhirApi, fhirReporting, dirApi);

//           List<OperationOutcome> operationOutcomes = sync.updateBiobanksIfNecessary();
//
//                  System.out.println(operationOutcomes.stream()
//                          .map(o -> o.getIssueFirstRep().getSeverity()+" "+o.getIssueFirstRep().getDiagnostics())
//                          .collect(Collectors.toList()));

        //  sync.updateCollectionSizes();

    }

    /**
     * Updates all biobanks from the FHIR server with information from the Directory.
     *
     * @return the individual {@link OperationOutcome}s from each update
     */
    List<OperationOutcome> updateAllBiobanksOnFhirServerIfNecessary() {
        return fhirApi.listAllBiobanks()
                .map(orgs -> orgs.stream().map(this::updateBiobankOnFhirServerIfNecessary).collect(Collectors.toList()))
                .fold(Collections::singletonList, Function.identity());
    }

    /**
     * Takes a biobank from FHIR and updates it with current information from the Directory.
     *
     * @param fhirBiobank the biobank to update.
     * @return the {@link OperationOutcome} from the FHIR server update
     */
    OperationOutcome updateBiobankOnFhirServerIfNecessary(Organization fhirBiobank) {
        return Option.ofOptional(FhirApi.BBMRI_ERIC_IDENTIFIER.apply(fhirBiobank))
                .toEither(missigIdentifierOperationOutcome())
                .flatMap(directoryApi::fetchBiobank)
                .map(dirBiobank -> new BiobankTuple(fhirBiobank, dirBiobank))
                .map(UPDATE_BIOBANK_NAME)
                .filterOrElse(BiobankTuple::hasChanged, tuple -> noUpdateNecessaryOperationOutcome())
                .map(tuple -> fhirApi.updateResource(tuple.fhirBiobank))
                .fold(Function.identity(), Function.identity());
    }

    /**
     * Updates collection sample count information for all collections that exist with the same BBMRI-ID
     * on the FHIR server and in the directory.
     *
     * @return the outcome of the directory update operation.
     */
    OperationOutcome syncCollectionSizesToDirectory() {
        return fhirReporting.fetchCollectionSizes()
                .map(directoryApi::updateCollectionSizes)
                .fold(Function.identity(), Function.identity());
    }

    private static class BiobankTuple {

        private Organization fhirBiobank;
        private Organization fhirBiobankCopy;
        private Biobank dirBiobank;

        private BiobankTuple(Organization fhirBiobank, Biobank dirBiobank) {
            this.fhirBiobank = fhirBiobank;
            this.fhirBiobankCopy = fhirBiobank.copy();
            this.dirBiobank = dirBiobank;
        }

        private boolean hasChanged() {
            return !fhirBiobank.equalsDeep(fhirBiobankCopy);
        }
    }
}
