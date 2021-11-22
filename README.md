[![Build Status](https://travis-ci.com/samply/directory-sync.svg?branch=master)](https://travis-ci.com/samply/directory-sync)

# Directory Sync

Based on the mapping from https://samply.github.io/bbmri-fhir-ig/mappings.html.

## Usage

```xml
<dependency>
    <groupId>de.samply</groupId>
    <artifactId>directory-sync</artifactId>
    <version>0.1.0</version>
</dependency>
```

# Directory fields and support status

#### Biobank

| BBMRI-ERIC-Field | Source of Truth | Status |
|--|--|--|
|id | Both! |*Used as primary means of biobank identification, should not change* |
|name | Directory | active |
|acronym | Directory | TODO |
|description | Directory | TODO |
|bioresource_reference | Directory | TODO |
|url | Directory | TODO |
|juridical_person | Directory | TODO |
|country | Directory | TODO |
|it_support_available | Directory | TODO |
|it_staff_size | Directory | TODO |
|is_available | Directory | TODO |
|his_available | Directory | TODO |
|partner_charter_signed | Directory | TODO |
|head_title_before_name | Directory | TODO |
|head_firstname | Directory | TODO |
|head_lastname | Directory | TODO |
|head_title_after_name | Directory | TODO |
|head_role | Directory | TODO |
|contact | *see section contact* |
|latitude | Directory | TODO |
|longitude | Directory | TODO |
|network | *see section network* |
|collaboration_commercial | Directory | TODO |
|collaboration_non_for_profit | Directory | TODO |
|capabilities | Directory | TODO |
|quality | Directory | TODO |

#### Contact Information
Unsolved problem: How do we identify corresponding contacts in the directory and FHIR? Contacts have no identifier in FHIR.
| BBMRI-ERIC-Field | Source of Truth | Status |
|--|--|--|
| title_before_name | Directory | TODO |
|first_name | Directory | TODO |
|last_name | Directory | TODO |
|title_after_name | Directory | TODO |
| phone | Directory | TODO |
|email | Directory | TODO |
|address | Directory | TODO |
|zip | Directory | TODO |
|city | Directory | TODO |
|country | Directory | TODO |

#### Collection

| BBMRI-ERIC-Field | Source of Truth | Status |
|--|--|--|
|id | Both! |*Used as primary means of biobank identification, should not change* |
|country | Directory | TODO |
|biobank | -- | *Not expected to change* |
|name | Directory | TODO |
|acronym | Directory | TODO |
|description | Directory | TODO |
|bioresource_reference | Directory | TODO |
|parent_collection | -- | *No FHIR Mapping yet, so no sync* |
|network | *see section network* |
|type |  Directory | TODO |
|data_categories |  Directory | TODO |
|quality | | Directory | TODO |
|order_of_magnitude | FHIR | TODO |
|order_of_magnitude_donors | FHIR | TODO |
|size | FHIR | active |
|number_of_donors | FHIR | TODO |
|timestamp | FHIR (??) | TODO |
|id_card | Directory | TODO |
|head_title_before_name | Directory | TODO |
|head_firstname | Directory | TODO |
|head_lastname | Directory | TODO |
|head_title_after_name | Directory | TODO |
|head_role | Directory | TODO |
|contact | *see section contact* |
|latitude | Directory | TODO |
|longitude | Directory | TODO |
|sex | FHIR | TODO |
|diagnosis_available | FHIR | TODO |
|age Low| FHIR | TODO |
|age High| FHIR | TODO |
|age Unit| FHIR | *Fixed to years* |
|body_part_examined| -- | *No FHIR Mapping yet, so no sync* |
|imaging_modality| -- | *No FHIR Mapping yet, so no sync* |
|image_dataset_type| -- | *No FHIR Mapping yet, so no sync* |
|materials| FHIR | TODO |
|storage_temperature | FHIR | TODO |
|sample_access_fee | Directory | TODO |
|sample_access_joint_project | Directory | TODO |
|sample_access_description | Directory | TODO |
|sample_access_uri | Directory | TODO |
|data_access_fee | Directory | TODO |
|data_access_joint_project | Directory | TODO |
|data_access_description | Directory | TODO |
|data_access_uri | Directory | TODO |
|image_access_fee | Directory | TODO |
|image_access_joint_project | Directory | TODO |
|image_access_description | Directory | TODO |
|image_access_uri | Directory | TODO |
|collaboration_commercial | Directory | TODO |
|collaboration_non_for_profit | Directory | TODO |
|sample_processing_sop | Directory | TODO |
|sample_transport_sop | Directory | TODO |
|sample_storage_sop | Directory | TODO |
|data_processing_sop | Directory | TODO |
|data_transport_sop | Directory | TODO |
|data_storage_sop | Directory | TODO |

## Docu

| Anfrage | Subject | Stratifier | Scoring |
|---------|---------|------------|---------|
| order_of_magnitude | Specimen | Collection | cohort |
| order_of_magnitude_donors | Patient | Collection | cohort |
| size |  Specimen | Collection | cohort |
| number_of_donors | Patient | Collection | cohort |
| age Low| Patient | Collection |  continuous-variable |
| age High| Patient | Collection |  continuous-variable |
| age Unit| hardcoded years | -- | -- |
| materials| Specimen | Collection, Type | cohort |
| storage_temperature | Specimen | Collection, storageTemp | cohort |
| sex| Patient | Collection, gender | cohort |
| diagnosis_available| Collection, Condition |

## License

Copyright 2020 The Samply Development Community

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
