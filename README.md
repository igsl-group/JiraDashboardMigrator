# Jira Dashboard Migrator
This tool was created because official Jira Cloud Migration Assistant does not support Data Center to Cloud migration of filters and dashboards.

## Pre-requisites
1. Java 8+ required.
1. Migrate Jira Data Center to Jira Cloud using Jira Cloud Migration Assistant. This migrates everything except filters and dashboards.
1. In Jira Data Center, grant a user admin rights and access to all projects. This is required to extract filter and dashboard data.
1. In Jira Cloud, grant a user admin rights and access to all projects and groups. This is required to recreate filters and dashboards and sharing them with original permissions. 
1. Network access to Jira DC/Server for REST API calls and network access to Jira database for database queries.
1. Network access to Jira Cloud for REST API calls.

## Limitations
1. You need to define a mapping configuration for each dashboard gadget type. 
1. Atlassian REST API only supports creating dashboard in 2-column layout.
1. Some object types cannot be mapped due to having no uniquely identifiable properties (agile board, sprint). 

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
- Important object types, e.g. status, issue type, project, custom field, should have all conflicts resolved. 
- Some object types are not possible to match. e.g. Agile Board and Sprint. These objects have no uniquely identifiable properties, two sprints can have the exact same properties, making matching impossible. But it is not a fatal error if nothing references them. 
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
- The -dmf switch deletes filters owned by current user. It is required to avoid filter name clashing. 
- You can perform a test run by specifying -cf false. This disables all REST API calls, so nothing will be modified. But as a result, filters referring other filters will not be fully translated.  
- For JQL clause with multiple object references, by default the algorithm will drop invalid references and continue translating. You can require all object references be resolved by specifying -avm (all values mapped). 
- The algorithm will automatically omit view/edit permissions that is not valid in Jira Cloud (e.g. shared to a user/group/project not found in Cloud). A log entry will be recorded.
- By default existing filters will not be modified. Specify -of (overwrite filter) to update existing filters.
- This will output Filter.Map.json and Filter.[Timestamp].csv. The CSV file contains all the information of the mapping results. 
- CSV columns: 
    * Owner - User key of the owner of the filter in Jira Server\Data Center.
    * Filter Name - Name of filter.
    * Server ID - Filter Id in Jira Server\Data Center.
    * Server JQL - JQL of the filter in Jira Server\Data Center.
    * Cloud ID - Filter Id in Jira Cloud. 
    * Cloud JQL - JQL of the filter in Jira Cloud.
    * Action - One of:
        * Add Edit Permission - This row is about adding edit permission.
        * Map Filter - This row is about translating JQL. 
        * Owner changed - This filter is owned by a user not in Cloud, and has been reassigned to another user.
        * Remove Edit Permission - This row is about removing edit permission. 
        * Share Permission omitted - This row is about invalid share permissions omitted.
    * Result - One of:
        * Success
        * Fail
        * Warning
    * Error - Error message logged.
    * Bug - Automatic analysis of if this entry is a bug or not.
    * Notes - Automatic analysis result.
- You should verify the results by filtering for Result = Fail and Bug = (blank). Any rows that match should be inspected. 
- Filters are also stored as JSON files in folders ```[Timestamp]-OriginalFilter``` and ```[Timestamp]-NewFilter```. You can use diff tools to compare the translated filters. 
- Filters are parsed using an ANTLR library provided in Jira Data Center. The classes are packaged in \lib\JiraClasses.jar. Atlassian stated they don't use this library themselves but provide it just to be nice. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -dmf -cf [true|false] [-avm] [-of]
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
- The -dmd switch deletes any dashboards owned by current user. It is required to avoid dashboard name clashing.
- Due to Atlassian not providing a REST API to change dashboard layout, migrated dashboards are stuck using the 2-column layout. If the original dashboard is using other layouts, the gadgets will be adjusted to fit into 2-column layout. i.e. 3-column layout will result in the 3rd column becoming a new row.
- Gadgets that cannot be found in Cloud will be replaced by a spacer gadget. The title will indicate the original uri/module key.
- This will output Dashboard.Map.json and Dashboard.[Timestamp].csv. The CSV file contains all the information of the mapping results. 
- CSV columns:
    * Owner - User key of the owner of dashboard.
    * Server ID - ID of dashboard in Jira Server\Data Center.
    * Server Name - Name of dashboard in Jira Server\Data Center.
    * Cloud ID - ID of dashboard in Jira Cloud.
    * Cloud Name - Name of dashboard in Jira Cloud.
    * Action - One of:
        * Change dashboard owner - This row is about assigning dashbaord to its real owner.
        * Configure gadget - Update gadget configuration.
        * Create dashboard - Create dashboard.
        * Create gadget - Create gadget in dashboard.
        * Map gadget configuration - Mapping gadget configuration.
        * Omitted Edit permission - Invalid edit permission omitted.
        * Omitted Share permission - Invalid share permission omitted.
        * Owner Change - Dashboard is owned by user not in Cloud and has been reassigned. 
    * Gadget - Gadget uri/module key.
    * Cloud Gadget ID - ID of gadget in Jira Cloud.
    * Configuration - Configuration key being mapped.
    * Result - One of:
        * Success
        * Fail
        * Warning
    * Message - Error message logged.
    * Bug - Automatic analysis of if this entry is a bug or not.
    * Notes - Automatic analysis result.
- You should verify the results by filtering for Result = Fail and Bug = (blank). Any rows that match should be inspected. 
- Dashboards are also stored as JSON files in folders ```[Timestamp]-OriginalDashboard``` and ```[Timestamp]-NewDashboard```. You can use diff tools to compare the translated dashboards. 
- Atlassian does not allow modifying dashboard permissions via REST API. As a result, we cannot modify existing dashboards. If a dashboard already exists, when changing owner, the created dashboard will be renamed by adding timestamp as a suffix. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -dmd -cd
```

##### Export Mapping
- Export object ID mappings as CSV files. 
- This will output [Object type].Mapping.csv files. A convenient CSV for looking up Data Center to Cloud object references. 
```
java -jar JiraDashboardMigrator-<version>.jar -c <config.json> -dom
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
1. Complete migration using JCMA. 
1. Grant Roles.
1. Dump Data Center Objects.
1. Dump Cloud Objects.
1. Map Objects.
1. Manually fix conflicts in [Object type].Map.json files.
1. [Optional] Export Mapping.
1. Migrate Filter.
1. Migrate Dashboard.
1. Revoke Roles. 

## Build Instructions
1. You need to import \lib\JiraClasses.jar into a local repository:
    * Run this command: 
    ```
    mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
        -Dfile=.\lib\JiraClasses.jar -DgroupId=Atlassian \ 
        -DartifactId=JiraClasses -Dversion=1.0.0 \
        -Dpackaging=jar -DlocalRepositoryPath=.\localRepository
    ```
    * Or use Maven build configuration to run goal ```install:install-file```.
1. Build with Maven.