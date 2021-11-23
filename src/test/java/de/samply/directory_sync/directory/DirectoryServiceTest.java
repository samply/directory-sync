package de.samply.directory_sync.directory;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import de.samply.directory_sync.directory.DirectoryApi.CollectionSizeDto;
import de.samply.directory_sync.directory.model.BbmriEricId;
import io.vavr.control.Either;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  private static final BbmriEricId COLLECTION_ID = BbmriEricId.valueOf("bbmri-eric:ID:DE_174718:collection:0").get();

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  private static final BbmriEricId COLLECTION_ID_1 = BbmriEricId.valueOf("bbmri-eric:ID:DE_174718:collection:1").get();

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  private static final BbmriEricId COLLECTION_ID_2 = BbmriEricId.valueOf("bbmri-eric:ID:DE_174718:collection:2").get();

  private static final int COLLECTION_SIZE = 135807;
  private static final int COLLECTION_SIZE_1 = 161200;
  private static final int COLLECTION_SIZE_2 = 161202;
  private static final String COUNTRY_CODE = "DE";

  @Mock
  private DirectoryApi api;

  @InjectMocks
  private DirectoryService service;

  @Test
  void updateCollectionSizes_OneExistingCollection() {
    when(api.listAllCollectionIds(COUNTRY_CODE)).thenReturn(Either.right(singleton(COLLECTION_ID)));
    OperationOutcome expectedOutcome = new OperationOutcome();
    when(api.updateCollectionSizes(COUNTRY_CODE,
        singletonList(new CollectionSizeDto(COLLECTION_ID, COLLECTION_SIZE))))
        .thenReturn(expectedOutcome);

    List<OperationOutcome> outcome = service.updateCollectionSizes(
        mapOf(COLLECTION_ID, COLLECTION_SIZE));

    assertEquals(singletonList(expectedOutcome), outcome);
  }

  @Test
  void updateCollectionSizes_OneNonExistingCollection() {
    when(api.listAllCollectionIds(COUNTRY_CODE)).thenReturn(Either.right(emptySet()));
    OperationOutcome expectedOutcome = new OperationOutcome();
    when(api.updateCollectionSizes(COUNTRY_CODE, emptyList())).thenReturn(expectedOutcome);

    List<OperationOutcome> outcome = service.updateCollectionSizes(mapOf(COLLECTION_ID, COLLECTION_SIZE));

    assertEquals(singletonList(expectedOutcome), outcome);
  }

  @Test
  void updateCollectionSizes_OneNonExistingAndOneExistingCollection() {
    when(api.listAllCollectionIds(COUNTRY_CODE)).thenReturn(Either.right(singleton(COLLECTION_ID_1)));
    OperationOutcome expectedOutcome = new OperationOutcome();
    when(api.updateCollectionSizes(COUNTRY_CODE,
        singletonList(new CollectionSizeDto(COLLECTION_ID_1, COLLECTION_SIZE_1))))
        .thenReturn(expectedOutcome);

    List<OperationOutcome> outcome = service.updateCollectionSizes(
        mapOf(COLLECTION_ID_1, COLLECTION_SIZE_1, COLLECTION_ID_2, COLLECTION_SIZE_2));

    assertEquals(singletonList(expectedOutcome), outcome);
  }

  @Test
  void updateCollectionSizes_ListAllCollectionIdsError() {
    OperationOutcome error = new OperationOutcome();
    when(api.listAllCollectionIds(COUNTRY_CODE)).thenReturn(Either.left(error));

    List<OperationOutcome> outcome = service.updateCollectionSizes(mapOf(COLLECTION_ID, COLLECTION_SIZE));

    assertEquals(singletonList(error), outcome);
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
