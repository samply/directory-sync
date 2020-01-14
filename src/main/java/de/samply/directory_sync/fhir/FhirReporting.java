package de.samply.directory_sync.fhir;

import io.vavr.control.Either;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;

import java.util.List;
import java.util.Map;
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
        return fhirApi.evaluateMeasure("https://fhir.bbmri.de/Measure/size")
                .flatMap(report -> {
                    Map<String, Integer> counts = extractStratifierCounts(report);
                    return fhirApi.fetchCollections(counts.keySet())
                            .map(collections -> mapToCounts(counts, collections));
                });
    }
}
