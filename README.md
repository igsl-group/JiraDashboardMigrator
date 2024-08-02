Jira Dashboard Migrator

This tool was created because official Jira Cloud Migration Assistant does not support Data Center to Cloud migration of filters and dashboards.

Pre-requisites
1. Java 8+ required.
1. Migrate Jira Data Center to Jira Cloud using Jira Cloud Migration Assistant. This migrates everything except filters and dashboards.
1. In Jira Data Center, grant a user admin rights and access to all projects. This is required to extract filter and dashboard data.
1. In Jira Cloud, grant a user admin rights and access to all projects and groups. This is required to recreate filters and dashboards and sharing them with original permissions. 

Configuration
1. Edit config.json and update source and target information. 
    * For targetAPIToken, you need to generate an API token at https://id.atlassian.com/manage-profile/security/api-tokens
1. Set jerseyLog to true if you want to log HTTP data of REST API calls.

Usage
1. Extract project/role/custom field/user/group identifier mappings:
    * java -jar JiraDashboardMigrator-[version].jar config.json dump
    * Data files will be created: 
        * Project.Cloud.json (List of projects found on Cloud)
        * Project.DataCenter.json (List of projects found on Data Center)
        * Project.Map.json (Data Center to Cloud project ID mapping)
        * The same set of files will be created for Role, User, Group, Field.
    * You will get some error messages: 
        * For projects, 3 projects "DEV_AAStock", "Production Management" and "Project Initialization" are expected due to outdated workflows. 
        * For users, "michaelcheungcy" cannot be mapped because the user was renamed by Cloud Migration to "Michael Chreung". 
        * Edit User.Map.json to map Michael Cheung manually.
        * For custom fields, 7 cannot be mapped due to plugins are not included in Cloud. This is expected.
        * For roles, "PM role" cannot be mapped because it wasn't migrated due to having no references. This is expected.
        * For groups, all groups should be mapped.
	
1. Dump filter from Data Center: 
    * java -jar JiraDashboardMigrator-[version].jar config.json dumpFilter
    * Data file Filter.Data.json will be created. 
    * You will get some errors about unable to map project ID for 3 projects mentioned above. These can be ignored.
    * You will get many warnings about unable to map values in JQL. These can be ignored.
    * The following JQL content will be automatically translated: 
        * Project ID
        * Custom field name
    * If possible, manually fix the jql attribute values in the filters.

1. Create filters on Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json createFilter
    * Data file Filter.Data.json is used as input.
    * Data file Filter.Migrated.json will be created. It contains the IDs of the migrated filters as well as failed migrations and their errors. 
    * You may get a lot of "cannot create" errors because Jira validates the jql first. 
    * If you want to fix the filter, you will need to update its jql attributes.
    * You may get "cannot change owner" error if the target own already has a filter of the same name. These can be ignored.
    
1. You may delete created filter from Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json deleteFilter
    * Data file Filter.Migrated.json is used as input.

1. Dump dashboard from Data Center: 
    * java -jar JiraDashboardMigrator-[version].jar config.json dumpDashboard
    * Data files will be used as input: 
        * Project|Role|User|Group|Field.Map.json
        * Filter.Migrated.json
    * Data file Dashboard.DataCenter.json will be created.
    * You will get some errors about not being able to map values, mostly related to filter IDs. These can be ignored. 
    * Optionally modify attributes in Dashboard.DataCenter.json.
    
1. Create dashboard on Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json createDashboard
    * Data file Dashboard.DataCenter.json is used as input.
    * Data file Dashboard.Migrated.json will be created.
    * You will get warning messages about setting owners. This is because dashboard owners cannot be changed programmatically.
    * Go to Cloud site and change dashboard owners as instructed.
    
1. You may delete created dashboard from Cloud: 
    * java -jar JiraDashboardMigrator-[version].jar config.json deleteDashboard
    * Data file Dashboard.Migrated.json is used as input.

1. Log is appended to DashboardMigrator.log. 