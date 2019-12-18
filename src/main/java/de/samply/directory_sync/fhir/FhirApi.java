package de.samply.directory_sync.fhir;

import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;

public class FhirApi {
    private final IGenericClient fhirClient;

    public IGenericClient getFhirClient() {
        return fhirClient;
    }

    public FhirApi(IGenericClient fhirClient) {
        this.fhirClient = fhirClient;
    }

    public OperationOutcome updateResource(IBaseResource theResource) {
        try {
            return (OperationOutcome) fhirClient.update().resource(theResource).prefer(PreferReturnEnum.OPERATION_OUTCOME).execute().getOperationOutcome();
        }catch(Exception e){
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics(e.getMessage());
            return outcome;
        }
    }

    public Bundle listAllBiobanks(){
        return (Bundle) fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute();
    }
}
