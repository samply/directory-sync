package de.samply.directory_sync;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.checkerframework.checker.units.qual.min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.samply.directory_sync.directory.model.BbmriEricId;

/**
 * Represents data for the STAR model, organized by collection.
 * 
 * Input data represents data read in from the FHIR store.
 * 
 * Output data is in a format that is ready to be exported to the Directory.
 */
public class StarModelData {
    private static final Logger logger = LoggerFactory.getLogger(StarModelData.class);

    // Input data for the star model.
    // This comprises of one row per Patient/Specimen/Diagnosis combination.
    // Each row is a map containing attributes relevant to the star model.
    // A Map of a List of Maps: collectionID_1 -> [row0, row1, ...]
    public class InputRow extends HashMap<String,String> {
        public InputRow(String collection, String sampleMaterial, String patientId, String sex, String age) {
            setCollection(collection);
            setSampleMaterial(sampleMaterial);
            setId(patientId);
            setSex(sex);
            setAgeAtPrimaryDiagnosis(age);
        }

        public InputRow(InputRow row, String histLoc) {
            for (String key: row.keySet())
                put(key, row.get(key));
            setHistLoc(histLoc);
        }

        public void setCollection(String collection) {
            if (collection == null)
                return;
            put("collection", collection);
        }

        public void setSampleMaterial(String sampleMaterial) {
             if (sampleMaterial == null)
                return;
           put("sample_material", FhirToDirectoryAttributeConverter.convertMaterial(sampleMaterial));
        }

        public void setId(String id) {
            if (id == null)
                return;
            put("id", id);
        }

        public void setSex(String sex) {
            if (sex == null)
                return;
            put("sex", FhirToDirectoryAttributeConverter.convertSex(sex));
        }

        public void setHistLoc(String histLoc) {
             if (histLoc == null)
                return;
           put("hist_loc", FhirToDirectoryAttributeConverter.convertDiagnosis(histLoc));
        }

        public void setAgeAtPrimaryDiagnosis(String age) {
            if (age == null)
                return;
            put("age_at_primary_diagnosis", age);
        }
    }

    private Map<String,List<InputRow>> inputData = new HashMap<String,List<InputRow>>();

    public void addInputRow(String collectionId, InputRow row) {
        if (!inputData.containsKey(collectionId))
            inputData.put(collectionId, new ArrayList<InputRow>());
        List<InputRow> rows = inputData.get(collectionId);
        rows.add(row);
    }

    public InputRow newInputRow(String collection, String sampleMaterial, String patientId, String sex, String age) {
        return new InputRow(collection, sampleMaterial, patientId, sex, age);
    }

    public InputRow newInputRow(InputRow row, String histLoc) {
        return new InputRow(row, histLoc);
    }

    public List<Map<String, String>> getInputRowsAsStringMaps(String collectionId) {
        List<Map<String, String>> rowsAsStringMaps = new ArrayList<Map<String, String>>();
        for (InputRow row: inputData.get(collectionId)) {
            rowsAsStringMaps.add(row);
        }

        return rowsAsStringMaps;
    }

    public Set<String> getInputCollectionIds() {
        return inputData.keySet();
    }

    // Output data.
    // One big fact table for everything. Every fact contains a mandatory collection ID.
    private List<Map<String, String>> factTables = new ArrayList<Map<String, String>>();
    private int minDonors = 10; // Minimum number of donors per fact

    public int getMinDonors() {
        return minDonors;
    }

    public void setMinDonors(int minDonors) {
        this.minDonors = minDonors;
    }

    public void addFactTable(String collectionId, List<Map<String, String>> factTable) {
        factTables.addAll(factTable);
    }

    public List<Map<String, String>> getFactTables() {
        return factTables;
    }

    public int getFactCount() {
        return factTables.size();
    }

    /**
     * Gets the country code for the collections, e.g. "DE".
     * 
     * Assumes that all collections will have the same code and simply returns
     * the code of the first collection.
     * 
     * If there are no collections, returns null.
     * 
     * May throw a null pointer exception.
     * 
     * @return Country code
     */
    public String getCountryCode() {
        if (factTables == null || factTables.size() == 0)
            return null;

        String countryCode = BbmriEricId
                .valueOf((String) factTables.get(0).get("collection"))
                .orElse(null)
                .getCountryCode();

        return countryCode;
    }

}
