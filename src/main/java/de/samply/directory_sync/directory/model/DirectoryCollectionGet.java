package de.samply.directory_sync.directory.model;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.samply.directory_sync.fhir.model.FhirCollection;

public class DirectoryCollectionGet extends HashMap {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryCollectionGet.class);

    public void init() {
        put("items", new ArrayList());
    }

    public String getCountryId(String id) {
        return (String) ((Map) getItem(id).get("country")).get("id");
    }

    public String getContactId(String id) {
        return (String) ((Map) getItem(id).get("contact")).get("id");
    }

    public String getBiobankId(String id) {
        return (String) ((Map) getItem(id).get("biobank")).get("id");
    }

    public List<String> getTypeIds(String id) {
        Map item = getItem(id);
        List<Map<String,Object>> types = (List<Map<String,Object>>) item.get("type");
        List<String> typeLabels = new ArrayList<String>();
        for (Map type: types)
            typeLabels.add((String) type.get("id"));

        return typeLabels;
    }

    public List<String> getDataCategoryIds(String id) {
        Map item = getItem(id);
        List<Map<String,Object>> dataCategories = (List<Map<String,Object>>) item.get("data_categories");
        List<String> dataCategoryLabels = new ArrayList<String>();
        for (Map type: dataCategories)
            dataCategoryLabels.add((String) type.get("id"));

        return dataCategoryLabels;
    }

    public List<String> getNetworkIds(String id) {
        Map item = getItem(id);
        List<Map<String,Object>> networks = (List<Map<String,Object>>) item.get("network");
        List<String> networkLabels = new ArrayList<String>();
        for (Map type: networks)
            networkLabels.add((String) type.get("id"));

        return networkLabels;
    }

    public String getName(String id) {
        return (String) getItem(id).get("name");
    }

    public String getDescription(String id) {
        return (String) getItem(id).get("description");
    }

    public List<String> getCollectionIds() {
        return getItems().stream()
            .map(entity -> (String) entity.get("id"))
            .collect(Collectors.toList());
    }

    public List<Map> getItems() {
        if (!this.containsKey("items")) {
            logger.warn("DirectoryCollectionGet.getItems: no items key, aborting");
            return null;
        }
        return (List<Map>) get("items");
    }

    public Map getItemZero() {
        if (!containsKey("items"))
            return null;
        List<Map> itemList = (List<Map>) get("items");
        if (itemList == null || itemList.size() == 0)
            return null;
        return itemList.get(0);
    }

   private Map getItem(String id) {
        Map item = null;

        List<Map> items = getItems();
        if (items == null)
            return null;

        for (Map e: items) {
            if (e == null) {
                logger.warn("DirectoryCollectionGet.getItem: problem with getItems()");
                continue;
            }
            if (e.get("id").equals(id)) {
                item = e;
                break;
            }
        }

        return item;
    }
}
