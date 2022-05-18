package de.samply.directory_sync.fhir;

import static ca.uhn.fhir.rest.api.PreferReturnEnum.OPERATION_OUTCOME;
import static ca.uhn.fhir.rest.api.SummaryEnum.COUNT;
import static java.util.Collections.emptyList;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.ERROR;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IUpdateExecutable;
import ca.uhn.fhir.rest.gclient.UriClientParam;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.MeasureReport;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides convenience methods for selected FHIR operations.
 */
public class FhirApi {

  private static final String BIOBANK_PROFILE_URI = "https://fhir.bbmri.de/StructureDefinition/Biobank";
  private static final String COLLECTION_PROFILE_URI = "https://fhir.bbmri.de/StructureDefinition/Collection";

  private static final Logger logger = LoggerFactory.getLogger(FhirApi.class);

  /**
   * Returns the BBMRI-ERIC identifier of {@code collection} if some valid one could be found.
   *
   * @param collection the Organization resource, possibly containing a BBMRI-ERIC identifier
   * @return the found BBMRI-ERIC identifier or {@link Optional#empty empty}
   */
  public static Optional<BbmriEricId> bbmriEricId(Organization collection) {
    return collection.getIdentifier().stream()
        .filter(i -> "http://www.bbmri-eric.eu/".equals(i.getSystem()))
        .findFirst().map(Identifier::getValue).flatMap(BbmriEricId::valueOf);
  }

  private final IGenericClient fhirClient;

  public FhirApi(IGenericClient fhirClient) {
    this.fhirClient = Objects.requireNonNull(fhirClient);
  }

  public OperationOutcome updateResource(IBaseResource theResource) {
    try {
      return (OperationOutcome) resourceUpdate(theResource).execute().getOperationOutcome();
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(ERROR).setDiagnostics(e.getMessage());
      return outcome;
    }
  }

  private IUpdateExecutable resourceUpdate(IBaseResource theResource) {
    return fhirClient.update().resource(theResource).prefer(OPERATION_OUTCOME);
  }

  public Either<String, Void> createResource(IBaseResource resource) {
    try {
      MethodOutcome outcome = resourceCreate(resource).execute();
      if (outcome.getCreated()) {
        return Either.right(null);
      } else {
        return Either.left("error while creating a resource");
      }
    } catch (Exception e) {
      return Either.left(e.getMessage());
    }
  }

  private ICreateTyped resourceCreate(IBaseResource resource) {
    return fhirClient.create().resource(resource).prefer(OPERATION_OUTCOME);
  }

  /**
   * Lists all Organization resources with the biobank profile.
   *
   * @return either a list of {@link Organization} resources or an {@link OperationOutcome} on *
   * errors
   */
  public Either<OperationOutcome, List<Organization>> listAllBiobanks() {
    return listAllOrganizations(BIOBANK_PROFILE_URI)
        .map(bundle -> extractOrganizations(bundle, BIOBANK_PROFILE_URI));
  }

  private Either<OperationOutcome, Bundle> listAllOrganizations(String profileUri) {
    try {
      return Either.right((Bundle) fhirClient.search().forResource(Organization.class)
          .withProfile(profileUri).execute());
    } catch (Exception e) {
      OperationOutcome outcome = new OperationOutcome();
      outcome.addIssue().setSeverity(ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }
  }

  private static List<Organization> extractOrganizations(Bundle bundle, String profileUrl) {
    return bundle.getEntry().stream()
        .map(BundleEntryComponent::getResource)
        .filter(r -> r.getResourceType() == ResourceType.Organization)
        .filter(r -> r.getMeta().hasProfile(profileUrl))
        .map(r -> (Organization) r)
        .collect(Collectors.toList());
  }

  /**
   * Lists all Organization resources with the collection profile.
   *
   * @return either a list of {@link Organization} resources or an {@link OperationOutcome} on
   * errors
   */
  public Either<OperationOutcome, List<Organization>> listAllCollections() {
    return listAllOrganizations(COLLECTION_PROFILE_URI)
        .map(bundle -> extractOrganizations(bundle, COLLECTION_PROFILE_URI));
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
    try {
      return Either.right(resourceQuery(type, uri).execute().getTotal() == 1);
    } catch (Exception e) {
      return Either.left(e.getMessage());
    }
  }

  private IQuery<Bundle> resourceQuery(Class<? extends IBaseResource> type, String uri) {
    return fhirClient.search().forResource(type)
        .where(new UriClientParam("url").matches().value(uri))
        .summaryMode(COUNT)
        .returnBundle(Bundle.class);
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
      outcome.addIssue().setSeverity(ERROR)
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
      outcome.addIssue().setSeverity(ERROR)
          .setDiagnostics(e.getMessage());
      return Either.left(outcome);
    }
  }
}
