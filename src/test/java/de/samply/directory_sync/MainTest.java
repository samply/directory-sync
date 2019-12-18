package de.samply.directory_sync;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.directory_sync.directory.DirectoryApi;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
class MainTest {

    @Mock
    DirectoryApi directoryApi;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    IGenericClient fhirClient;

    @Test
    void testMergeById() {

        Map<String,Integer> testCounts = new HashMap<>();
        List<Organization> testOrg = new ArrayList<>();
        testCounts.put("1",100);
        Identifier identifier = createBbmriIdentifier("test");
        testOrg.add((Organization) new Organization().addIdentifier(identifier).setId("1"));

        Map<String, Integer> res = Main.mapToCounts(testCounts, testOrg);
        System.out.println("res = " + res);
    }

    @Test
    void testUpdateBiobankNameIfChanged() throws IOException {
        String expResponse = "{\"_meta\":{\"href\":\"/api/v2/eu_bbmri_eric_biobanks\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_biobanks\",\"name\":\"eu_bbmri_eric_biobanks\"," +
                "\"label\":\"Biobanks\",\"description\":\"Biobank (or standalone collection) Organization\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"description\":\"Unique biobank ID within BBMRI-ERIC based on " +
                "MIABIS 2.0 standard (ISO 3166-1 alpha-2 + underscore + biobank national ID or name), prefixed with " +
                "bbmri-eric:ID: string - MIABIS-2.0-01.\",\"attributes\":[],\"maxLength\":255,\"auto\":false," +
                "\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true,\"visible\":false," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/name\",\"fieldType\":\"STRING\",\"name\":\"name\"," +
                "\"label\":\"name\",\"description\":\"Biobank name according to MIABIS 2.0 - MIABIS-2.0-03.\"," +
                "\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":false,\"readOnly\":false," +
                "\"labelAttribute\":true,\"unique\":false,\"visible\":true,\"lookupAttribute\":false," +
                "\"isAggregatable\":false},{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/acronym\"," +
                "\"fieldType\":\"STRING\",\"name\":\"acronym\",\"label\":\"acronym\",\"description\":\"Biobank " +
                "acronym - MIABIS-2.0-02.\",\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/description\",\"fieldType\":\"TEXT\"," +
                "\"name\":\"description\",\"label\":\"description\",\"description\":\"Biobank description - MIABIS-2" +
                ".0-08.\",\"attributes\":[],\"maxLength\":65535,\"auto\":false,\"nillable\":true,\"readOnly\":false," +
                "\"labelAttribute\":false,\"unique\":false,\"visible\":false,\"lookupAttribute\":false," +
                "\"isAggregatable\":false},{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/bioresource_reference\"," +
                "\"fieldType\":\"TEXT\",\"name\":\"bioresource_reference\",\"label\":\"bioresource reference\"," +
                "\"description\":\"Bioresource reference to be cited when the bioresource (biobank/collection) is " +
                "used for research.\",\"attributes\":[],\"maxLength\":65535,\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":false," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/url\",\"fieldType\":\"HYPERLINK\",\"name\":\"url\"," +
                "\"label\":\"url\",\"description\":\"Biobank URL - MIABIS-2.0-04.\",\"attributes\":[]," +
                "\"maxLength\":255,\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/juridical_person\",\"fieldType\":\"STRING\"," +
                "\"name\":\"juridical_person\",\"label\":\"juridical person\",\"description\":\"Juristic person of a " +
                "biobank according to MIABIS 2.0 - MIABIS-2.0-05.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false," +
                "\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/country\",\"fieldType\":\"XREF\"," +
                "\"name\":\"country\",\"label\":\"country\",\"description\":\"Country hosting the biobank according " +
                "to MIABIS 2.0 - MIABIS-2.0-06.\",\"attributes\":[]," +
                "\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_countries\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_countries\",\"name\":\"eu_bbmri_eric_countries\"," +
                "\"label\":\"countries\",\"description\":\"Countries\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_countries/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"description\":\"Unique ID.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true," +
                "\"visible\":true,\"lookupAttribute\":true,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_countries/meta/name\",\"fieldType\":\"STRING\",\"name\":\"name\"," +
                "\"label\":\"name\",\"description\":\"Country hosting the biobank according to MIABIS 2.0 - MIABIS-2" +
                ".0-06.\",\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":false,\"readOnly\":false," +
                "\"labelAttribute\":true,\"unique\":false,\"visible\":true,\"lookupAttribute\":true," +
                "\"isAggregatable\":false}],\"labelAttribute\":\"name\",\"idAttribute\":\"id\"," +
                "\"lookupAttributes\":[\"id\",\"name\"],\"isAbstract\":false,\"writable\":false," +
                "\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\",\"READ_METADATA\",\"COUNT_DATA\"," +
                "\"READ_DATA\"]},\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/it_support\",\"fieldType\":\"COMPOUND\"," +
                "\"name\":\"it_support\",\"label\":\"it support\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/it_support_available\"," +
                "\"fieldType\":\"BOOL\",\"name\":\"it_support_available\",\"label\":\"it support available\"," +
                "\"description\":\"Is IT support available at the biobank?\",\"attributes\":[],\"auto\":false," +
                "\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/it_staff_size\",\"fieldType\":\"INT\"," +
                "\"name\":\"it_staff_size\",\"label\":\"it staff size\",\"description\":\"Size of the biobank " +
                "dedicated IT staff measured as 2n.\",\"attributes\":[],\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/is_available\",\"fieldType\":\"BOOL\"," +
                "\"name\":\"is_available\",\"label\":\"is available\",\"description\":\"Has the biobank a " +
                "computer-based Information System (IS)?\",\"attributes\":[],\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/his_available\",\"fieldType\":\"BOOL\"," +
                "\"name\":\"his_available\",\"label\":\"his available\",\"description\":\"Has the biobank on-line or " +
                "off-line connection to a Hospital Information System (HIS)?\",\"attributes\":[],\"auto\":false," +
                "\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}],\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":false," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/partner_charter_signed\",\"fieldType\":\"BOOL\"," +
                "\"name\":\"partner_charter_signed\",\"label\":\"partner charter signed\",\"description\":\"Biobank " +
                "has signed BBMRI-ERIC Partner Charter.\",\"attributes\":[],\"auto\":false,\"nillable\":false," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/contact_information\",\"fieldType\":\"COMPOUND\"," +
                "\"name\":\"contact_information\",\"label\":\"contact information\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/head_title_before_name\"," +
                "\"fieldType\":\"STRING\",\"name\":\"head_title_before_name\",\"label\":\"head title before name\"," +
                "\"description\":\"head title before name, value \\\"prof. mr.\\\"\",\"attributes\":[]," +
                "\"maxLength\":255,\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/head_firstname\",\"fieldType\":\"STRING\"," +
                "\"name\":\"head_firstname\",\"label\":\"head first name\",\"description\":\"First name of a person " +
                "in charge of the biobank.\",\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/head_lastname\",\"fieldType\":\"STRING\"," +
                "\"name\":\"head_lastname\",\"label\":\"head last name\",\"description\":\"Last name of a person in " +
                "charge of the biobank.\",\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/head_title_after_name\",\"fieldType\":\"STRING\"," +
                "\"name\":\"head_title_after_name\",\"label\":\"head title after name\",\"description\":\"head value " +
                "after name, value \\\"Bsc.\\\"\",\"attributes\":[],\"maxLength\":255,\"auto\":false," +
                "\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/head_role\",\"fieldType\":\"STRING\"," +
                "\"name\":\"head_role\",\"label\":\"head role\",\"description\":\"Official role of the person in " +
                "charge of the biobank: typically PI or Director.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/contact_priority\",\"fieldType\":\"INT\"," +
                "\"name\":\"contact_priority\",\"label\":\"contact priority\",\"description\":\"Priority of the " +
                "contact 1..n (i.e., non-negative integer), where the highest priority should be used for contacting " +
                "about given set of samples. E.g., if a collection has contactPriority=3, the biobank in which the " +
                "collection resides has contactPriority=10, and the biobankNetwork to which the collection or biobank" +
                " belongs has contactPriority=7, the biobank contact should be used.\",\"attributes\":[]," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/latitude\",\"fieldType\":\"STRING\"," +
                "\"name\":\"latitude\",\"label\":\"latitude\",\"description\":\"Latitude of the biobank in the WGS84 " +
                "system (the one used by GPS), positive is northern hemisphere.\",\"attributes\":[]," +
                "\"maxLength\":255,\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/longitude\",\"fieldType\":\"STRING\"," +
                "\"name\":\"longitude\",\"label\":\"longitude\",\"description\":\"Longitude of the biobank in the " +
                "WGS84 system (the one used by GPS), positive is to the East of Greenwich.\",\"attributes\":[]," +
                "\"maxLength\":255,\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}]," +
                "\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/also_known\",\"fieldType\":\"ONE_TO_MANY\"," +
                "\"name\":\"also_known\",\"label\":\"Also Known in\",\"attributes\":[]," +
                "\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_also_known_in_bio\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_also_known_in_bio\"," +
                "\"name\":\"eu_bbmri_eric_also_known_in_bio\",\"label\":\"Biobank also Known in\"," +
                "\"description\":\"Biobank also found in …\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_also_known_in_bio/meta/id\"," +
                "\"fieldType\":\"STRING\",\"name\":\"id\",\"label\":\"id\",\"description\":\"Unique ID.\"," +
                "\"attributes\":[],\"maxLength\":255,\"auto\":true,\"nillable\":false,\"readOnly\":true," +
                "\"labelAttribute\":false,\"unique\":true,\"visible\":true,\"lookupAttribute\":true," +
                "\"isAggregatable\":false},{\"href\":\"/api/v2/eu_bbmri_eric_also_known_in_bio/meta/label\"," +
                "\"fieldType\":\"STRING\",\"name\":\"label\",\"label\":\"label\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":true,\"unique\":false," +
                "\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false," +
                "\"expression\":\"name_system\"}],\"labelAttribute\":\"label\",\"idAttribute\":\"id\"," +
                "\"lookupAttributes\":[\"id\"],\"isAbstract\":false,\"writable\":false,\"languageCode\":\"en\"," +
                "\"permissions\":[\"AGGREGATE_DATA\",\"READ_METADATA\",\"COUNT_DATA\",\"READ_DATA\"]}," +
                "\"mappedBy\":\"biobank\",\"auto\":false,\"nillable\":true,\"readOnly\":false," +
                "\"labelAttribute\":false,\"unique\":false,\"visible\":true,\"lookupAttribute\":false," +
                "\"isAggregatable\":false},{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/contact\"," +
                "\"fieldType\":\"XREF\",\"name\":\"contact\",\"label\":\"contact\",\"description\":\"Reference to a " +
                "contact ID.\",\"attributes\":[],\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_persons\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_persons\",\"name\":\"eu_bbmri_eric_persons\"," +
                "\"label\":\"Persons\",\"description\":\"Contact Information\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_persons/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"description\":\"Contact identifier, prefixed with " +
                "bbmri-eric:contactID:\",\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":false," +
                "\"readOnly\":true,\"labelAttribute\":false,\"unique\":true,\"visible\":false," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_persons/meta/email\",\"fieldType\":\"EMAIL\",\"name\":\"email\"," +
                "\"label\":\"email\",\"description\":\"Email according to MIABIS 2.0 - MIABIS-2.0-07-D.\"," +
                "\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":false,\"readOnly\":false," +
                "\"labelAttribute\":true,\"unique\":false,\"visible\":true,\"lookupAttribute\":true," +
                "\"isAggregatable\":false}],\"labelAttribute\":\"email\",\"idAttribute\":\"id\"," +
                "\"lookupAttributes\":[\"email\"],\"isAbstract\":false,\"writable\":false,\"languageCode\":\"en\"," +
                "\"permissions\":[\"AGGREGATE_DATA\",\"READ_METADATA\",\"COUNT_DATA\",\"READ_DATA\"]},\"auto\":false," +
                "\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/collections\",\"fieldType\":\"ONE_TO_MANY\"," +
                "\"name\":\"collections\",\"label\":\"collections\",\"attributes\":[]," +
                "\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_collections\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_collections\",\"name\":\"eu_bbmri_eric_collections\"," +
                "\"label\":\"Collections\",\"description\":\"Biobanks and sample collections\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_collections/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id-collections\",\"description\":\"Unique collection ID within " +
                "BBMRI-ERIC based on MIABIS 2.0 standard, constructed from biobankID prefix + :collection: + local " +
                "collection ID string - MIABIS-2.0-01.\",\"attributes\":[],\"maxLength\":255,\"auto\":false," +
                "\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true,\"visible\":false," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_collections/meta/collection\",\"fieldType\":\"COMPOUND\"," +
                "\"name\":\"collection\",\"label\":\"collection\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_collections/meta/name\",\"fieldType\":\"STRING\"," +
                "\"name\":\"name\",\"label\":\"collection name\",\"description\":\"Collection name according to " +
                "MIABIS 2.0 - MIABIS-2.0-03.\",\"attributes\":[],\"maxLength\":255,\"auto\":false,\"nillable\":false," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}],\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}],\"labelAttribute\":\"name\"," +
                "\"idAttribute\":\"id\",\"lookupAttributes\":[],\"isAbstract\":false,\"writable\":false," +
                "\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\",\"READ_METADATA\",\"COUNT_DATA\"," +
                "\"READ_DATA\"]},\"mappedBy\":\"biobank\",\"auto\":false,\"nillable\":true,\"readOnly\":false," +
                "\"labelAttribute\":false,\"unique\":false,\"visible\":true,\"lookupAttribute\":false," +
                "\"isAggregatable\":false},{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/network\"," +
                "\"fieldType\":\"MREF\",\"name\":\"network\",\"label\":\"network\",\"description\":\"Reference to a " +
                "biobank network ID, to which the collection or biobank belongs; this attribute can also be used for " +
                "biobank network, where it refers to the superior biobank network).\",\"attributes\":[]," +
                "\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_networks\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_networks\",\"name\":\"eu_bbmri_eric_networks\"," +
                "\"label\":\"Networks\",\"description\":\"Biobank networks\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_networks/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"description\":\"Unique ID of a biobank network within BBMRI-ERIC " +
                "based on MIABIS 2.0 standard (ISO 3166-1 alpha-2 +underscore + biobank national ID or name), " +
                "prefixed with bbmri-eric:networkID: string; if biobank network is on European or higher level, EU_ " +
                "prefix is to be used instead of country prefix.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_networks/meta/name\",\"fieldType\":\"STRING\",\"name\":\"name\"," +
                "\"label\":\"name\",\"description\":\"Biobank network name.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":true,\"unique\":false," +
                "\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}],\"labelAttribute\":\"name\"," +
                "\"idAttribute\":\"id\",\"lookupAttributes\":[],\"isAbstract\":false,\"writable\":false," +
                "\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\",\"READ_METADATA\",\"COUNT_DATA\"," +
                "\"READ_DATA\"]},\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/collaboration_commercial\",\"fieldType\":\"BOOL\"," +
                "\"name\":\"collaboration_commercial\",\"label\":\"collaboration commercial\"," +
                "\"description\":\"Biobank/collection can be used for collaboration with commercial partners.\"," +
                "\"attributes\":[],\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/collaboration_non_for_profit\"," +
                "\"fieldType\":\"BOOL\",\"name\":\"collaboration_non_for_profit\",\"label\":\"collaboration non for " +
                "profit\",\"description\":\"Biobank/collection can be used for collaboration with non-for-profit " +
                "partners.\",\"attributes\":[],\"auto\":false,\"nillable\":true,\"readOnly\":false," +
                "\"labelAttribute\":false,\"unique\":false,\"visible\":true,\"lookupAttribute\":false," +
                "\"isAggregatable\":false},{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/capabilities\"," +
                "\"fieldType\":\"CATEGORICAL_MREF\",\"name\":\"capabilities\",\"label\":\"Capablities provided\"," +
                "\"description\":\"Capabilities that the biobank organisation can offer to a researcher\"," +
                "\"attributes\":[],\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_capabilities\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_capabilities\",\"name\":\"eu_bbmri_eric_capabilities\"," +
                "\"label\":\"capabilities\",\"description\":\"Capabilities\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_capabilities/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"description\":\"Unique ID.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_capabilities/meta/label\",\"fieldType\":\"STRING\"," +
                "\"name\":\"label\",\"label\":\"label\",\"description\":\"Term in the ontology.\",\"attributes\":[]," +
                "\"maxLength\":255,\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":true," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":true,\"isAggregatable\":false}]," +
                "\"labelAttribute\":\"label\",\"idAttribute\":\"id\",\"lookupAttributes\":[\"label\"]," +
                "\"isAbstract\":false,\"writable\":false,\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\"," +
                "\"READ_METADATA\",\"COUNT_DATA\",\"READ_DATA\"]},\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/operational_standards\"," +
                "\"fieldType\":\"CATEGORICAL_MREF\",\"name\":\"operational_standards\",\"label\":\"Applied " +
                "operational standards\",\"description\":\"Biobank/collection can be used for collaboration with " +
                "non-for-profit partners.\",\"attributes\":[]," +
                "\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_ops_standards\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_ops_standards\",\"name\":\"eu_bbmri_eric_ops_standards\"," +
                "\"label\":\"ops_standards\",\"description\":\"Operational Standards\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_ops_standards/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"description\":\"Unique ID.\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_ops_standards/meta/label\",\"fieldType\":\"STRING\"," +
                "\"name\":\"label\",\"label\":\"label\",\"description\":\"Term in the ontology.\",\"attributes\":[]," +
                "\"maxLength\":255,\"auto\":false,\"nillable\":false,\"readOnly\":false,\"labelAttribute\":true," +
                "\"unique\":false,\"visible\":true,\"lookupAttribute\":true,\"isAggregatable\":false}]," +
                "\"labelAttribute\":\"label\",\"idAttribute\":\"id\",\"lookupAttributes\":[\"label\"]," +
                "\"isAbstract\":false,\"writable\":false,\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\"," +
                "\"READ_METADATA\",\"COUNT_DATA\",\"READ_DATA\"]},\"auto\":false,\"nillable\":true," +
                "\"readOnly\":false,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/other_standards\",\"fieldType\":\"STRING\"," +
                "\"name\":\"other_standards\",\"label\":\"Other operational standards\",\"description\":\"Other " +
                "standards that the biobank complies with (please specify)\",\"attributes\":[],\"maxLength\":255," +
                "\"auto\":false,\"nillable\":true,\"readOnly\":false,\"labelAttribute\":false,\"unique\":false," +
                "\"visible\":false,\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_biobanks/meta/quality\",\"fieldType\":\"ONE_TO_MANY\"," +
                "\"name\":\"quality\",\"label\":\"Quality\",\"attributes\":[]," +
                "\"refEntity\":{\"href\":\"/api/v2/eu_bbmri_eric_bio_qual_info\"," +
                "\"hrefCollection\":\"/api/v2/eu_bbmri_eric_bio_qual_info\",\"name\":\"eu_bbmri_eric_bio_qual_info\"," +
                "\"label\":\"Biobank quality information\",\"description\":\"Biobank quality information\"," +
                "\"attributes\":[{\"href\":\"/api/v2/eu_bbmri_eric_bio_qual_info/meta/id\",\"fieldType\":\"STRING\"," +
                "\"name\":\"id\",\"label\":\"id\",\"attributes\":[],\"maxLength\":255,\"auto\":true," +
                "\"nillable\":false,\"readOnly\":true,\"labelAttribute\":false,\"unique\":true,\"visible\":false," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}," +
                "{\"href\":\"/api/v2/eu_bbmri_eric_bio_qual_info/meta/label\",\"fieldType\":\"STRING\"," +
                "\"name\":\"label\",\"label\":\"Label\",\"attributes\":[],\"maxLength\":255,\"auto\":false," +
                "\"nillable\":false,\"readOnly\":false,\"labelAttribute\":true,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":true,\"isAggregatable\":false,\"expression\":\"standards\"}]," +
                "\"labelAttribute\":\"label\",\"idAttribute\":\"id\",\"lookupAttributes\":[\"label\"]," +
                "\"isAbstract\":false,\"writable\":false,\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\"," +
                "\"READ_METADATA\",\"COUNT_DATA\",\"READ_DATA\"]},\"mappedBy\":\"biobank\",\"auto\":false," +
                "\"nillable\":true,\"readOnly\":true,\"labelAttribute\":false,\"unique\":false,\"visible\":true," +
                "\"lookupAttribute\":false,\"isAggregatable\":false}],\"labelAttribute\":\"name\"," +
                "\"idAttribute\":\"id\",\"lookupAttributes\":[],\"isAbstract\":false,\"writable\":false," +
                "\"languageCode\":\"en\",\"permissions\":[\"AGGREGATE_DATA\",\"READ_METADATA\",\"COUNT_DATA\"," +
                "\"READ_DATA\"]},\"_href\":\"/api/v2/eu_bbmri_eric_biobanks/bbmri-eric:ID:DE_BBD\"," +
                "\"id\":\"bbmri-eric:ID:DE_TEST\",\"name\":\"New Value\",\"acronym\":\"TEST\"," +
                "\"description\":\"Biobanks are cool \",\"url\":\"https://example.org/biobank\"," +
                "\"juridical_person\":\"University Hospital Musterstadt\"," +
                "\"country\":{\"_href\":\"/api/v2/eu_bbmri_eric_countries/DE\",\"id\":\"DE\",\"name\":\"Germany\"}," +
                "\"it_support_available\":true,\"it_staff_size\":2,\"is_available\":true," +
                "\"partner_charter_signed\":false,\"head_firstname\":\"Vlad\",\"head_lastname\":\"Dracula\"," +
                "\"head_role\":\"Scientific Head\",\"contact_priority\":1,\"latitude\":\"52.510494\"," +
                "\"longitude\":\"13.396764\",\"also_known\":[]," +
                "\"contact\":{\"_href\":\"/api/v2/eu_bbmri_eric_persons/bbmri-eric:contactID:DE_TEST\"," +
                "\"id\":\"bbmri-eric:contactID:DE_TEST\",\"email\":\"test@example.org\"}," +
                "\"collections\":[{\"_href\":\"/api/v2/eu_bbmri_eric_collections/bbmri-eric:ID:DE_BBD:collection:VAMPIR" +
                "\",\"id\":\"bbmri-eric:ID:DE_BBD:collection:VAMPIR\",\"name\":\"Vampir collection\"}," +
                "{\"_href\":\"/api/v2/eu_bbmri_eric_collections/bbmri-eric:ID:DE_BBD:collection:WEREWOLF\"," +
                "\"id\":\"bbmri-eric:ID:DE_BBD:collection:WEREWOLF\",\"name\":\"Vergessen\"}]," +
                "\"network\":[],\"collaboration_commercial\":true,\"collaboration_non_for_profit\":true," +
                "\"capabilities\":[{\"_href\":\"/api/v2/eu_bbmri_eric_capabilities/biomaterial-storage\"," +
                "\"id\":\"biomaterial-storage\",\"label\":\"Biological material storage\"}," +
                "{\"_href\":\"/api/v2/eu_bbmri_eric_capabilities/digital-imaging\",\"id\":\"digital-imaging\"," +
                "\"label\":\"Digital imaging\"},{\"_href\":\"/api/v2/eu_bbmri_eric_capabilities/immunohistochemistry" +
                "-scoring\",\"id\":\"immunohistochemistry-scoring\",\"label\":\"Immunohistochemistry scoring\"}," +
                "{\"_href\":\"/api/v2/eu_bbmri_eric_capabilities/immunohistochemistry-staining\"," +
                "\"id\":\"immunohistochemistry-staining\",\"label\":\"Immunohistochemistry staining\"}," +
                "{\"_href\":\"/api/v2/eu_bbmri_eric_capabilities/nucleic-acid-extraction\"," +
                "\"id\":\"nucleic-acid-extraction\",\"label\":\"Nucleic acid extraction\"}]," +
                "\"operational_standards\":[],\"quality\":[]}";


        Organization biobank = new Organization();
        biobank.setName("To be replaced");
        biobank.addIdentifier(createBbmriIdentifier("test"));
        Bundle bundle = new Bundle();
        bundle.addEntry().setResource(biobank);
        when(fhirClient.search().forResource(Organization.class)
                .withProfile("https://fhir.bbmri.de/StructureDefinition/Biobank").execute()).thenReturn(bundle);

        CloseableHttpResponse mockDirectoryResponse = mock(CloseableHttpResponse.class);
        when(mockDirectoryResponse.getEntity()).thenReturn(new StringEntity(expResponse));

        //Main.updateBiobanksIfChanged(sync);
        assertEquals(biobank.getName(),"New Value");
        //TODO Find a way to verify update actually happens
       /**
          verify(mockFhirClient)
                .update()
                .resource(biobank)
                .execute();
        **/
    }

    private static Identifier createBbmriIdentifier(String value) {
        Identifier identifier = new Identifier();
        identifier.setSystem("http://www.bbmri-eric.eu/").setValue(value);
        return identifier;
    }


}
