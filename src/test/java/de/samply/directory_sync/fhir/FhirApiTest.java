package de.samply.directory_sync.fhir;

import de.samply.directory_sync.MainTest;
import de.samply.directory_sync.fhir.FhirApi;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FhirApiTest {

    @Test
    void testMergeById() {

        Map<String,Integer> testCounts = new HashMap<>();
        List<Organization> testOrg = new ArrayList<>();
        testCounts.put("1",100);
        Identifier identifier = MainTest.createBbmriIdentifier("test");
        testOrg.add((Organization) new Organization().addIdentifier(identifier).setId("1"));

        Map<String, Integer> res = FhirApi.mapToCounts(testCounts, testOrg);
        System.out.println("res = " + res);
    }

}
