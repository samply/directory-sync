package de.samply.directory_sync.fhir;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.io.ByteStreams;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functionality releated to FHIR MeasureReports
 */
public class FhirReporting {

    private static final Logger logger = LoggerFactory.getLogger(FhirReporting.class);

    private static final String LIBRARY_URI = "https://fhir.bbmri.de/Library/collection-size";
    private static final String MEASURE_URI = "https://fhir.bbmri.de/Measure/collection-size";

    private final FhirContext fhirContext;
    private final FhirApi fhirApi;

    public FhirReporting(FhirContext fhirContext, FhirApi fhirApi) {
        this.fhirContext = fhirContext;
        this.fhirApi = fhirApi;
    }

    private static Map<String, Integer> extractStratifierCounts(MeasureReport report) {
        return report.getGroupFirstRep().getStratifierFirstRep().getStratum().stream()
                .filter(stratum -> 2 == stratum.getValue().getText().split("/").length)
                .collect(Collectors.toMap(stratum -> stratum.getValue().getText().split("/")[1],
                        stratum -> stratum.getPopulationFirstRep().getCount()));
    }

    /**
     * Maps the logical FHIR ID keys of {@code counts} to BBMRI-ERIC ID keys unsing {@code collections}.
     *
     * @param counts map from FHIR logical ID to counts
     * @param collections list of Collections to use for resolving the BBMRI-ERIC ID's
     * @return a map of BBMRI_ERIC ID to counts
     */
    private static Map<BbmriEricId, Integer> resolveBbmriEricIds(Map<String, Integer> counts, List<Organization> collections) {
        return collections.stream()
                .filter(c -> FhirApi.bbmriEricId(c).isPresent())
                .filter(c -> counts.containsKey(c.getIdElement().getIdPart()))
                .collect(Collectors.toMap(c -> FhirApi.bbmriEricId(c).get(),
                        o -> counts.get(o.getIdElement().getIdPart()), Integer::sum));
    }

    /**
     * Tries to create Library and Measure resources if not present on the FHIR server.
     *
     * @return either an error or nothing
     */
    private Either<String, Void> initResources() {
        return initLibrary().flatMap(foo -> initMeasure());
    }

    private Either<String, Void> initLibrary() {
        return fhirApi.resourceExists(Library.class, LIBRARY_URI)
            .flatMap(exists -> exists
                ? Either.right(null)
                : slurp("CollectionSize.Library.json")
                    .flatMap(s -> parseResource(Library.class, s))
                    .flatMap(this::appendCql)
                    .flatMap(fhirApi::createResource));
    }

    private Either<String, Void> initMeasure() {
        return fhirApi.resourceExists(Measure.class, MEASURE_URI)
            .flatMap(exists -> exists
                ? Either.right(null)
                : slurp("CollectionSize.Measure.json")
                    .flatMap(s -> parseResource(Measure.class, s))
                    .flatMap(fhirApi::createResource));
    }

    private static Either<String, String> slurp(String name) {
        try (InputStream in = FhirApi.class.getResourceAsStream(name)) {
            if (in == null) {
                logger.error("file `{}` not found in classpath", name);
                return Either.left(format("file `%s` not found in classpath", name));
            } else {
                logger.info("read file `{}` from classpath", name);
                return Either.right(new String(ByteStreams.toByteArray(in), UTF_8));
            }
        } catch (IOException e) {
            logger.error("error while reading the file `{}` from classpath", name, e);
            return Either.left(format("error while reading the file `%s` from classpath", name));
        }
    }

    private <T extends IBaseResource> Either<String, T> parseResource(Class<T> type, String s) {
        IParser parser = fhirContext.newJsonParser();
        try {
            return Either.right(type.cast(parser.parseResource(s)));
        } catch (Exception e) {
            return Either.left(e.getMessage());
        }
    }

    private Either<String, Library> appendCql(Library library) {
        return slurp("query.cql").map(cql -> {
            library.getContentFirstRep().setContentType("text/cql");
            library.getContentFirstRep().setData(cql.getBytes(UTF_8));
            return library;
        });
    }

    /**
     * Returns collection sample counts indexed by BBMRI-ERIC identifier.
     * <p>
     * Executes the `https://fhir.bbmri.de/Measure/collection-size` measure for all collections on the FHIR server.
     *
     * @return collection sample counts indexed by BBMRI-ERIC identifier or OperationOutcome indicating an error
     */
    public Either<OperationOutcome, Map<BbmriEricId, Integer>> fetchCollectionSizes() {
        // First check to see if we have a GBN-style situation, where
        // there are only two Organization resources, one for the biobank
        // and one for the Collection. In this case, we know that all samples
        // belong to the Collection, so we can simply return the total count
        // of samples, without checking to see which Collection they belong to.
        Either<OperationOutcome, List<Organization>> biobanksOutcome = fhirApi.listAllBiobanks();
        Either<OperationOutcome, List<Organization>> collectionsOutcome = fhirApi.listAllCollections();
        if (biobanksOutcome != null && biobanksOutcome.isRight() && collectionsOutcome != null && collectionsOutcome.isRight()) {
            List<Organization> biobanks = biobanksOutcome.get();
            List<Organization> collections = collectionsOutcome.get();
            if (biobanks.size() == 1 && collections.size() == 1) {
                Optional<BbmriEricId> biobankIdentifier = FhirApi.bbmriEricId(biobanks.get(0));
                Optional<BbmriEricId> collectionIdentifier = FhirApi.bbmriEricId(collections.get(0));
                if (biobankIdentifier.isPresent() &&
                    collectionIdentifier.isPresent() &&
                    isValidDirectoryCollectionIdentifier(biobankIdentifier.get(), collectionIdentifier.get())) {
                    Either<Object, Integer> specimenCountOutcome = fhirApi.fetchSpecimenCount();
                    if (specimenCountOutcome.isRight()) {
                        Integer specimenCount = specimenCountOutcome.get();
                        Map<BbmriEricId, Integer> counts = new HashMap<>();
                        counts.put(collectionIdentifier.get(), specimenCount);
                        return Either.right(counts);
                    }
                }
            }
        }

        // For the more general case, call an external size-measuring service.
        return fhirApi.evaluateMeasure(MEASURE_URI)
                .flatMap(report -> {
                    Map<String, Integer> counts = extractStratifierCounts(report);
                    return fhirApi.fetchCollections(counts.keySet())
                            .map(collections -> resolveBbmriEricIds(counts, collections));
                });
    }

    private boolean isValidDirectoryCollectionIdentifier(BbmriEricId biobankIdentifier, BbmriEricId collectionIdentifier) {
        if (! collectionIdentifier.toString().startsWith(biobankIdentifier.toString() + ":"))
            return false;
        String[] parts = collectionIdentifier.toString().split(":");
        if (parts.length != 5)
            return false;
        if ( ! parts[3].equals("collection"))
            return false;
        return true;
    }
}
