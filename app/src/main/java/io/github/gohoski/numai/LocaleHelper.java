package io.github.gohoski.numai;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

final class LocaleHelper {
    private static final String PREFS_NAME = "numAi";
    private static final String KEY_APP_LANGUAGE = "appLanguage";

    private LocaleHelper() {}

    static Context wrap(Context context) {
        return updateResources(context, getStoredLanguage(context));
    }

    static void applyAppLocale(Context context) {
        updateResources(context, getStoredLanguage(context));
    }

    static String getStoredLanguage(Context context) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_APP_LANGUAGE, "");
    }

    static String normalizeLanguage(String language) {
        if (language == null || language.trim().length() == 0) {
            return "";
        }
        return language.toLowerCase(Locale.US);
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = language == null || language.length() == 0
                ? Resources.getSystem().getConfiguration().locale
                : new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }
}
