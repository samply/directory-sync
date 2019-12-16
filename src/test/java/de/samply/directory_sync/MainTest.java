package de.samply.directory_sync;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void mergeById1() {

        Map<String,Integer> testCounts = new HashMap<>();
        List<Organization> testOrg = new ArrayList<>();
        testCounts.put("1",100);
        Identifier identifier = new Identifier();
        identifier.setSystem("http://www.bbmri-eric.eu/").setValue("test");
        testOrg.add((Organization) new Organization().addIdentifier(identifier).setId("1"));

        Map<String, Integer> res = Main.mapToCounts(testCounts, testOrg);
        System.out.println("res = " + res);
    }
}