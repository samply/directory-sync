package de.samply.directory_sync.fhir;

import static java.util.Collections.emptyList;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides convenience methods for selected FHIR operations.
 */
public class FhirApi {

  private static final Logger logger = LoggerFactory.getLogger(FhirApi.class);

  public static Optional<BbmriEricId> bbmriEricId(Organization collection) {
    return collection.getIdentifier().stream()
        .filter(i -> "http://www.bbmri-eric.eu/".equals(i.getSystem()))
        .findFirst().map(Identifier::getValue).flatMap(BbmriEricId::valueOf);
  }

  private final IGenericClient fhirClient;

  public FhirApi(IGenericClient fhirClient) {
    this.fhirClient = fhirClient;
  }

  public OperationOutcome updateResource(IBaseResource theResource) {
    try {
      return (OperationOutcome) fhirClient.update().resource(theResource)
          .prefer(PreferReturnEnum.OPERATION_OUTCOME).execute().getOperationOutcome();
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setDiagnostics(e.getMessage());
      return outcome;
    }
  }

  public Either<String, Void> createResource(IBaseResource resource) {
    try {
      MethodOutcome outcome = fhirClient.create().resource(resource).encodedJson().execute();
      if (outcome.getCreated()) {
        return Either.right(null);
      } else {
        return Either.left("error while creating a resource");
      }
    } catch (Exception e) {
      return Either.left(e.getMessage());
    }
  }

  /**
   * Lists all biobanks in form of Organization resources.
   *
   * @return either a list of biobanks or an operation outcome
   */
  public Either<OperationOutcome, List<Organization>> listAllBiobanks() {
    try {
      return Either.right(((Bundle) fhirClient.search().forResource(Organization.class)
          .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute())
          .getEntry().stream()
          .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
          .map(e -> (Organization) e.getResource())
          .filter(o -> o.getMeta().hasProfile("https://fhir.bbmri.de/StructureDefinition/Biobank"))
          .collect(Collectors.toList()));
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }
  }

  /**
   * Lists all Organizations of type Collection in the Store
   *
   * @return
   */
  public Either<OperationOutcome, List<Organization>> listAllCollections() {
    try {
      return Either.right(((Bundle) fhirClient.search().forResource(Organization.class)
          .withProfile("https://fhir.bbmri.de/StructureDefinition/Collection").execute())
          .getEntry().stream()
          .filter(e -> e.getResource().getResourceType() == ResourceType.Organization)
          .map(e -> (Organization) e.getResource())
          .filter(
              o -> o.getMeta().hasProfile("https://fhir.bbmri.de/StructureDefinition/Collection"))
          .collect(Collectors.toList()));
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }
  }

  /**
   * Checks whether a resource of {@code type} and canonical {@code uri} exists.
   *
   * @param type the resource type
   * @param uri  the canonical URI
   * @return a Right with {@code true} if the resource exists or a Left in case of an error
   */
  public Either<String, Boolean> resourceExists(Class<? extends IBaseResource> type, String uri) {
    logger.debug("Check whether {} with canonical URI {} exists.", type.getSimpleName(), uri);
    return Either.tryGet(() -> resourceQuery(type, uri).execute())
        .mapLeft(Exception::getMessage)
        .map(bundle -> bundle.getTotal() == 1);
  }

  /**
   * Executes the Measure with the given canonical URL.
   *
   * @param url canonical URL of the Measure to be executed
   * @return MeasureReport or OperationOutcome in case of error.
   */
  Either<OperationOutcome, MeasureReport> evaluateMeasure(String url) {
    // Create the input parameters to pass to the server
    Parameters inParams = new Parameters();
    inParams.addParameter().setName("periodStart").setValue(new DateType("1900"));
    inParams.addParameter().setName("periodEnd").setValue(new DateType("2100"));
    inParams.addParameter().setName("measure").setValue(new StringType(url));

    try {
      Parameters outParams = fhirClient
          .operation()
          .onType(Measure.class)
          .named("$evaluate-measure")
          .withParameters(inParams)
          .useHttpGet()
          .execute();

      return Either.right((MeasureReport) outParams.getParameter().get(0).getResource());
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }

  }

  /**
   * Loads the Organization resource for each of the FHIR ids given.
   *
   * @param ids logical ids of the Organization resources to load
   * @return List of Organization Resources or OperationOutcome in case of failure.
   */
  Either<OperationOutcome, List<Organization>> fetchCollections(Set<String> ids) {
    if (ids.isEmpty()) {
      return Either.right(emptyList());
    }
    try {
      Bundle response = (Bundle) fhirClient.search().forResource(Organization.class)
          .where(Organization.RES_ID.exactly().codes(ids)).execute();

      return Either.right(response.getEntry().stream()
          .filter(e -> ResourceType.Organization == e.getResource().getResourceType())
          .map(e -> (Organization) e.getResource())
          .collect(Collectors.toList()));
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }
  }

  /**
   * Counts the Specimen resources available.
   *
   * @return Count of Specimen Resources or OperationOutcome in case of failure.
   */
  Either<Object, Integer> fetchSpecimenCount() {
    try {
      Bundle response = (Bundle) fhirClient.search().forResource(Specimen.class).execute();

      return Either.right(response.getEntry().stream()
          .filter(e -> ResourceType.Specimen == e.getResource().getResourceType())
          .map(e -> (Specimen) e.getResource())
          .collect(Collectors.toList()).size());
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }
  }
}
