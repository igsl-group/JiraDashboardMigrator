package com.igsl;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.igsl.model.mapping.MappingType;

/**
 * Houses the members for command line parsing
 */
public class CLI {
	
	// Enum so we can use switch on Option
	public static enum CLIOptions {
		CONFIG(null),
		DUMP_DC(DUMPDC_OPTION),
		DUMP_CLOUD(DUMPCLOUD_OPTION),
		MAP_OBJECT(MAPOBJECT_OPTION),
		CREATE_FILTER(CREATEFILTER_OPTION),
		DELETE_FILTER(DELETEFILTER_OPTION),
		LIST_FILTER(LISTFILTER_OPTION),
		MAP_DASHBOARD(MAPDASHBOARD_OPTION),
		CREATE_DASHBOARD(CREATEDASHBOARD_OPTION),
		DELETE_DASHBOARD(DELETEDASHBOARD_OPTION),
		LIST_DASHBOARD(LISTDASHBOARD_OPTION),
		GRANT_ROLE(GRANT_OPTION),
		REVOKE_ROLE(REVOKE_OPTION);
		private Option option;
		CLIOptions(Option option) {
			this.option = option;
		}
		public Option getOption() {
			return this.option;
		}
		public static CLIOptions parse(Option option) {
			for (CLIOptions opt : CLIOptions.values()) {
				if (opt.option != null &&
					opt.option.getOpt().equals(option.getOpt())) {
					return opt;
				}
			}
			return null;
		}
	}
	
	public static final Option CONFIG_OPTION = Option.builder()
			.desc("Path to config.json")
			.option("c")
			.longOpt("conf")
			.required()
			.hasArg()
			.build();
	
	public static final Option DUMPDC_OPTION = Option.builder()
			.desc("Dump objects from Data Center. Optionally specify object types: " + 
					MappingType.getMappingTypes(false))
			.option("ddc")
			.longOpt("dumpDC")
			.hasArgs()
			.optionalArg(true)
			.build();
	
	public static final Option DUMPCLOUD_OPTION = Option.builder()
			.desc("Dump objects from Cloud. Optionally specify object types: " + 
					MappingType.getMappingTypes(true))
			.option("dcloud")
			.longOpt("dumpCloud")
			.hasArgs()
			.optionalArg(true)
			.build();
	
	public static final Option MAPOBJECT_OPTION = Option.builder()
			.desc("Map Data Center and Cloud objects. Provide CSV exported from Cloud User Management, otherwise user is mapped based on display name")
			.option("m")
			.longOpt("mapObject")
			.hasArg()
			.optionalArg(true)
			.build();
	
	public static final Option EXACTMATCH_OPTION = Option.builder()
			.desc(	"Modifier for mapObject. " + 
					"If specified, only map objects with identical names. Default is to allow (migrated #).")
			.option("em")
			.longOpt("exactMatch")
			.build();
			
	public static final Option CREATEFILTER_OPTION = Option.builder()
			.desc(	"Create filters in Cloud. " + 
					"Optionally specify true/false to call/disable REST API calls. Default false." + 
					"When REST API calls are disabled, filters get remapped to file in a single batch.")
			.option("cf")
			.longOpt("createFilter")
			.hasArg()
			.optionalArg(true)
			.build();
	
	public static final Option OVERWRITEFILTER_OPTION = Option.builder()
			.desc(	"Modifier to createFilter, only applicable when REST API calls are not disabled. " + 
					"If specificed, overwrites filters that already exist in Cloud. " + 
					"Default is to not overwrite.")
			.option("of")
			.longOpt("overwriteFilter")
			.build();
	
	public static final Option DELETEFILTER_OPTION = Option.builder()
			.desc("Delete filters created in Cloud")
			.option("df")
			.longOpt("deleteFilter")
			.build();

	public static final Option LISTFILTER_OPTION = Option.builder()
			.desc("List filters in Cloud")
			.option("lf")
			.longOpt("listFilter")
			.build();

	public static final Option MAPDASHBOARD_OPTION = Option.builder()
			.desc("Map dashboards")
			.option("md")
			.longOpt("mapDashboard")
			.build();

	public static final Option CREATEDASHBOARD_OPTION = Option.builder()
			.desc("Create dashboards in Cloud")
			.option("cd")
			.longOpt("createDashboard")
			.build();

	public static final Option DELETEDASHBOARD_OPTION = Option.builder()
			.desc("Delete dashboards created in Cloud")
			.option("dd")
			.longOpt("deleteDashboard")
			.build();

	public static final Option LISTDASHBOARD_OPTION = Option.builder()
			.desc("List dashboards in Cloud")
			.option("ld")
			.longOpt("listDashboard")
			.build();

	public static final Options MAIN_OPTIONS = new Options()
			.addOption(CONFIG_OPTION)
			.addOption(DUMPDC_OPTION)
			.addOption(DUMPCLOUD_OPTION)
			.addOption(MAPOBJECT_OPTION)
			.addOption(EXACTMATCH_OPTION)
			.addOption(CREATEFILTER_OPTION)
			.addOption(OVERWRITEFILTER_OPTION)
			.addOption(DELETEFILTER_OPTION)
			.addOption(LISTFILTER_OPTION)
			.addOption(MAPDASHBOARD_OPTION)
			.addOption(CREATEDASHBOARD_OPTION)
			.addOption(DELETEDASHBOARD_OPTION)
			.addOption(LISTDASHBOARD_OPTION);
			
	public static final Option ROLE_OPTION = Option.builder()
			.desc("Role name(s)")
			.option("r")
			.longOpt("role")
			.required()
			.hasArgs()
			.build();
	public static final Option USER_OPTION = Option.builder()
			.desc("Account ID to grant/revoke project roles. If not specified, defaults to Cloud user in config")
			.option("u")
			.longOpt("user")
			.hasArg()
			.build();

	public static final Option GRANT_OPTION = Option.builder()
			.desc("Grant project roles in Cloud")
			.option("gr")
			.longOpt("grant")
			.required()
			.build();
	public static final Options GRANT_OPTIONS = new Options()
			.addOption(CONFIG_OPTION)
			.addOption(GRANT_OPTION)
			.addOption(ROLE_OPTION)
			.addOption(USER_OPTION);
	
	public static final Option REVOKE_OPTION = Option.builder()
			.desc("Revoke project roles in Cloud")
			.option("rr")
			.longOpt("revoke")
			.required()
			.build();
	public static final Options REVOKE_OPTIONS = new Options()
			.addOption(CONFIG_OPTION)
			.addOption(REVOKE_OPTION)
			.addOption(ROLE_OPTION)
			.addOption(USER_OPTION);
	
	public static void printHelp() {
		String command = "java -jar JiraDashboardMigrator-[Version].jar -c [config.json] ";
		HelpFormatter hf = new HelpFormatter();
		hf.printHelp(command + " [other options to be executed in sequence]", MAIN_OPTIONS);
		hf.printHelp(command + " -gr -r [Role names]", GRANT_OPTIONS);
		hf.printHelp(command + " -rr -r [Role names]", REVOKE_OPTIONS);
	}
	
	public static CommandLine parseCommandLine(String[] args) {
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			return parser.parse(MAIN_OPTIONS, args);
		} catch (Exception ex) {
			// Ignore
		}
		try {
			return parser.parse(GRANT_OPTIONS, args);
		} catch (Exception ex) {
			// Ignore
		}
		try {
			return parser.parse(REVOKE_OPTIONS, args);
		} catch (Exception ex) {
			// Ignore
		}
		printHelp();
		return null;
	}
}
