package de.samply.directory_sync.directory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.samply.directory_sync.Util;
import de.samply.directory_sync.directory.model.DirectoryCollectionGet;
import de.samply.directory_sync.directory.model.DirectoryCollectionPut;

public class MergeDirectoryCollectionGetToDirectoryCollectionPut {
  private static final Logger logger = LoggerFactory.getLogger(MergeDirectoryCollectionGetToDirectoryCollectionPut.class);

  public static DirectoryCollectionPut merge(DirectoryCollectionGet directoryCollectionGet, DirectoryCollectionPut directoryCollectionPut) {
    List<String> collectionIds = directoryCollectionPut.getCollectionIds();
    for (String collectionId: collectionIds)
        if (merge(collectionId, directoryCollectionGet, directoryCollectionPut) == null)
          return null;
    
    return directoryCollectionPut;
  }

  private static DirectoryCollectionPut merge(String collectionId, DirectoryCollectionGet directoryCollectionGet, DirectoryCollectionPut directoryCollectionPut) {
    try {
      directoryCollectionPut.setName(collectionId, directoryCollectionGet.getName(collectionId));
      directoryCollectionPut.setDescription(collectionId, directoryCollectionGet.getDescription(collectionId));
      directoryCollectionPut.setContact(collectionId, directoryCollectionGet.getContactId(collectionId));
      directoryCollectionPut.setCountry(collectionId, directoryCollectionGet.getCountryId(collectionId));
      directoryCollectionPut.setBiobank(collectionId, directoryCollectionGet.getBiobankId(collectionId));
      directoryCollectionPut.setType(collectionId, directoryCollectionGet.getTypeIds(collectionId));
      directoryCollectionPut.setDataCategories(collectionId, directoryCollectionGet.getDataCategoryIds(collectionId));
      directoryCollectionPut.setNetworks(collectionId, directoryCollectionGet.getNetworkIds(collectionId));
      directoryCollectionPut.setNetworks(collectionId, directoryCollectionGet.getNetworkIds(collectionId));
    } catch(Exception e) {
      logger.error("Problem merging DirectoryCollectionGet into DirectoryCollectionPut. " + Util.traceFromException(e));
      return null;
    }

    return directoryCollectionPut;
  }
}
