package com.igsl;

import java.util.Comparator;

import com.igsl.model.DataCenterPortletConfiguration;

/**
 * Sort gadgets based on position (for creating gadgets in order). 
 * Or sort gadgets based on numerical id (for easy text compare). 
 */
public class GadgetOrderComparator implements Comparator<DataCenterPortletConfiguration> {

	private static Comparator<Integer> integerComparator = Comparator.naturalOrder();

	private boolean byPosition = false;
	
	public GadgetOrderComparator(boolean byPosition) {
		this.byPosition = byPosition;
	}
	
	@Override
	public int compare(DataCenterPortletConfiguration o1, DataCenterPortletConfiguration o2) {
		if (o1 != null && o2 == null) {
			return 1;
		}
		if (o1 == null && o2 != null) {
			return -1;
		}
		if (byPosition) {
			if (o1.getPositionSeq() != o2.getPositionSeq()) {
				return integerComparator.compare(o1.getPositionSeq(), o2.getPositionSeq());
			}
			return integerComparator.compare(o1.getColumnNumber(), o2.getColumnNumber());
		} else {
			return integerComparator.compare(o1.getId(), o2.getId());
		}
	}

}
