package com.igsl.config.brokenbuild;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.igsl.Log;
import com.igsl.config.CustomGadgetConfigMapper;
import com.igsl.model.DataCenterGadgetConfiguration;
import com.igsl.model.DataCenterPortletConfiguration;
import com.igsl.model.mapping.Mapping;
import com.igsl.model.mapping.MappingType;

/**
 * Velocity charts by Broken Build
 */
public class VelocityCharts extends CustomGadgetConfigMapper {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final String CONF_KEY = "config";
	private static final ObjectMapper OM = new ObjectMapper();
	
	@Override
	public void process(DataCenterPortletConfiguration gadget, Map<MappingType, Mapping> mappings) 
			throws Exception {
		ObjectReader reader = OM.readerFor(VelocityChartConfig.class);
		for (DataCenterGadgetConfiguration conf : gadget.getGadgetConfigurations()) {
			if (CONF_KEY.equals(conf.getUserPrefKey())) {
				// Parse the value into object
				VelocityChartConfig config = reader.readValue(conf.getUserPrefValue());
				Log.info(LOGGER, "Before: " + OM.writeValueAsString(config));
				// Remap items
				// estimationField
				String estimationField = config.getEstimationField();
				if (mappings.get(MappingType.CUSTOM_FIELD).getMapped().containsKey(estimationField)) {
					config.setEstimationField(
							mappings.get(MappingType.CUSTOM_FIELD).getMapped().get(estimationField));
				}
				// sprintDateField
				String sprintDateField = config.getSprintDateField();
				if (mappings.get(MappingType.CUSTOM_FIELD).getMapped().containsKey(sprintDateField)) {
					config.setSprintDateField(
							mappings.get(MappingType.CUSTOM_FIELD).getMapped().get(sprintDateField));
				}
				// boardId
				String boardId = String.valueOf(config.getBoardId());
				if (mappings.get(MappingType.AGILE_BOARD).getMapped().containsKey(boardId)) {
					config.setBoardId(
							Integer.parseInt(
									mappings.get(MappingType.AGILE_BOARD).getMapped().get(boardId)));
				}
				// boards
				for (Board b : config.getBoards()) {
					// estimationField
					estimationField = b.getEstimationField();
					if (mappings.get(MappingType.CUSTOM_FIELD).getMapped().containsKey(estimationField)) {
						b.setEstimationField(
								mappings.get(MappingType.CUSTOM_FIELD).getMapped().get(estimationField));
					}	
					// customDoneStatuses
					List<String> statuses = new ArrayList<>();
					for (String status : b.getCustomDoneStatuses()) {
						if (mappings.get(MappingType.STATUS).getMapped().containsKey(status)) {
							statuses.add(mappings.get(MappingType.STATUS).getMapped().get(status));
						} else {
							statuses.add(status);
						}
					}
					b.setCustomDoneStatuses(statuses);
					// boardId
					boardId = String.valueOf(b.getBoardId());
					if (mappings.get(MappingType.AGILE_BOARD).getMapped().containsKey(boardId)) {
						b.setBoardId(
								Integer.parseInt(
										mappings.get(MappingType.AGILE_BOARD).getMapped().get(boardId)));
					}
				}
				// Update value
				conf.setUserPrefValue(OM.writeValueAsString(config));
				Log.info(LOGGER, "After: " + OM.writeValueAsString(config));
			}
		}
	}

}
