package com.craiovadata.guessthecountry;

import android.util.Log;

import java.util.Random;

import static com.craiovadata.guessthecountry.MainActivity.TAG;

class Utils {

    static void log(String s) {
        if (BuildConfig.DEBUG)
            Log.w(TAG, s);
    }

    static String getAdUnitId_interstitial(Random random) {
        int r = random.nextInt(10);
        if (r == 0)
            return "ca-app-pub-3052455927658337/3812605089";
        else if (r < 3)
            return "ca-app-pub-1015344817183694/6453535707";
        return "ca-app-pub-3931793949981809/5906740796";

    }
}
