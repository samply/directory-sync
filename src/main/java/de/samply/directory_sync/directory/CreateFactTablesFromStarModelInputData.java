package de.samply.directory_sync.directory;

import de.samply.directory_sync.StarModelData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateFactTablesFromStarModelInputData {
  private static final Logger logger = LoggerFactory.getLogger(CreateFactTablesFromStarModelInputData.class);

  public static void createFactTables(StarModelData starModelInputData) {
    for (String collectionId: starModelInputData.getInputCollectionIds()) {
      List<Map<String, String>> factTableFinal = createFactTableFinal(collectionId,
        starModelInputData.getMinDonors(),
        starModelInputData.getInputRowsAsStringMaps(collectionId));
      starModelInputData.addFactTable(collectionId, factTableFinal);
    }
  }

  private static List<Map<String, String>> createFactTableFinal(String collectionId, int minDonors, List<Map<String, String>> patientSamples) {
    // Code translated from Petr Holub's R script "CRC-fact-sheet.R".
            // Select columns and create a new column "age_range"
            List<Map<String, String>> patientSamplesFacts = patientSamples.stream()
                    .map(result -> {
                        Map<String, String> fact = new HashMap<>(result);
                        fact.remove("sample_type");
                        fact.remove("sample_year_num");
                        fact.put("age_range", cutAgeRange(result.get("age_at_primary_diagnosis")));
                        return fact;
                    })
                    .collect(Collectors.toList());

            // Group by certain columns and calculate summary statistics
            Map<String, Long> factTable = patientSamplesFacts.stream()
                    .filter(fact -> !fact.containsValue(null))
                    .collect(Collectors.groupingBy(
                            fact -> String.join("_",
                                    fact.get("sex"),
                                    fact.get("hist_loc"),
                                    fact.get("age_range"),
                                    fact.get("sample_material")),
                            Collectors.counting()));

            // Filter out rows with fewer than a given number of donors
            if (minDonors > 0)
                factTable = factTable.entrySet().stream()
                    .filter(entry -> entry.getValue() >= minDonors)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // Perform additional data transformations on the facts table
            List<Map<String, String>> factTableFinal = new ArrayList<>();
            for (Map.Entry<String, Long> entry : factTable.entrySet()) {
                List<String> keyParts = Arrays.asList(entry.getKey().split("_"));

                String sampleMaterial = keyParts.get(3);
                for (int i=4; i<keyParts.size(); i++)
                    sampleMaterial = sampleMaterial + "_" + keyParts.get(i);
                Map<String, String> mapEntry = new HashMap<>();
                mapEntry.put("sex", keyParts.get(0));
                mapEntry.put("disease", keyParts.get(1));
                //mapEntry.put("disease", "urn:miriam:icd:U07.1"); // Fake value that should be acceptable to Directory
                mapEntry.put("age_range", keyParts.get(2));
                mapEntry.put("sample_type", sampleMaterial);
                mapEntry.put("number_of_donors", Long.toString(entry.getValue()));
                mapEntry.put("number_of_samples", Long.toString(entry.getValue()));
                mapEntry.put("id", "bbmri-eric:factID:" // All fact IDs must start with this (mandatory).
                    // Snip "bbmri-eric:ID:" from collection ID and replace : with _
                    + collectionId.substring(14, collectionId.length()).replaceAll(":", "_")
                    + "_"
                    + Math.abs(entry.getKey().hashCode()) // Add a hash code to make the ID unique
                    );
                mapEntry.put("last_update", LocalDate.now().toString());
                mapEntry.put("collection", collectionId);

                factTableFinal.add(mapEntry);
            }
            
        return factTableFinal;
    }

  private static String cutAgeRange(String age) {
    if (age == null || age.isBlank())
        return "Unknown";

    // Logic to cut age into bins
    int ageValue = Integer.parseInt(age);
    if (ageValue < 1) {
        return "Infant";
    } else if (ageValue < 2) {
        return "Infant";
    } else if (ageValue < 13) {
        return "Child";
    } else if (ageValue < 18) {
        return "Adolescent";
    } else if (ageValue < 45) {
        return "Adult";
    } else if (ageValue < 65) {
        return "Middle-aged";
    } else if (ageValue < 80) {
        return "Aged (65-79 years)";
    } else {
        return "Aged (>80 years)";
    }
  }
}
