package com.bharatpe.lending.service.merchant.util;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Util {

    public static String partialUpdatePayload(List<Map<String, String>> data) {
        Map<String, String> temp = new HashMap<>();
        for (Map<String, String> map : data) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String value = !temp.containsKey(entry.getKey())
                        ? entry.getValue()
                        : String.format("%s%s%s", temp.get(entry.getKey()), "|", entry.getValue());
                temp.put(entry.getKey(), value);
            }
        }
        Map<String, Object> sortedMap = new TreeMap<String, Object>(temp);
        return StringUtils.collectionToDelimitedString(sortedMap.values(), "|");
    }
}
