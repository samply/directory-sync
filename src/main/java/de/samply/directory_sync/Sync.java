package de.samply.directory_sync;

import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.fhir.FhirApi;
import io.vavr.control.Option;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;

import java.util.function.Function;



public class Sync {

    static Function<BiobankTuple, BiobankTuple> UPDATE_BIOBANK_NAME = t -> {
        t.fhirBiobank.setName(t.dirBiobank.getName());
        return t;
    };
    private FhirApi fhirApi;

    private DirectoryApi directoryApi;

    public Sync(FhirApi fhirApi, DirectoryApi directoryApi) {
        this.fhirApi = fhirApi;
        this.directoryApi = directoryApi;
    }

    static OperationOutcome missigIdentifierOperationOutcome(){
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setDiagnostics("No BBMRI Identifier for Organization");
        return outcome;
    }

    static OperationOutcome noUpdateNecessaryOperationOutcome(){
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.INFORMATION).setDiagnostics("No Update necessary");
        return outcome;
    }

    public FhirApi getFhirApi() {
        return fhirApi;
    }

    public OperationOutcome updateBiobankIfNecessary(Organization o) {
        return Option.ofOptional(Main.BBMRI_ERIC_IDENTIFIER.apply(o))
                        .toEither(missigIdentifierOperationOutcome())
                        .flatMap(directoryApi::fetchBiobank)
                        .map(b -> new BiobankTuple(o, b))
                        .map(UPDATE_BIOBANK_NAME)
                        .filterOrElse(BiobankTuple::hasChanged, t -> noUpdateNecessaryOperationOutcome())
                        .map(t -> fhirApi.updateResource(t.fhirBiobank))
                        .fold(Function.identity(), Function.identity());
    }

    private static class BiobankTuple {

        private Organization fhirBiobank;
        private Organization fhirBiobankCopy;
        private Biobank dirBiobank;

        private BiobankTuple(Organization fhirBiobank,Biobank dirBiobank) {
            this.fhirBiobank = fhirBiobank;
            this.fhirBiobankCopy = fhirBiobank.copy();
            this.dirBiobank = dirBiobank;
        }

        private boolean hasChanged(){
            return !fhirBiobank.equalsDeep(fhirBiobankCopy);
        }
    }
}
