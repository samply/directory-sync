package de.samply.directory_sync.directory;

import de.samply.directory_sync.directory.DirectoryApi.CollectionSizeDto;
import io.vavr.control.Either;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.OperationOutcome;

public class DirectoryService {

  private final DirectoryApi api;

  public DirectoryService(DirectoryApi api) {
    this.api = api;
  }

  public OperationOutcome updateCollectionSizes(Map<String, Integer> collectionSizes) {
    Either<OperationOutcome, Set<String>> result = api.listAllCollectionIds();
    if (result.isLeft()) {
      return result.getLeft();
    }

    Set<String> existingCollectionIds = result.get();

    List<CollectionSizeDto> collectionSizeDtos = collectionSizes.entrySet().stream()
        .filter(e -> existingCollectionIds.contains(e.getKey()))
        .map(e -> new CollectionSizeDto(e.getKey(), e.getValue()))
        .collect(Collectors.toList());

    return api.updateCollectionSizes(collectionSizeDtos);
  }
}
