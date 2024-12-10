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
1. ```"sourceDatabaseURL": "jdbc:mysql://[IP]:[Port]/[Database Name]"```
    * Update IP, Port and Database Name to connect to Jira database. 
1. ```"sourceDatabaseUser": "[User Name]"```
    * Update database user name. If you leave it empty, you will be prompted for it. 
1. ```"sourceDatabasePassword": null```
    * If you leave the password as null, you will be prompted for it. 
1. ```"sourceScheme": "[http|https]"```
    * Scheme to use for Jira Data Center. If empty, default is https.
1. ```"sourceRESTBaseURL": "[IP]:[Port]"```
    * Update IP and Port to connect to Jira Data Center.
1. ```"sourceUser": "[User]"```
    * Update Jira user name. A user with administrative rights is required. If you leave it empty, you will be prompted for it.
1. ```"sourcePassword": null```
    * Jira user password. If you leave the password as null, you will be prompted for it.
1. ```"targetScheme": "[http|https]"```
    * Scheme to use for Jira Cloud. If empty, default is https.
1. ```"targetRESTBaseURL": "[Cloud Domain].atlassian.net"```
    * Update Cloud domain to connect to Jira Cloud.
1. ```"targetUser": "[User Email]"```
    * Update Jira Cloud user email. If you leave it empty, you will be prompted for it.
1. ```"targetAPIToken": "[API Token]"```
    * If you leave the API token as null, you will be prompted for it.
1. ```"jerseyLog": false```
    * Set to true if you get details of REST API calls. 
1. ```"connectionPoolSize": [#]```
    * Connection pool size. Default is 10.
1. ```"threadCount": [#]```
    * Thread count for multi-threaded actions. Default is 8.
1. ```"threadWait": [#]```
    * No. of milliseconds to wait in threads. Default is 1000.
1. ```"limit": [#]```
    * No. of REST API calls allowed within a period. Default is 3. 
1. ```"period": [#]```
    * No. of milliseconds in 1 period. Default is 1000.
1. ```"connectTimeout": [#]```
    * Network connection timeout. Default is 0 (unlimited).
1. ```"readTimeout": [#]```
    * Network read timeout. Default is 0 (unlimited).
1. ```"defaultOwner": "[Cloud user account ID]"```
    * Replacement owner for filters and dashboards owned by users not found in Jira Cloud. If empty, such filters and dashboards will be reported as error and not migrated. Otherwise, the owner will be changed. 

## Gadget Configuration
1. ```\src\main\config\GadgetType``` folder contains configuration files for migrating gadgets.
1. ```\src\main\config\GadgetTypeDisabled\Example.json``` contains the template.
1. Any gadgets not matched to a configuration in ```\src\main\config\GadgetType``` will be migrated as is, which will most likely fail (as most gadgets use a different module key in Cloud). These gadgets will then be replaced by a spacer gadget. 
1. To add a new gadget configuration: 
    * Create a new dashboard in both Data Center and Cloud. 
    * Add and configure a gadget in both.
    * For Data Center:
        * Use -ddc (see Usage section) to export dashboard from Data Center. 
    * For Cloud:
        * Get list of gadgets in dashboard: 
        ```
        /rest/api/3/dashboard/{dashboardId}/gadget
        ```
        * Get gadget configurations: 
        ```
        /rest/api/3/dashboard/{dashboardId}/items/{itemId}/properties
        ```
        * Get gadget configuration details: 
        ```
        /rest/api/3/dashboard/{dashboardId}/items/{itemId}/properties/{propertyKey}
        ```
    * Compare the configurations between Data Center and Cloud.
    * Test with different configurations of the gadget. 
    * Create a JSON configuration and add it to GadgetType folder. Use the gadget type's plugin name and display name as the file name.
    * Your goals would be: 
        * To find out uri/module key of the gadget in Data Center and Cloud.
        * To map the configurations. 
1. If the features provided by JSON configuration is not sufficient, implement a custom mapper using interface ```com.igsl.config.CustomGadgetConfigMapper```. You can find examples in these packages: 
```
com.igsl.config.brokenbuild
com.igsl.config.countergadget
com.igsl.config.zephyrsquad
```

## Usage
##### Print Help
```
java -jar JiraDashboardMigrator-[version].jar
```

##### Grant Roles
- To grant roles in Jira Cloud.
- Roles are referenced by name, e.g. jira-administrator.
- Specify multiple roles by delimiting with space.
- If -u is not specified, the roles will be granted to targetUser in config.json. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -gr -r <Role names> [-u <Account ID>]
```

##### Revoke Roles
- To revoke roles in Jira Cloud.
- Roles are referenced by name, e.g. jira-administrator.
- Specify multiple roles by delimiting with space.
- If -u is not specified, the roles will be revoked from targetUser in config.json. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -rr -r <Role names> [-u <Account ID>]
```

