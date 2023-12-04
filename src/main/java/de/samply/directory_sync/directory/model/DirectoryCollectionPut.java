package de.samply.directory_sync.directory.model;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryCollectionPut extends HashMap {
  private static final Logger logger = LoggerFactory.getLogger(DirectoryCollectionPut.class);

  public DirectoryCollectionPut() {
        this.put("entities", new ArrayList<Entity>());
    }

    public void setCountry(String id, String country) {
        getEntity(id).setCountry(country);
    }

    public void setName(String id, String name) {
        getEntity(id).setName(name);
    }

    public void setDescription(String id, String description) {
        getEntity(id).setDescription(description);
    }

    public void setContact(String id, String contact) {
        getEntity(id).setContact(contact);
    }

    public void setBiobank(String id, String biobank) {
        getEntity(id).setBiobank(biobank);
    }

    public void setSize(String id, Integer size) {
        getEntity(id).setSize(size);
    }

    public void setOrderOfMagnitude(String id, Integer size) {
        getEntity(id).setOrderOfMagnitude(size);
    }

    public void setNumberOfDonors(String id, Integer size) {
        getEntity(id).setNumberOfDonors(size);
    }

    public void setOrderOfMagnitudeDonors(String id, Integer size) {
        getEntity(id).setOrderOfMagnitudeDonors(size);
    }

    public void setType(String id, List<String> type) {
        getEntity(id).setType(type);
    }

    public void setDataCategories(String id, List<String> dataCategories) {
        getEntity(id).setDataCategories(dataCategories);
    }

    public void setNetworks(String id, List<String> networks) {
        getEntity(id).setNetworks(networks);
    }

    public void setSex(String id, List<String> sex) {
        getEntity(id).setSex(sex);
    }

    public void setAgeLow(String id, Integer value) {
        getEntity(id).setAgeLow(value);
    }

    public void setAgeHigh(String id, Integer value) {
        getEntity(id).setAgeHigh(value);
    }

    public void setMaterials(String id, List<String> value) {
        getEntity(id).setMaterials(value);
    }

    public void setStorageTemperatures(String id, List<String> value) {
        getEntity(id).setStorageTemperatures(value);
    }

    public void setDiagnosisAvailable(String id, List<String> value) {
        getEntity(id).setDiagnosisAvailable(value);
    }

    public List<String> getCollectionIds() {
        return getEntities().stream()
            .map(entity -> (String) entity.get("id"))
            .collect(Collectors.toList());
    }

    public ArrayList<Entity> getEntities() {
        return (ArrayList<Entity>) get("entities");
    }

    private Entity getEntity(String id) {
        Entity entity = null;

        for (Entity e: getEntities())
            if (e.get("id").equals(id)) {
                entity = e;
                break;
            }

        if (entity == null) {
            entity = new Entity(id);
            this.getEntities().add(entity);
        }

        return entity;
    }

    public class Entity extends HashMap<String, Object> {
        public Entity(String id) {
            setId(id);
        }

        public void setId(String id) {
            put("id", id);
        }

        public String getId() {
            return (String) get("id");
        }
        
        public void setCountry(String country) {
            if (country == null || country.isEmpty())
                return;

                put("country", country);
        }

        public void setName(String name) {
            if (name == null || name.isEmpty())
                return;

            put("name", name);
        }

        public void setDescription(String description) {
            if (description == null || description.isEmpty())
                return;

            put("description", description);
        }

        public void setContact(String contact) {
            if (contact == null || contact.isEmpty())
                return;

            put("contact", contact);
        }

        public void setBiobank(String biobank) {
            if (biobank == null || biobank.isEmpty())
                return;

            put("biobank", biobank);
        }

        public void setSize(Integer size) {
            if (size == null)
                return;

            put("size", size);
        }

        public void setOrderOfMagnitude(Integer orderOfMagnitude) {
            if (orderOfMagnitude == null)
                return;

            put("order_of_magnitude", orderOfMagnitude);
        }

        public void setNumberOfDonors(Integer size) {
            if (size == null)
                return;

            put("number_of_donors", size);
        }

        public void setOrderOfMagnitudeDonors(Integer orderOfMagnitude) {
            if (orderOfMagnitude == null)
                return;

            put("order_of_magnitude_donors", orderOfMagnitude);
        }

        public void setType(List<String> type) {
            if (type == null)
                type = new ArrayList<String>();
 
            put("type", type);
        }

        public void setDataCategories(List<String> dataCategories) {
            if (dataCategories == null)
                dataCategories = new ArrayList<String>();
 
            put("data_categories", dataCategories);
        }

        public void setNetworks(List<String> networks) {
            if (networks == null)
                networks = new ArrayList<String>();
 
            put("network", networks);
        }

        public void setSex(List<String> sex) {
            if (sex == null)
                sex = new ArrayList<String>();

            put("sex", sex);
        }

        public void setAgeLow(Integer value) {
            put("age_low", value);
        }

        public void setAgeHigh(Integer value) {
            put("age_high", value);
        }

        public void setMaterials(List<String> materials) {
            if (materials == null)
                materials = new ArrayList<String>();
 
            put("materials", materials);
        }

        public void setStorageTemperatures(List<String> storageTemperatures) {
            if (storageTemperatures == null)
                storageTemperatures = new ArrayList<String>();

            put("storage_temperatures", storageTemperatures);
        }

        public void setDiagnosisAvailable(List<String> diagnoses) {
            if (diagnoses == null)
                diagnoses = new ArrayList<String>();

            put("diagnosis_available", diagnoses);
        }
    }
}
