package de.samply.directory_sync.fhir;

import io.vavr.control.Either;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Provides functionality releated to FHIR MeasureReports
 */
public class FhirReporting {

    private final FhirApi fhirApi;

    public FhirReporting(FhirApi fhirApi) {
        this.fhirApi = fhirApi;
    }

    private static Map<String, Integer> extractStratifierCounts(MeasureReport report) {
        return report.getGroupFirstRep().getStratifierFirstRep().getStratum().stream()
                .filter(stratum -> 2 == stratum.getValue().getText().split("/").length)
                .collect(Collectors.toMap(stratum -> stratum.getValue().getText().split("/")[1],
                        stratum -> stratum.getPopulationFirstRep().getCount()));
    }

    private static Map<String, Integer> mapToCounts(Map<String, Integer> counts, List<Organization> collections) {
        return collections.stream()
                .filter(o -> FhirApi.BBMRI_ERIC_IDENTIFIER.apply(o).isPresent())
                .filter(o -> counts.containsKey(o.getIdElement().getIdPart()))
                .collect(Collectors.toMap(o -> FhirApi.BBMRI_ERIC_IDENTIFIER.apply(o).get(),
                        o -> counts.get(o.getIdElement().getIdPart()), Integer::sum));
    }

    /**
     * Returns collection sample counts indexed by BBMRI-ERIC identifier.
     * <p>
     * Executes the `https://fhir.bbmri.de/Measure/size` measure for all collections on the FHIR server.
     *
     * @return collection sample counts indexed by BBMRI-ERIC identifier or OperationOutcome indicating an error
     */
    public Either<OperationOutcome, Map<String, Integer>> fetchCollectionSizes() {
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
                String biobankIdentifier = extractIdentifierFromOrganization(biobanks.get(0));
                String collectionIdentifier = extractIdentifierFromOrganization(collections.get(0));
                if (isValidDirectoryBiobankIdentifier(biobankIdentifier) && isValidDirectoryCollectionIdentifier(biobankIdentifier, collectionIdentifier)) {
                    Either<Object, Integer> specimenCountOutcome = fhirApi.fetchSpecimenCount();
                    if (specimenCountOutcome.isRight()) {
                        Integer specimenCount = specimenCountOutcome.get();
                        Map<String, Integer> counts = new HashMap<String, Integer>();
                        counts.put(collectionIdentifier, specimenCount);
                        return Either.right(counts);
                    }
                }
            }
        }

        // For the more general case, call an external size-measuring service.
        return fhirApi.evaluateMeasure("https://fhir.bbmri.de/Measure/size")
                .flatMap(report -> {
                    Map<String, Integer> counts = extractStratifierCounts(report);
                    return fhirApi.fetchCollections(counts.keySet())
                            .map(collections -> mapToCounts(counts, collections));
                });
    }

    private String extractIdentifierFromOrganization(Organization organization) {
        List<Identifier> identifier = organization.getIdentifier();
        Identifier identifier0 = identifier.get(0);
        String value = identifier0.getValue();

        return value;
    }

    private boolean isValidDirectoryBiobankIdentifier(String biobankIdentifier) {
        if (! biobankIdentifier.startsWith("bbmri-eric:"))
            return false;
        String[] parts = biobankIdentifier.split(":");
        if (parts.length != 3)
            return false;
        if ( ! parts[1].equals("ID"))
            return false;
        return true;
    }

    private boolean isValidDirectoryCollectionIdentifier(String biobankIdentifier, String collectionIdentifier) {
        if (! collectionIdentifier.startsWith(biobankIdentifier + ":"))
            return false;
        String[] parts = collectionIdentifier.split(":");
        if (parts.length != 5)
            return false;
        if ( ! parts[3].equals("collection"))
            return false;
        return true;
    }
}