##### Dump Data Center Objects
- Dump objects from Data Center to JSON files.
- You can optionally specify object types to export. Default is to export all types. 
- Supported object types: 
    * SERVICE_DESK
    * REQUEST_TYPE
    * PRIORITY
    * STATUS
    * PROJECT
    * PROJECT_CATEGORY
    * PROJECT_COMPONENT
    * PROJECT_VERSION
    * ROLE
    * USER
    * GROUP
    * CUSTOM_FIELD
    * CUSTOM_FIELD_OPTION
    * AGILE_BOARD 
    * SPRINT 
    * ISSUE_TYPE
    * FILTER
    * DASHBOARD
- This will output [Object type].DataCenter.json files. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -ddc [Object types]
```

##### Dump Cloud Objects
- Dump objects from Data Center to JSON files.
- You can optionally specify object types to export. Default is to export all types. 
- Supported object types: 
    * SERVICE_DESK
    * REQUEST_TYPE
    * PRIORITY
    * STATUS
    * PROJECT
    * PROJECT_CATEGORY
    * PROJECT_COMPONENT
    * PROJECT_VERSION
    * ROLE
    * USER
    * GROUP
    * CUSTOM_FIELD
    * CUSTOM_FIELD_CONTEXT
    * CUSTOM_FIELD_OPTION
    * AGILE_BOARD
    * SPRINT
    * ISSUE_TYPE
    * FILTER
- This will output [Object type].Cloud.json files.
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -dcloud [Object types]
```

##### Map Objects
- Map dumped objects. 
- You can optionally specify object types to map. Default is to map all types. 
- Default mapping algorithm is based on the object's display name. Some objects will be renamed by JCMA (e.g. "Field 1" to "Field 1 (migrated)". The default matching logic takes this into consideration and will consider them the same field. To disable fuzzy matching, specify -em for exact match. 
- Due to Cloud not allowing access user emails, the default user mapping (based on display name) will be very inaccurate. You can export user list CSV from admin.atlassian.com and specify the CSV file with -uc. 
- This will output [Object type].Map.json files.
- You will need to manually inspect the results. It is a JSON containing "matched", "conflict" and "unmapped" properties. 
- You should resolve the entries in "conflict" by selecting which Cloud object to use, and add them to "matched".
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -m [-ot <Object types>] [-uc <CSV>] [-em]
```

##### Migrate Filter
- Migrate filters by:
    * Add current user as editor for all filters in Cloud. 
    * For each filter: 
        * Translate filter JQL.
        * Create/update filter in Cloud.
        * Change created filter owner.
    * Remove current user as editor for all filters in Cloud.
- You can perform a test run by specifying -cf false. This disables all REST API calls, so nothing will be modified. But as a result, filters referring other filters will not be fully translated.  
- For JQL clause with multiple object references, by default the algorithm will drop invalid references and continue translating. You can require all object references be resolved by specifying -avm (all values mapped). 
- By default existing filters will not be modified. Specify -of (overwrite filter) to update existing filters.
- This will output Filter.Map.json and Filter.[Timestamp].csv. The CSV file contains all the information of the mapping results. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -cf [true|false] [-avm] [-of]
```

##### Migrate Dashboard
- Migrate dashboards by:
    * For each dashboard: 
        * Create dashboard in Cloud (owned by current user).
        * For each gadget:
            * Map gadget uri/module key.
            * Map gadget configurations.  
            * Add gadget to dashboard.
            * Configure gadget. 
            * Change dashboard owner.
- Due to Atlassian not providing a REST API to change dashboard layout, migrated dashboards are stuck using the 2-column layout. If the original dashboard is using other layouts, the gadgets will be adjusted to fit into 2-column layout. i.e. 3-column layout will result in the 3rd column becoming a new row.
- Gadgets that cannot be found in Cloud will be replaced by a spacer gadget. The title will indicate the original uri/module key.
- This will output Dashboard.Map.json and Dashboard.[Timestamp].csv. The CSV file contains all the information of the mapping results. 
- Atlassian does not allow modifying dashboard permissions via REST API. As a result, we cannot modify existing dashboards. If a dashboard already exists, when changing owner, the created dashboard will be renamed by adding timestamp as a suffix. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -cd
```

##### Add Filter Permission
- Add current user as editor for all filters in Cloud. 
- This is provided for convenience.
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -afp
```

##### Remove Filter Permission
- Remove current user as editor for all filters in Cloud. 
- This is provided for convenience.
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -rfp
```

## Migration Steps
1. Grant Roles.
1. Dump Data Center Objects.
1. Dump Cloud Objects.
1. Map Objects.
1. Manually fix conflicts in [Object type].Map.json files.
1. Migrate Filter.
1. Migrate Dashboard.
1. Revoke Roles. 