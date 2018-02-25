package fr.neamar.kiss;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import fr.neamar.kiss.broadcast.IncomingCallHandler;
import fr.neamar.kiss.broadcast.IncomingSmsHandler;
import fr.neamar.kiss.dataprovider.AppProvider;
import fr.neamar.kiss.dataprovider.SearchProvider;
import fr.neamar.kiss.utils.PackageManagerUtils;

@SuppressWarnings("FragmentInjection")
public class SettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    // Those settings require the app to restart
    final static private String requireRestartSettings = "force-portrait primary-color transparent-search transparent-favorites history-hide enable-favorites-bar theme notification-bar-color";

    private boolean requireFullRestart = false;

    private SharedPreferences prefs;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = prefs.getString("theme", "light");
        if (theme.contains("dark")) {
            setTheme(R.style.SettingThemeDark);
        }

        // Lock launcher into portrait mode
        // Do it here to make the transition as smooth as possible
        if (prefs.getBoolean("force-portrait", true)) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        }

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        ListPreference iconsPack = (ListPreference) findPreference("icons-pack");
        setListPreferenceIconsPacksData(iconsPack);

        fixSummaries();

        addExcludedAppSettings(prefs);

        addCustomSearchProvidersPreferences(prefs);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if(item.getItemId() == R.id.help) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("http://help.kisslauncher.com"));
            startActivity(intent);
            return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private void loadExcludedAppsToPreference(MultiSelectListPreference multiSelectList) {
        String excludedAppList = prefs.getString("excluded-apps-list", "").replace(this.getPackageName() + ";", "");
        String[] apps = excludedAppList.split(";");

        multiSelectList.setEntries(apps);
        multiSelectList.setEntryValues(apps);
        //noinspection Convert2Diamond
        multiSelectList.setValues(new HashSet<String>(Arrays.asList(apps)));
    }

    private boolean hasNoExcludedApps(final SharedPreferences prefs) {
        String excludedAppList = prefs.getString("excluded-apps-list", "").replace(this.getPackageName() + ";", "");
        return excludedAppList.isEmpty();
    }

    @SuppressWarnings("deprecation")
    private void addExcludedAppSettings(final SharedPreferences prefs) {
        final MultiSelectListPreference multiPreference = new MultiSelectListPreference(this);
        multiPreference.setTitle(R.string.ui_excluded_apps);
        multiPreference.setDialogTitle(R.string.ui_excluded_apps_dialog_title);
        multiPreference.setKey("excluded_apps_ui");
        multiPreference.setOrder(15);
        PreferenceGroup category = (PreferenceGroup) findPreference("history_category");
        category.addPreference(multiPreference);

        loadExcludedAppsToPreference(multiPreference);
        multiPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Set<String> appListToBeExcluded = (HashSet<String>) newValue;

                StringBuilder builder = new StringBuilder();
                for (String s : appListToBeExcluded) {
                    builder.append(s).append(";");
                }

                prefs.edit().putString("excluded-apps-list", builder.toString() + SettingsActivity.this.getPackageName() + ";").apply();
                loadExcludedAppsToPreference(multiPreference);
                if (hasNoExcludedApps(prefs)) {
                    multiPreference.setDialogMessage(R.string.ui_excluded_apps_not_found);
                }

                final AppProvider provider = KissApplication.getApplication(SettingsActivity.this).getDataHandler().getAppProvider();
                if (provider != null) {
                    provider.reload();
                }

                return false;
            }
        });
        if (hasNoExcludedApps(prefs)) {
            multiPreference.setDialogMessage(R.string.ui_excluded_apps_not_found);
        }
    }

    private void addCustomSearchProvidersPreferences(SharedPreferences prefs) {
        removeSearchProviderSelect();
        removeSearchProviderDelete();
        addCustomSearchProvidersSelect(prefs);
        addCustomSearchProvidersDelete(prefs);
    }

    private void removeSearchProviderSelect() {
        PreferenceGroup category = (PreferenceGroup) findPreference("providers");
        Preference pref = findPreference("selected-search-provider-names");
        if (pref != null) {
            category.removePreference(pref);
        }
    }

    private void removeSearchProviderDelete() {
        PreferenceGroup category = (PreferenceGroup) findPreference("providers");
        Preference pref = findPreference("deleting-search-providers-names");
        if (pref != null) {
            category.removePreference(pref);
        }
    }

    private void addCustomSearchProvidersSelect(SharedPreferences prefs) {
        MultiSelectListPreference multiPreference = new MultiSelectListPreference(this);
        //get stored search providers or default hard-coded values
        Set<String> availableSearchProviders = prefs.getStringSet("available-search-providers", SearchProvider.getSearchProviders(this));
        String[] searchProvidersArray = new String[availableSearchProviders.size()];
        int pos = 0;
        //get names of search providers
        for (String searchProvider : availableSearchProviders) {
            searchProvidersArray[pos++] = searchProvider.split("\\|")[0];
        }
        String search_providers_title = this.getString(R.string.search_providers_title);
        multiPreference.setTitle(search_providers_title);
        multiPreference.setDialogTitle(search_providers_title);
        multiPreference.setKey("selected-search-provider-names");
        multiPreference.setEntries(searchProvidersArray);
        multiPreference.setEntryValues(searchProvidersArray);
        multiPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            @SuppressWarnings("unchecked")
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                final SearchProvider provider = KissApplication.getApplication(SettingsActivity.this).getDataHandler().getSearchProvider();
                if (provider != null) {
                    provider.reload();
                }
                return true;
            }
        });

        PreferenceGroup category = (PreferenceGroup) findPreference("providers");
        category.addPreference(multiPreference);
    }

    private void addCustomSearchProvidersDelete(final SharedPreferences prefs) {
        MultiSelectListPreference multiPreference = new MultiSelectListPreference(this);

        Set<String> availableSearchProviders = prefs.getStringSet("available-search-providers", SearchProvider.getSearchProviders(this));
        String[] searchProvidersArray = new String[availableSearchProviders.size()];
        int pos = 0;
        //get names of search providers
        for (String searchProvider : availableSearchProviders) {
            searchProvidersArray[pos++] = searchProvider.split("\\|")[0];
        }
        multiPreference.setEnabled(availableSearchProviders.size() > 0);
        String search_providers_title = this.getString(R.string.search_providers_delete);
        multiPreference.setTitle(search_providers_title);
        multiPreference.setDialogTitle(search_providers_title);
        multiPreference.setKey("deleting-search-providers-names");
        multiPreference.setEntries(searchProvidersArray);
        multiPreference.setEntryValues(searchProvidersArray);
        PreferenceGroup category = (PreferenceGroup) findPreference("providers");

        multiPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Set<String> searchProvidersToDelete = (Set<String>) newValue;//PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getStringSet("deleting-search-providers-names", new HashSet<String>());
                Set<String> availableSearchProviders = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getStringSet("available-search-providers", SearchProvider.getSearchProviders(SettingsActivity.this));

                Set<String> updatedProviders = new HashSet<>(PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this).getStringSet("available-search-providers", SearchProvider.getSearchProviders(SettingsActivity.this)));

                for (String searchProvider : availableSearchProviders) {
                    for (String providerToDelete : searchProvidersToDelete) {
                        if (searchProvider.startsWith(providerToDelete + "|")) {
                            updatedProviders.remove(searchProvider);
                        }
                    }
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putStringSet("available-search-providers", updatedProviders);
                editor.putStringSet("deleting-search-providers-names", updatedProviders);
                editor.apply();

                if (searchProvidersToDelete.size() > 0) {
                    Toast.makeText(SettingsActivity.this, R.string.search_provider_deleted, Toast.LENGTH_LONG).show();
                }

                // Reload search list
                final SearchProvider provider = KissApplication.getApplication(SettingsActivity.this).getDataHandler().getSearchProvider();
                if (provider != null) {
                    provider.reload();
                }
                return true;
            }
        });

        category.addPreference(multiPreference);
    }

    @Override
    public void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equalsIgnoreCase("available-search-providers")) {
            addCustomSearchProvidersPreferences(prefs);
        } else if (key.equalsIgnoreCase("icons-pack")) {
            KissApplication.getApplication(this).getIconsHandler().loadIconsPack(sharedPreferences.getString(key, "default"));
        } else if (key.equalsIgnoreCase("sort-apps")) {
            // Reload application list
            final AppProvider provider = KissApplication.getApplication(this).getDataHandler().getAppProvider();
            if (provider != null) {
                provider.reload();
            }
        } else if (key.equalsIgnoreCase("enable-sms-history")) {
            PackageManagerUtils.enableComponent(this, IncomingSmsHandler.class, sharedPreferences.getBoolean(key, false));
        } else if (key.equalsIgnoreCase("enable-phone-history")) {
            PackageManagerUtils.enableComponent(this, IncomingCallHandler.class, sharedPreferences.getBoolean(key, false));
        }

        if (requireRestartSettings.contains(key)) {
            requireFullRestart = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        // Some settings require a full UI refresh,
        // Flag this, so that MainActivity get the information onResume().
        if (requireFullRestart) {
            prefs.edit().putBoolean("require-layout-update", true).apply();
        }

    }

    @SuppressWarnings("deprecation")
    private void fixSummaries() {
        int historyLength = KissApplication.getApplication(this).getDataHandler().getHistoryLength();
        if (historyLength > 5) {
            findPreference("reset").setSummary(String.format(getString(R.string.items_title), historyLength));
        }

        // Only display "rate the app" preference if the user has been using KISS long enough to enjoy it ;)
        Preference rateApp = findPreference("rate-app");
        if (historyLength < 300) {
            getPreferenceScreen().removePreference(rateApp);
        } else {
            rateApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=" + getApplicationContext().getPackageName()));
                    startActivity(intent);

                    return true;
                }
            });
        }
    }

    protected void setListPreferenceIconsPacksData(ListPreference lp) {
        IconsHandler iph = KissApplication.getApplication(this).getIconsHandler();

        CharSequence[] entries = new CharSequence[iph.getIconsPacks().size() + 1];
        CharSequence[] entryValues = new CharSequence[iph.getIconsPacks().size() + 1];

        int i = 0;
        entries[0] = this.getString(R.string.icons_pack_default_name);
        entryValues[0] = "default";
        for (String packageIconsPack : iph.getIconsPacks().keySet()) {
            entries[++i] = iph.getIconsPacks().get(packageIconsPack);
            entryValues[i] = packageIconsPack;
        }

        lp.setEntries(entries);
        lp.setDefaultValue("default");
        lp.setEntryValues(entryValues);
    }
}
