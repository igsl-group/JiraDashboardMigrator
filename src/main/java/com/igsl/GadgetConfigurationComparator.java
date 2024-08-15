package com.igsl;

import java.util.Comparator;

import com.igsl.model.DataCenterGadgetConfiguration;

/**
 * Sort gadget config based on id 
 */
public class GadgetConfigurationComparator implements Comparator<DataCenterGadgetConfiguration> {

	private static Comparator<Integer> integerComparator = Comparator.naturalOrder();
	
	@Override
	public int compare(DataCenterGadgetConfiguration o1, DataCenterGadgetConfiguration o2) {
		if (o1 != null && o2 == null) {
			return 1;
		}
		if (o1 == null && o2 != null) {
			return -1;
		}
		return integerComparator.compare(o1.getId(), o2.getId());
	}

}
