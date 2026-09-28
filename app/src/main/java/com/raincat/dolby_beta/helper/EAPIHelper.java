package com.raincat.dolby_beta.helper;

import org.json.JSONObject;

import java.util.regex.Pattern;

public class EAPIHelper {

    public static String modifyPlayer(String original) {
        try {
            JSONObject body = new JSONObject(original);
            org.json.JSONArray data = body.optJSONArray("data");
            if (data == null)
                return original;

            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null)
                    continue;

                item.remove("freeTrialInfo");
                item.remove("freeTimeTrialPrivilege");
                item.remove("freeTrialPrivilege");
                item.remove("trialMode");
                item.remove("trialModes");

                int flag = item.optInt("flag");
                if ((flag & 0x8) == 0) {
                    item.put("fee", 0);
                    item.put("flag", 0);
                    item.put("payed", 0);
                } else {
                    item.put("fee", 0);
                    item.put("payed", 0);
                    item.put("flag", flag & 0x8);
                }

            }
            return body.toString();
        } catch (Throwable t) {
            com.raincat.dolby_beta.xposed.XposedCompat.log("EAPIHelper modifyPlayer failed");
            com.raincat.dolby_beta.xposed.XposedCompat.log(t);
            return original;
        }
    }

    public static String modifyEffect(String originalContent) {
        originalContent = Pattern.compile("\"type\":\\d+").matcher(originalContent).replaceAll("\"type\":1");
        return originalContent;
    }

}
