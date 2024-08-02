# Jira Dashboard Migrator
This tool was created because official Jira Cloud Migration Assistant does not support Data Center to Cloud migration of filters and dashboards.

## Pre-requisites
1. Java 8+ required.
1. Migrate Jira Data Center to Jira Cloud using Jira Cloud Migration Assistant. This migrates everything except filters and dashboards.
1. In Jira Data Center, grant a user admin rights and access to all projects. This is required to extract filter and dashboard data.
1. In Jira Cloud, grant a user admin rights and access to all projects and groups. This is required to recreate filters and dashboards and sharing them with original permissions. 
1. Network access to Jira DC/Server for REST API calls and network access to Jira database for database queries.
1. Network access to Jira Cloud for REST API calls.

## Configuration
Edit config.json and update source and target information. 
1. "sourceDatabaseURL": "jdbc:mysql://[IP]:[Port]/[Database Name]"
    * Update IP, Port and Database Name to connect to Jira database. 
1. "sourceDatabaseUser": "[User Name]",
    * Update database user name. If you leave it empty, you will be prompted for it. 
1. "sourceDatabasePassword": null,
    * If you leave the password as null, you will be prompted for it. 
1. "sourceRESTBaseURL": "http://[IP]:[Port]/jira/",
    * Update IP and Port to connect to Jira Data Center.
1. "sourceUser": "[User]",
    * Update Jira user name. A user with administrative rights is required. If you leave it empty, you will be prompted for it.
1. "sourcePassword": null,
    * If you leave the password as null, you will be prompted for it.
1. "targetRESTBaseURL": "https://[Cloud Domain]/",
    * Update Cloud domain to connect to Jira Cloud.
1. "targetUser": "[User Email]",
    * Update Jira Cloud user email. If you leave it empty, you will be prompted for it.
1. "targetAPIToken": "[API Token]",
    * If you leave the API token as null, you will be prompted for it.
1. "jerseyLog": false
    * Set to true if you get details of REST API calls. 

## Advanced: Gadget Configuration
1. GadgetType folder contains configuration files for migrating gadgets.
1. GadgetTypeDisabled\Example.json contains the template.

## Usage
1. Extract objects from DC/Server: 
    * java -jar JiraDashboardMigrator-[version].jar config.json dumpDC
    * Data files [ObjectType].DataCenter.json will be created.
1. Extract filters and dashboards from DC/Server: 
    * java -jar JiraDashboardMigrator-[version].jar config.json dumpDCFilterDashboard
    * Data files [Filter|Dashboard].DataCenter.json will be created.
1. Dump objects from Cloud:
    * java -jar JiraDashboardMigrator-[version].jar config.json dumpCloud
    * Data files [ObjectType].Cloud.json will be created.
1. Map objects between DC/Server and Cloud:
    * java -jar JiraDashboardMigrator-[version].jar config.json mapObject
    * Data files [ObjectType].Map.json will be created.
1. Manually inspect [ObjectType].Map.json files to:
    * Check "conflict" attribute.
    * Each sub-entry is a DC/Server object ID mapped to multiple Cloud object IDs.
    * Resolve by adding under "mapped" attribute "[DC ID]": "[Cloud ID]"
1. Map filters: 
    * java -jar JiraDashboardMigrator-[version].jar config.json mapFilter
    * Data file Filter.Remapped.json will be created.
    * Compare with Filter.DataCenter.json to verify the mapped values. 
1. Create filters on Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json createFilter
    * Data file Filter.Remapped.json is used as input.
    * Data file Filter.Migrated.json will be created. It contains the IDs of the migrated filters as well as failed migrations and their errors. 
1. You may delete created filter from Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json deleteFilter
    * Data file Filter.Migrated.json is used as input.
1. Map dashboards: 
    * java -jar JiraDashboardMigrator-[version].jar config.json mapDashboard
    * Data file Dashboard.Remapped.json will be created.
    * Compare with Dashboard.DataCenter.json to verify the mapped values.
1. Create dashboard on Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json createDashboard
    * Data file Dashboard.Remapped.json is used as input.
    * Data file Dashboard.Migrated.json will be created.
    * You will get warning messages about setting owners. This is because dashboard owners cannot be changed programmatically.
    * Go to Cloud site and change dashboard owners as instructed.
1. You may delete created dashboard from Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json deleteDashboard
    * Data file Dashboard.Migrated.json is used as input.