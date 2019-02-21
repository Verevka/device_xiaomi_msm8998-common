/*
* Copyright (C) 2016 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/

package org.omnirom.device;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v14.preference.PreferenceFragment;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import org.omnirom.device.R;
import org.omnirom.device.utils.FileUtils;

public class DeviceSettings extends PreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String KEY_CATEGORY_DISPLAY = "display";
    private static final String KEY_CATEGORY_HW_BUTTONS = "hw_buttons";
    private static final String KEY_CATEGORY_USB_FASTCHARGE = "usb_fastcharge";

    private static final String SPECTRUM_KEY = "spectrum";

    private static final String SPECTRUM_SYSTEM_PROPERTY = "persist.spectrum.profile";

    public static final String S2S_KEY = "sweep2sleep";
    public static final String KEY_VIBSTRENGTH = "vib_strength";
    public static final String KEY_S2S_VIBSTRENGTH = "s2s_vib_strength";
    public static final String FILE_S2S_TYPE = "/sys/sweep2sleep/sweep2sleep";

    public static final String BUTTONS_SWAP_KEY = "buttons_swap";
    public static final String BUTTONS_SWAP_PATH = "/proc/touchpanel/reversed_keys_enable";

    public static final String USB_FASTCHARGE_KEY = "fastcharge";
    public static final String USB_FASTCHARGE_PATH = "/sys/kernel/fast_charge/force_fast_charge";

    private static final String KEY_CATEGORY_CAM_SWITCHER = "cam_switcher_cat";
    public static final String KEY_CAM_SWITCHER = "cam_switcher";

    private VibratorStrengthPreference mVibratorStrength;
    private S2SVibratorStrengthPreference mVibratorStrengthS2S;
    private ListPreference mS2S;
    private Preference mKcalPref;
    private ListPreference mSPECTRUM;
    private SwitchPreference mButtonSwap;
    private PreferenceCategory mHWButtons;
    private SwitchPreference mFastcharge;
    private PreferenceCategory mUsbFastcharge;
    private PreferenceCategory mCamSwitcherCat;
    private ListPreference mCamSwitcher;

    private CharSequence[] mValues;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.main, rootKey);

        PreferenceScreen prefSet = getPreferenceScreen();

        mKcalPref = findPreference("kcal");
        mKcalPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getContext(), DisplayCalibration.class);
                startActivity(intent);
                return true;
            }
        });

        mS2S = (ListPreference) findPreference(S2S_KEY);
        mS2S.setValue(FileUtils.getFileValue(FILE_S2S_TYPE, "0"));
        mS2S.setOnPreferenceChangeListener(this);

        if (FileUtils.isFileWritable(BUTTONS_SWAP_PATH)) {
            mButtonSwap = (SwitchPreference) findPreference(BUTTONS_SWAP_KEY);
            mButtonSwap.setChecked(FileUtils.getFileValueAsBoolean(BUTTONS_SWAP_PATH, false));
            mButtonSwap.setOnPreferenceChangeListener(this);
        } else {
            mHWButtons = (PreferenceCategory) prefSet.findPreference("hw_buttons");
            prefSet.removePreference(mHWButtons);
        }

        if (FileUtils.isFileWritable(USB_FASTCHARGE_PATH)) {
          mFastcharge = (SwitchPreference) findPreference(USB_FASTCHARGE_KEY);
          mFastcharge.setChecked(FileUtils.getFileValueAsBoolean(USB_FASTCHARGE_PATH, false));
          mFastcharge.setOnPreferenceChangeListener(this);
        } else {
          mUsbFastcharge = (PreferenceCategory) prefSet.findPreference("usb_fastcharge");
          prefSet.removePreference(mUsbFastcharge);
        }

        mSPECTRUM = (ListPreference) findPreference(SPECTRUM_KEY);
        if( mSPECTRUM != null ) {
            mSPECTRUM.setValue(SystemProperties.get(SPECTRUM_SYSTEM_PROPERTY, "0"));
            mSPECTRUM.setOnPreferenceChangeListener(this);
        }

        mVibratorStrength = (VibratorStrengthPreference) findPreference(KEY_VIBSTRENGTH);
        if (mVibratorStrength != null) {
            mVibratorStrength.setEnabled(VibratorStrengthPreference.isSupported());
        }

        mVibratorStrengthS2S = (S2SVibratorStrengthPreference) findPreference(KEY_S2S_VIBSTRENGTH);
        if (mVibratorStrengthS2S != null) {
            mVibratorStrengthS2S.setEnabled(S2SVibratorStrengthPreference.isSupported());
        }

        mCamSwitcher = (ListPreference) findPreference(KEY_CAM_SWITCHER);
        if( mCamSwitcher != null && CamSwitcherNotOneCam() && isGappsInstalled()) {
            mCamSwitcher.setOnPreferenceChangeListener(this);
            mCamSwitcher.setSummary(mCamSwitcher.getEntry());
        } else {
            mCamSwitcherCat = (PreferenceCategory) prefSet.findPreference(KEY_CATEGORY_CAM_SWITCHER);
            prefSet.removePreference(mCamSwitcherCat);
        }
    }

    public static void restoreSpectrumProp(Context context) {
        String spectrumStoredValue = PreferenceManager.getDefaultSharedPreferences(context).getString(SPECTRUM_KEY, "0");
        SystemProperties.set(SPECTRUM_SYSTEM_PROPERTY, spectrumStoredValue);
    }

    private void setButtonSwap(boolean value) {
        FileUtils.writeValue(BUTTONS_SWAP_PATH, value ? "1" : "0");
    }

    private void setFastcharge(boolean value) {
        FileUtils.writeValue(USB_FASTCHARGE_PATH, value ? "1" : "0");
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();

        boolean value;
        String strvalue;

        if (S2S_KEY.equals(key)) {
            strvalue = (String) newValue;
            FileUtils.writeValue(FILE_S2S_TYPE, strvalue);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putString(S2S_KEY, strvalue);
            editor.commit();
            return true;
        } else if (SPECTRUM_KEY.equals(key)) {
            strvalue = (String) newValue;
            SystemProperties.set(SPECTRUM_SYSTEM_PROPERTY, strvalue);
            return true;
        } else if (BUTTONS_SWAP_KEY.equals(key)) {
            value = (Boolean) newValue;
            mButtonSwap.setChecked(value);
            setButtonSwap(value);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putBoolean(BUTTONS_SWAP_KEY, value);
            editor.commit();
            return true;
        } else if (USB_FASTCHARGE_KEY.equals(key)) {
            value = (Boolean) newValue;
            mFastcharge.setChecked(value);
            setFastcharge(value);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putBoolean(USB_FASTCHARGE_KEY, value);
            editor.commit();
            return true;
        } else if (KEY_CAM_SWITCHER.equals(key)) {
            strvalue = (String) newValue;
            int index = mCamSwitcher.findIndexOfValue(strvalue);
            mCamSwitcher.setSummary(mCamSwitcher.getEntries()[index]);
            CamSwitcher(index);
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putString(KEY_CAM_SWITCHER, strvalue);
            editor.commit();
            return true;
        }
        return true;
    }

    private boolean isAppInstalled(String uri) {
        PackageManager pm = getContext().getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
        }
        return false;
    }

    // If only one (or less) camera is installed, then we return false
    private boolean CamSwitcherNotOneCam() {
        int mCount = 0;
        mValues = mCamSwitcher.getEntryValues();
        // (mValues.length - 1) - because the last value - "all"
        for (int i = 0; i < mValues.length - 1; ++i) {
            if (isAppInstalled(mValues[i].toString())) {
                mCount++;
            }
        }
        if ( mCount <= 1 ) {
            return false;
        }
        return true;
    }

    private boolean isGappsInstalled() {
        return isAppInstalled("com.google.android.gms");
    }

    private void CamSwitcher(int index) {
        PackageManager pm = getContext().getPackageManager();
        mValues = mCamSwitcher.getEntryValues();

        if (index == (mValues.length - 1)) {
            for (int i = 0; i < mValues.length - 1; ++i) {
                pm.setApplicationEnabledSetting(mValues[i].toString(),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            }
        } else {
            for (int i = 0; i < mValues.length - 1; ++i) {
                if (mValues[i].toString() == mValues[index].toString()) {
                    pm.setApplicationEnabledSetting(mValues[i].toString(),
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
                } else {
                    pm.setApplicationEnabledSetting(mValues[i].toString(),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
                }
            }
        }
    }
}
