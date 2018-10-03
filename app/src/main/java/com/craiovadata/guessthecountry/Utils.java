package com.craiovadata.guessthecountry;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.Log;

import java.util.Random;

import static com.craiovadata.guessthecountry.MainActivity.TAG;

public class Utils {
    static int i_ext = 0;

    public static void printCountries(Context context, TypedArray countries) {
        if (!BuildConfig.DEBUG)
            return;


        String log = "\n ", log_refs = "";

        for (int i = 0; i < 30; i++) {

            if (i_ext + i >= countries.length()) {
                Log.d(TAG, log);
//                Log.d(TAG, log_refs);
                return;
            }

            int id = countries.getResourceId(i_ext + i, 0);
            String[] country = context.getResources().getStringArray(id);

            log += "\n<string-array name=\"country_" + country[0].toLowerCase() + "\">" +
                    "<item>" + country[0] + "</item>" +
                    "<item>" + country[1] + "</item>" +
                    "<item>@drawable/zz_" + country[0].toLowerCase() + "</item>" +
                    "</string-array>";

//            log_refs += "<item>@array/country_" + country[CODE].toLowerCase() + "</item>\n";
//                    <item>@array/country_173</item>

        }
        i_ext += 30;

        Log.d(TAG, log);
//        Log.d(TAG, log_refs);
//        printCountries(countries);
    }


    static void log(String s) {
        if (!BuildConfig.DEBUG)
            return;
        Log.e(TAG, s);
    }

    static String getAdUnitId(Random random) {
        int r = random.nextInt(10);
        if (r == 0)
            return "ca-app-pub-3052455927658337/3812605089";
        else if (r < 3)
            return "ca-app-pub-1015344817183694/6453535707";
        return "ca-app-pub-3931793949981809/5906740796";

    }
}
