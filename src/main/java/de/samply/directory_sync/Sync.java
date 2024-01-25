package de.samply.directory_sync;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import de.samply.directory_sync.directory.CreateFactTablesFromStarModelInputData;
import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.DirectoryService;
import de.samply.directory_sync.directory.MergeDirectoryCollectionGetToDirectoryCollectionPut;
import de.samply.directory_sync.directory.model.BbmriEricId;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.directory.model.DirectoryCollectionGet;
import de.samply.directory_sync.directory.model.DirectoryCollectionPut;
import de.samply.directory_sync.fhir.FhirApi;
import de.samply.directory_sync.fhir.FhirReporting;
import de.samply.directory_sync.fhir.model.FhirCollection;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

     /**
     * Take information from the FHIR store and send aggregated updates to the Directory.
     * 
     * This is a multi step process:
     *  1. Fetch a list of collections objects from the FHIR store. These contain aggregated
     *     information over all specimens in the collections.
     *  2. Convert the FHIR collection objects into Directory collection PUT DTOs. Copy
     *     over avaialble information from FHIR, converting where necessary.
     *  3. Using the collection IDs found in the FHIR store, send queries to the Directory
     *     and fetch back the relevant GET collections. If any of the collection IDs cannot be
     *     found, this ie a breaking error.
     *  4. Transfer data from the Directory GET collections to the corresponding Directory PUT
     *     collections.
     *  5. Push the new information back to the Directory.
     * 
     * @param defaultCollectionId The default collection ID to use for fetching collections from the FHIR store.
     * @param country The country to which the updates are targeted.
     * @return A list of OperationOutcome objects indicating the outcome of the update operation.
     */
    public List<OperationOutcome> sendUpdatesToDirectory(String defaultCollectionId) {
        try {
            BbmriEricId defaultBbmriEricCollectionId = BbmriEricId
                .valueOf(defaultCollectionId)
                .orElse(null);

            Either<OperationOutcome, List<FhirCollection>> fhirCollectionOutcomes = fhirReporting.fetchFhirCollections(defaultBbmriEricCollectionId);
            if (fhirCollectionOutcomes.isLeft())
                return createErrorOutcome("Problem getting collections from FHIR store, " + errorMessageFromOperationOutcome(fhirCollectionOutcomes.getLeft()));

            DirectoryCollectionPut directoryCollectionPut = FhirCollectionToDirectoryCollectionPutConverter.convert(fhirCollectionOutcomes.get());
            if (directoryCollectionPut == null) 
                return createErrorOutcome("Problem converting FHIR attributes to Directory attributes");
    
            List<String> collectionIds = directoryCollectionPut.getCollectionIds();
            String countryCode = directoryCollectionPut.getCountryCode();
            Either<OperationOutcome, DirectoryCollectionGet> directoryCollectionGetOutcomes = directoryService.fetchDirectoryCollectionGetOutcomes(countryCode, collectionIds);
            if (directoryCollectionGetOutcomes.isLeft())
                return createErrorOutcome("Problem getting collections from Directory, " + errorMessageFromOperationOutcome(directoryCollectionGetOutcomes.getLeft()));

            DirectoryCollectionGet directoryCollectionGet = directoryCollectionGetOutcomes.get();
            if (!MergeDirectoryCollectionGetToDirectoryCollectionPut.merge(directoryCollectionGet, directoryCollectionPut))
                return createErrorOutcome("Problem merging Directory GET attributes to Directory PUT attributes");

            return directoryService.updateEntities(directoryCollectionPut);
        } catch (Exception e) {
            return createErrorOutcome("sendUpdatesToDirectory - unexpected error: " + Util.traceFromException(e));
        }
    }

    /**
     * Sends updates for Star Model data to the Directory service, based on FHIR store information.
     * This method fetches Star Model input data from the FHIR store, generates star model fact tables,
     * performs diagnosis corrections, and then updates the Directory service with the prepared data.
     * <p>
     * The method handles errors by returning a list of OperationOutcome objects describing the issues.
     * </p>
     *
     * @param defaultCollectionId The default BBMRI-ERIC collection ID for fetching data from the FHIR store.
     * @param minDonors The minimum number of donors required for a fact to be included in the star model output.
     * @return A list of OperationOutcome objects indicating the outcome of the star model updates.
     *
     * @throws IllegalArgumentException if the defaultCollectionId is not a valid BbmriEricId.
     */
    public List<OperationOutcome> sendStarModelUpdatesToDirectory(String defaultCollectionId, int minDonors) {
        try {
            BbmriEricId defaultBbmriEricCollectionId = BbmriEricId
                .valueOf(defaultCollectionId)
                .orElse(null);

            // Pull data from the FHIR store and save it in a format suitable for generating
            // star model hypercubes.
            Either<OperationOutcome, StarModelData> starModelInputDataOutcome = fhirReporting.fetchStarModelInputData(defaultBbmriEricCollectionId);
            if (starModelInputDataOutcome.isLeft())
                return createErrorOutcome("Problem getting star model information from FHIR store, " + errorMessageFromOperationOutcome(starModelInputDataOutcome.getLeft()));

            StarModelData starModelInputData = starModelInputDataOutcome.get();

            // Hpercubes containing less than the minimum number of donors will not be
            // included in the star model output.
            starModelInputData.setMinDonors(minDonors);

            // Take the patient list and the specimen list from starModelInputData and
            // use them to generate the star model fact tables.
            CreateFactTablesFromStarModelInputData.createFactTables(starModelInputData);

            // Check all of the ICD 10 values to see if they are known to the Directory
            // and deal with them appropriately if not.
            directoryService.collectStarModelDiagnosisCorrections(starModelInputData);
            starModelInputData.implementDiagnosisCorrections();

            // Send fact tables to Direcory. Return some kind of results count or whatever
            List<OperationOutcome> starModelUpdateOutcome = directoryService.updateStarModel(starModelInputData);
            return starModelUpdateOutcome;
        } catch (Exception e) {
            return createErrorOutcome("sendUpdatesToDirectory - unexpected error: " + Util.traceFromException(e));
        }
    }
    
    private String errorMessageFromOperationOutcome(OperationOutcome operationOutcome) {
        return operationOutcome.getIssue().stream()
                .filter(issue -> issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR || issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
                .map(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
                .collect(Collectors.joining("\n"));
    }
    
    private List<OperationOutcome> createErrorOutcome(String diagnostics) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(ERROR).setDiagnostics(diagnostics);
        return Collections.singletonList(outcome);
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
