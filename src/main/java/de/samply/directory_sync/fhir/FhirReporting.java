package de.samply.directory_sync.fhir;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.io.ByteStreams;
import de.samply.directory_sync.Util;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.Tuple;
import io.vavr.control.Either;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.MeasureReport.StratifierGroupComponent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides functionality related to FHIR MeasureReports.
 */
public class FhirReporting {

  private static final Logger logger = LoggerFactory.getLogger(FhirReporting.class);

  private static final String LIBRARY_URI = "https://fhir.bbmri.de/Library/collection-size";
  private static final String MEASURE_URI = "https://fhir.bbmri.de/Measure/collection-size";

  private final FhirContext fhirContext;
  private final FhirApi fhirApi;

  public FhirReporting(FhirContext fhirContext, FhirApi fhirApi) {
    this.fhirContext = Objects.requireNonNull(fhirContext);
    this.fhirApi = Objects.requireNonNull(fhirApi);
  }

  /**
   * The returned map key is an optional FHIR logical ID. The empty case encompasses all Specimen
   * which are not assigned to a Collection.
   */
  private static Map<Optional<String>, Integer> extractStratifierCounts(MeasureReport report) {
    return report.getGroupFirstRep().getStratifierFirstRep().getStratum().stream()
        .collect(Collectors.toMap(FhirReporting::extractFhirId,
            stratum -> stratum.getPopulationFirstRep().getCount(),
            Integer::sum));
  }

  private static Optional<String> extractFhirId(StratifierGroupComponent stratum) {
    String[] parts = stratum.getValue().getText().split("/");
    return parts.length == 2 ? Optional.of(parts[1]) : empty();
  }

  /**
   * Maps the logical FHIR ID keys of {@code counts} to BBMRI-ERIC ID keys using
   * {@code collections}.
   *
   * @param counts      map from FHIR logical ID to counts
   * @param collections list of Organization resources to use for resolving the BBMRI-ERIC ID's
   * @return a map of BBMRI_ERIC ID to counts
   */
  private static Map<BbmriEricId, Integer> resolveBbmriEricIds(Map<String, Integer> counts,
      List<Organization> collections) {
    return collections.stream()
        .map(c -> Tuple.of(FhirApi.bbmriEricId(c), counts.get(c.getIdElement().getIdPart())))
        .filter(t -> t._1.isPresent())
        .filter(t -> t._2 != null)
        .collect(Collectors.toMap(t -> t._1.get(), t -> t._2, Integer::sum));
  }

  /**
   * Tries to create Library and Measure resources if not present on the FHIR server.
   *
   * @return either an error or nothing
   */
  public Either<String, Void> initLibrary() {
    return fhirApi.resourceExists(Library.class, LIBRARY_URI)
        .flatMap(exists -> exists
            ? Either.right(null)
            : slurp("CollectionSize.Library.json")
                .flatMap(s -> parseResource(Library.class, s))
                .flatMap(this::appendCql)
                .flatMap(fhirApi::createResource));
  }

  public Either<String, Void> initMeasure() {
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
    return slurp("CollectionSize.cql").map(cql -> {
      library.getContentFirstRep().setContentType("text/cql");
      library.getContentFirstRep().setData(cql.getBytes(UTF_8));
      return library;
    });
  }

  /**
   * Returns collection sample counts indexed by BBMRI-ERIC identifier.
   * <p>
   * Executes the <a href="https://fhir.bbmri.de/Measure/collection-size">collection-size</a>
   * measure.
   * <p>
   * In case all samples are unassigned, meaning the stratum code has text {@literal null} and only
   * one collection exists, all that samples are assigned to this single collection.
   *
   * @return collection sample counts indexed by BBMRI-ERIC identifier or OperationOutcome
   * indicating an error
   */
  public Either<OperationOutcome, Map<BbmriEricId, Integer>> fetchCollectionSizes() {
    return fhirApi.evaluateMeasure(MEASURE_URI)
        .map(FhirReporting::extractStratifierCounts)
        .flatMap(counts -> {
          if (counts.size() == 1 && counts.containsKey(Optional.<String>empty())) {
            return fhirApi.listAllCollections()
                .map(collections -> {
                  if (collections.size() == 1) {
                    return FhirApi.bbmriEricId(collections.get(0))
                        .map(ericId -> Util.mapOf(ericId, counts.get(Optional.<String>empty())))
                        .orElseGet(Util::mapOf);
                  } else {
                    return Util.mapOf();
                  }
                });
          } else {
            return fhirApi.fetchCollections(filterPresents(counts.keySet()))
                .map(collections -> resolveBbmriEricIds(filterPresents(counts), collections));
          }
        });
  }

  private static <T> Set<T> filterPresents(Set<Optional<T>> optionals) {
    return optionals.stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private static <K, V> Map<K, V> filterPresents(Map<Optional<K>, V> optionals) {
    return filterPresents(optionals.keySet()).stream()
        .collect(Collectors.toMap(Function.identity(), k -> optionals.get(Optional.of(k))));
  }
}
