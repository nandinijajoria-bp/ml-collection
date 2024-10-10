package com.bharatpe.lending.collection.core.constant;
import java.util.HashMap;
import java.util.Map;

public interface ExcessConstants {

	public static final int DEFAULT_EXCESS_PRIORITY = 999;

	// NOTE -> for each mode descrption must be added
	Map<String , Integer> ExcessCollectionPriorityMap = new HashMap<String , Integer>() {{
		put("NACH", 10);
		put("UPI_AUTO_PAY", 20);
		put("FP", 30);
		put("UPI", 40);
		put("NB", 50);
		put("DC", 60);
		put("SETTLEMENT", 70);
	}};
	Map<String , String> ExcessCollectionAdjustmentModeDescription = new HashMap<String , String>() {{
		put("NACH", "EXCESS_NACH_ADJUSTED");
		put("UPI_AUTO_PAY", "AUTO_PAY_UPI_EXCESS_ADJUSTED");
		put("FP", "EXCESS_ADJUSTED_FP");
		put("UPI", "EXCESS_ADJUSTED_UPI");
		put("NB", "EXCESS_ADJUSTED_NB");
		put("DC", "EXCESS_ADJUSTED_DC");
		put("SETTLEMENT", "EXCESS_ADJUSTED_Settlement");
	}};

}
