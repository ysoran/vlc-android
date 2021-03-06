/*****************************************************************************
 * PreferencesActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

public class PreferencesActivity extends PreferenceActivity {

    public final static String TAG = "VLC/PreferencesActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Create onClickListen
        Preference directoriesPref = (Preference) findPreference("directories");
        directoriesPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(MainActivity.getInstance(),
                                BrowserActivity.class);
                        startActivity(intent);
                        return true;
                    }
                });

        // Create onClickListen
        Preference clearHistoryPref = (Preference) findPreference("clear_history");
        clearHistoryPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    public boolean onPreferenceClick(Preference preference) {
                        new AlertDialog.Builder(PreferencesActivity.this)
                                .setTitle(R.string.clear_history)
                                .setMessage(R.string.validation)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        DatabaseManager db = DatabaseManager.getInstance();
                                        db.clearSearchhistory();
                                    }
                                })

                                .setNegativeButton(android.R.string.cancel, null).show();
                        return true;
                    }
                });

        // Headset detection option
        CheckBoxPreference checkboxHS = (CheckBoxPreference) findPreference("enable_headset_detection");
        checkboxHS.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        CheckBoxPreference checkboxHS = (CheckBoxPreference) preference;
                        AudioServiceController.getInstance().detectHeadset(checkboxHS.isChecked());
                        return true;
                    }
                });

        // Attach debugging items
        Preference quitAppPref = (Preference) findPreference("quit_app");
        quitAppPref.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {

                    public boolean onPreferenceClick(Preference preference) {
                    	System.exit(0);
                        return true;
                    }
                });
    }

}
