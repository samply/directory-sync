package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.DirectoryService;
import de.samply.directory_sync.directory.MergeDirectoryCollectionGetToDirectoryCollectionPut;
import de.samply.directory_sync.directory.model.BbmriEricId;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.directory.model.DirectoryCollectionGet;
import de.samply.directory_sync.directory.model.DirectoryCollectionPut;
import de.samply.directory_sync.directory.model.DirectoryCollectionPut;
import de.samply.directory_sync.fhir.FhirApi;
import de.samply.directory_sync.fhir.FhirReporting;
import de.samply.directory_sync.fhir.model.FhirCollection;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.Map;
import java.util.Objects;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.INFORMATION;

/**
 * Provides functionality to synchronize MOLGENIS directory and FHIR server in both directions.
 */
public class Sync {
  private static final Logger logger = LoggerFactory.getLogger(Sync.class);

    private static final Function<BiobankTuple, BiobankTuple> UPDATE_BIOBANK_NAME = t -> {
        t.fhirBiobank.setName(t.dirBiobank.getName());
        return t;
    };

    private final FhirApi fhirApi;
    private final FhirReporting fhirReporting;
    private final DirectoryApi directoryApi;
    private final DirectoryService directoryService;

    public Sync(FhirApi fhirApi, FhirReporting fhirReporting, DirectoryApi directoryApi,
        DirectoryService directoryService) {
        this.fhirApi = fhirApi;
        this.fhirReporting = fhirReporting;
        this.directoryApi = directoryApi;
        this.directoryService = directoryService;
    }

    public static void main(String[] args) {
        FhirContext fhirContext = FhirContext.forR4();
        FhirApi fhirApi = new FhirApi(fhirContext.newRestfulGenericClient(args[0]));
        FhirReporting fhirReporting = new FhirReporting(fhirContext, fhirApi);
        Sync sync = new Sync(fhirApi, fhirReporting, null, null);
        Either<String, Void> result = sync.initResources();
        System.out.println("result = " + result);
        Either<OperationOutcome, Map<BbmriEricId, Integer>> collectionSizes = fhirReporting.fetchCollectionSizes();
        System.out.println("collectionSizes = " + collectionSizes);
    }

    public Either<String, Void> initResources() {
        return fhirReporting.initLibrary().flatMap(_void -> fhirReporting.initMeasure());
    }

    private static OperationOutcome missingIdentifierOperationOutcome() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(ERROR).setDiagnostics("No BBMRI Identifier for Organization");
        return outcome;
    }

    private static OperationOutcome noUpdateNecessaryOperationOutcome() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(INFORMATION).setDiagnostics("No Update " +
                "necessary");
        return outcome;
    }

    /**
     * Updates all biobanks from the FHIR server with information from the Directory.
     *
     * @return the individual {@link OperationOutcome}s from each update
     */
    public List<OperationOutcome> updateAllBiobanksOnFhirServerIfNecessary() {
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
        return Option.ofOptional(FhirApi.bbmriEricId(fhirBiobank))
                .toEither(missingIdentifierOperationOutcome())
                .flatMap(directoryApi::fetchBiobank)
                .map(dirBiobank -> new BiobankTuple(fhirBiobank, dirBiobank))
                .map(UPDATE_BIOBANK_NAME)
                .filterOrElse(BiobankTuple::hasChanged, tuple -> noUpdateNecessaryOperationOutcome())
                .map(tuple -> fhirApi.updateResource(tuple.fhirBiobank))
                .fold(Function.identity(), Function.identity());
    }

    /**
     * Updates collection sample count information for all collections that exist with the same
     * BBMRI-ERIC identifier on the FHIR server and in the directory.
     *
     * @return the outcome of the directory update operation.
     */
    public List<OperationOutcome> syncCollectionSizesToDirectory() {
        return fhirReporting.fetchCollectionSizes()
                .map(directoryService::updateCollectionSizes)
                .fold(Collections::singletonList, Function.identity());
    }

    public List<OperationOutcome> sendUpdatesToDirectory(String defaultCollectionId, String country) {

        // Testing stuff out...
        // This is a multi step process:
        // 1. Fetch a list of collections objects from the FHIR store. These contain aggregated
        //    information over all specimens in the collections.
        // 2. Convert the FHIR collection objects into Directory collection PUT DTOs. Copy
        //    over avaialble information from FHIT, converting where necessary.
        // 3. Using the collection IDs found in the FHIR store, send queries to the Directory
        //    and fetch back the relevant GET collections. If any of the collection IDs cannot be
        //    found, this ie a breaking error.
        // 4. Transfer data from the Directory GET collections to the corresponding Directory PUT
        //    collections.
        // 5. Push the new information back to the Directory.
        DirectoryCollectionPut directoryCollectionPut = null;
        try {
            Either<OperationOutcome, List<FhirCollection>> fhirCollectionOutcomes = fhirReporting.fetchFhirCollections(defaultCollectionId);
            if (fhirCollectionOutcomes.isRight()) {
                directoryCollectionPut = FhirCollectionToDirectoryCollectionPutConverter.convert(fhirCollectionOutcomes.get());
                if (directoryCollectionPut == null) {
                    OperationOutcome outcome = new OperationOutcome();
                    outcome.addIssue().setSeverity(ERROR).setDiagnostics("Problem converting FHIR attributes to Directory attributes");
                    return Collections.singletonList(outcome);
                }
                List<String> collectionIds = directoryCollectionPut.getCollectionIds();
                Either<OperationOutcome, DirectoryCollectionGet> directoryCollectionGetOutcomes = directoryService.fetchDirectoryCollectionGetOutcomes(country, collectionIds);
                if (directoryCollectionGetOutcomes.isRight()) {
                    if (MergeDirectoryCollectionGetToDirectoryCollectionPut.merge(directoryCollectionGetOutcomes.get(), directoryCollectionPut) == null) {
                        OperationOutcome outcome = new OperationOutcome();
                        outcome.addIssue().setSeverity(ERROR).setDiagnostics("Problem merging Directory GET attributes to Directory PUT attributes");
                        return Collections.singletonList(outcome);
                    }
                    logger.info("sendUpdatesToDirectory: ");
                    List<OperationOutcome> result = directoryService.updateEntities(country, directoryCollectionPut); // Update Directory
                    logger.info("sendUpdatesToDirectory: ");
                    return(result);
            } else {
                    OperationOutcome outcome = new OperationOutcome();
                    outcome.addIssue().setSeverity(ERROR).setDiagnostics("Problem getting collections from Directory");
                    return Collections.singletonList(outcome);
                }
            } else {
                OperationOutcome outcome = new OperationOutcome();
                outcome.addIssue().setSeverity(ERROR).setDiagnostics("Problem getting collections from FHIR store");
                return Collections.singletonList(outcome);
            }
        } catch(Exception e) {
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue().setSeverity(ERROR).setDiagnostics(Util.traceFromException(e));
            return Collections.singletonList(outcome);
        }
    }

    private static class BiobankTuple {

        private final Organization fhirBiobank;
        private final Organization fhirBiobankCopy;
        private final Biobank dirBiobank;

        private BiobankTuple(Organization fhirBiobank, Biobank dirBiobank) {
            this.fhirBiobank = Objects.requireNonNull(fhirBiobank);
            this.fhirBiobankCopy = fhirBiobank.copy();
            this.dirBiobank = Objects.requireNonNull(dirBiobank);
        }

        private boolean hasChanged() {
            return !fhirBiobank.equalsDeep(fhirBiobankCopy);
        }
    }
}
