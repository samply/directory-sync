package de.samply.directory_sync.directory;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import de.samply.directory_sync.directory.DirectoryApi.CollectionSizeDto;
import io.vavr.control.Either;
import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

  private static final String COLLECTION_ID = "collection-id-135747";
  private static final String COLLECTION_ID_1 = "collection-id-1-161039";
  private static final String COLLECTION_ID_2 = "collection-id-2-161049";
  private static final int COLLECTION_SIZE = 135807;
  private static final int COLLECTION_SIZE_1 = 161200;
  private static final int COLLECTION_SIZE_2 = 161202;

  @Mock
  private DirectoryApi api;

  @InjectMocks
  private DirectoryService service;

  @Test
  void updateCollectionSizes_OneExistingCollection() {
    when(api.listAllCollectionIds()).thenReturn(Either.right(singleton(COLLECTION_ID)));
    OperationOutcome expectedOutcome = new OperationOutcome();
    when(api.updateCollectionSizes(
        singletonList(new CollectionSizeDto(COLLECTION_ID, COLLECTION_SIZE))))
        .thenReturn(expectedOutcome);

    OperationOutcome outcome = service.updateCollectionSizes(mapOf(COLLECTION_ID, COLLECTION_SIZE));

    assertSame(expectedOutcome, outcome);
  }

  @Test
  void updateCollectionSizes_OneNonExistingCollection() {
    when(api.listAllCollectionIds()).thenReturn(Either.right(emptySet()));
    OperationOutcome expectedOutcome = new OperationOutcome();
    when(api.updateCollectionSizes(emptyList())).thenReturn(expectedOutcome);

    OperationOutcome outcome = service.updateCollectionSizes(mapOf(COLLECTION_ID, COLLECTION_SIZE));

    assertSame(expectedOutcome, outcome);
  }

  @Test
  void updateCollectionSizes_OneNonExistingAndOneExistingCollection() {
    when(api.listAllCollectionIds()).thenReturn(Either.right(singleton(COLLECTION_ID_1)));
    OperationOutcome expectedOutcome = new OperationOutcome();
    when(api.updateCollectionSizes(
        singletonList(new CollectionSizeDto(COLLECTION_ID_1, COLLECTION_SIZE_1))))
        .thenReturn(expectedOutcome);

    OperationOutcome outcome = service.updateCollectionSizes(
        mapOf(COLLECTION_ID_1, COLLECTION_SIZE_1, COLLECTION_ID_2, COLLECTION_SIZE_2));

    assertSame(expectedOutcome, outcome);
  }

  @Test
  void updateCollectionSizes_ListAllCollectionIdsError() {
    OperationOutcome error = new OperationOutcome();
    when(api.listAllCollectionIds()).thenReturn(Either.left(error));

    OperationOutcome outcome = service.updateCollectionSizes(mapOf(COLLECTION_ID, COLLECTION_SIZE));

    assertSame(error, outcome);
  }

  private static <K, V> Map<K, V> mapOf(K key, V value) {
    HashMap<K, V> map = new HashMap<>();
    map.put(key, value);
    return map;
  }

  private static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
    HashMap<K, V> map = new HashMap<>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }
}
