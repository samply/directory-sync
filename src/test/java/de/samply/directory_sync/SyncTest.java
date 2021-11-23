package de.samply.directory_sync;

import static de.samply.directory_sync.TestUtil.createBbmriIdentifier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import de.samply.directory_sync.directory.DirectoryApi;
import de.samply.directory_sync.directory.DirectoryService;
import de.samply.directory_sync.directory.model.BbmriEricId;
import de.samply.directory_sync.directory.model.Biobank;
import de.samply.directory_sync.fhir.FhirApi;
import de.samply.directory_sync.fhir.FhirReporting;
import io.vavr.control.Either;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SyncTest {

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  private static final BbmriEricId BBMRI_ERIC_ID = BbmriEricId.valueOf("bbmri-eric:ID:AT_MUG").get();

  @Mock
  private FhirApi fhirApi;

  @Mock
  private FhirReporting fhirReporting;

  @Mock
  private DirectoryApi directoryApi;

  @Mock
  private DirectoryService directoryService;

  @InjectMocks
  private Sync sync;

  @Test
  void testUpdateCollectionSizes() {
    Map<BbmriEricId, Integer> sizes = Collections.singletonMap(BBMRI_ERIC_ID, 165148);
    List<OperationOutcome> outcomes = Collections.singletonList(new OperationOutcome());
    when(fhirReporting.fetchCollectionSizes()).thenReturn(Either.right(sizes));
    when(directoryService.updateCollectionSizes(sizes)).thenReturn(outcomes);

    List<OperationOutcome> result = sync.syncCollectionSizesToDirectory();

    assertEquals(outcomes, result);
  }

  @Test
  void testUpdateBiobanksIfIfNecessary() {
    Organization biobank = new Organization();
    when(fhirApi.listAllBiobanks()).thenReturn(Either.right(Collections.singletonList(biobank)));
    OperationOutcome expected = new OperationOutcome();
    Sync localSync = spy(sync);
    when(localSync.updateBiobankOnFhirServerIfNecessary(biobank)).thenReturn(expected);

    List<OperationOutcome> actual = localSync.updateAllBiobanksOnFhirServerIfNecessary();

    assertEquals(Collections.singletonList(expected), actual);
  }

  @Test
  void testUpdateBiobankIfNecessary_noIdentifier() {
    Organization biobank = new Organization();

    OperationOutcome outcome = sync.updateBiobankOnFhirServerIfNecessary(biobank);

    assertEquals("No BBMRI Identifier for Organization",
        outcome.getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testUpdateBiobankIfNecessary_noDirectoryResponse() {
    Organization biobank = new Organization().addIdentifier(createBbmriIdentifier(BBMRI_ERIC_ID));
    OperationOutcome expected = new OperationOutcome();
    when(directoryApi.fetchBiobank(BBMRI_ERIC_ID)).thenReturn(Either.left(expected));

    OperationOutcome actual = sync.updateBiobankOnFhirServerIfNecessary(biobank);

    assertEquals(expected, actual);
  }

  @Test
  void testUpdateBiobankIfNecessary_noUpdateNecessary() {
    Organization org = new Organization().addIdentifier(createBbmriIdentifier(BBMRI_ERIC_ID));
    Biobank biobank = new Biobank();
    when(directoryApi.fetchBiobank(BBMRI_ERIC_ID)).thenReturn(Either.right(biobank));

    OperationOutcome actual = sync.updateBiobankOnFhirServerIfNecessary(org);

    assertEquals("No Update necessary", actual.getIssueFirstRep().getDiagnostics());
  }

  @Test
  void testUpdateBiobankIfNecessary_updateName() {
    Organization org = new Organization().addIdentifier(createBbmriIdentifier(BBMRI_ERIC_ID));
    Biobank biobank = new Biobank();
    biobank.setName("target");
    when(directoryApi.fetchBiobank(BBMRI_ERIC_ID)).thenReturn(Either.right(biobank));
    OperationOutcome expected = new OperationOutcome();
    when(fhirApi.updateResource(org)).thenReturn(expected);

    OperationOutcome actual = sync.updateBiobankOnFhirServerIfNecessary(org);

    assertEquals(expected, actual);
    assertEquals("target", org.getName());
  }
}
