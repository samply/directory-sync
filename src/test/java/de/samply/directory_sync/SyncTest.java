package de.samply.directory_sync;

import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.fhir.FhirApi;
import io.vavr.control.Either;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncTest {

    @Mock
    private FhirApi fhirApi;

    @Mock
    private DirectoryApi directoryApi;


    private Sync sync;


    @BeforeEach
    void setUp() {
        sync = new Sync(fhirApi,directoryApi);
    }


    @Test
    void testUpdateBiobanksIfIfNecessary_emptyBundle() {
        Bundle bundle = new Bundle();
        when(fhirApi.listAllBiobanks()).thenReturn(bundle);

        List<OperationOutcome> operationOutcome = sync.updateBiobanksIfNecessary();

        assertTrue(operationOutcome.isEmpty());
    }


    @Test
    void testUpdateBiobanksIfIfNecessary_noProfile() {
        Organization biobank = new Organization();
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(biobank);
        when(fhirApi.listAllBiobanks()).thenReturn(bundle);

        List<OperationOutcome> outcome = sync.updateBiobanksIfNecessary();

        assertTrue(outcome.isEmpty());
    }

    @Test
    void testUpdateBiobanksIfIfNecessary() {
        Organization biobank = new Organization();
        biobank.getMeta().addProfile("https://fhir.bbmri.de/StructureDefinition/Biobank");
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(biobank);
        when(fhirApi.listAllBiobanks()).thenReturn(bundle);
        OperationOutcome expected = new OperationOutcome();
        Sync localSync = spy(sync);
        when(localSync.updateBiobankIfNecessary(biobank)).thenReturn(expected);

        List<OperationOutcome> actual = localSync.updateBiobanksIfNecessary();

        assertEquals(Collections.singletonList(expected),actual);
    }

    @Test
    void testUpdateBiobankIfNecessary_noIdentifier() {
        Organization org = new Organization();

        OperationOutcome operationOutcome = sync.updateBiobankIfNecessary(org);

        assertEquals("No BBMRI Identifier for Organization",operationOutcome.getIssueFirstRep().getDiagnostics());
    }

    @Test
    void testUpdateBiobankIfNecessary_noDirectoryResponse() {
        Organization org = new Organization().addIdentifier(createBbmriIdentifier("test"));
        OperationOutcome expected = new OperationOutcome();
        when(directoryApi.fetchBiobank("test")).thenReturn(Either.left(expected));

        OperationOutcome actual = sync.updateBiobankIfNecessary(org);

        assertEquals(expected,actual);
    }

    @Test
    void testUpdateBiobankIfNecessary_noUpdateNecessary() {
        Organization org = new Organization().addIdentifier(createBbmriIdentifier("test"));
        Biobank biobank = new Biobank();
        when(directoryApi.fetchBiobank("test")).thenReturn(Either.right(biobank));

        OperationOutcome actual = sync.updateBiobankIfNecessary(org);

        assertEquals("No Update necessary",actual.getIssueFirstRep().getDiagnostics());
    }

    @Test
    void testUpdateBiobankIfNecessary_updateName() {
        Organization org = new Organization().addIdentifier(createBbmriIdentifier("test"));
        Biobank biobank = new Biobank();
        biobank.setName("target");
        when(directoryApi.fetchBiobank("test")).thenReturn(Either.right(biobank));
        OperationOutcome expected = new OperationOutcome();
        when(fhirApi.updateResource(org)).thenReturn(expected);

        OperationOutcome actual = sync.updateBiobankIfNecessary(org);

        assertEquals(expected,actual);
        assertEquals("target",org.getName());
    }

    private static Identifier createBbmriIdentifier(String value) {
        Identifier identifier = new Identifier();
        identifier.setSystem("http://www.bbmri-eric.eu/").setValue(value);
        return identifier;
    }
}