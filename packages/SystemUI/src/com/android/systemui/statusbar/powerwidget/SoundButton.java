/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SoundButton extends PowerButton {

    private static final String TAG = "SoundButton";

    private static final int VIBRATE_DURATION = 250; // 0.25s

    private static final IntentFilter INTENT_FILTER = new IntentFilter();
    static {
        INTENT_FILTER.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
    }

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.EXPANDED_RING_MODE));
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING));
    }

    private final Ringer mSilentRinger = new Ringer(AudioManager.RINGER_MODE_SILENT, false);
    private final Ringer mVibrateRinger = new Ringer(AudioManager.RINGER_MODE_VIBRATE, true);
    private final Ringer mSoundRinger = new Ringer(AudioManager.RINGER_MODE_NORMAL, false);
    private final Ringer mSoundVibrateRinger = new Ringer(AudioManager.RINGER_MODE_NORMAL, true);
    private final Ringer[] mRingers = new Ringer[] {
            mSilentRinger, mVibrateRinger, mSoundRinger, mSoundVibrateRinger
    };

    private int mRingersIndex = 2;

    private int[] mRingerValues = new int[] {
            0, 1, 2, 3
    };
    private int mRingerValuesIndex = 2;

    private AudioManager mAudioManager;

    public SoundButton() {
        mType = BUTTON_SOUND;
    }

    @Override
    protected void setupButton(View view) {
        super.setupButton(view);
        if (mView != null) {
            ensureAudioManager(mContext);
            updateSettings(mContext.getContentResolver());
            update(mContext);
        }
    }

    @Override
    protected void updateState(Context context) {
        findCurrentState();
        switch (mRingersIndex) {
            case 0:
                mIcon = R.drawable.stat_silent;
                mState = STATE_DISABLED;
                break;
            case 1:
                mIcon = R.drawable.stat_vibrate_off;
                mState = STATE_DISABLED;
                break;
            case 2:
                mIcon = R.drawable.stat_ring_on;
                mState = STATE_ENABLED;
                break;
            case 3:
                mIcon = R.drawable.stat_ring_vibrate_on;
                mState = STATE_ENABLED;
                break;
        }
        for (int i = 0; i < mRingerValues.length; i++) {
            if (mRingersIndex == mRingerValues[i]) {
                mRingerValuesIndex = i;
                break;
            }
        }
    }

    @Override
    protected void toggleState(Context context) {
        mRingerValuesIndex++;
        if (mRingerValuesIndex > mRingerValues.length - 1) {
            mRingerValuesIndex = 0;
        }
        mRingersIndex = mRingerValues[mRingerValuesIndex];
        if (mRingersIndex > mRingers.length - 1) {
            mRingersIndex = 0;
        }
        ensureAudioManager(mContext);
        Ringer ringer = mRingers[mRingersIndex];
        ringer.execute(context);
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.SOUND_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    protected void onReceive(Context context, Intent intent) {
        update(mContext);
    }

    @Override
    protected void onChangeUri(ContentResolver cr, Uri uri) {
        updateSettings(cr);
        updateState(mContext);
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        return INTENT_FILTER;
    }

    private void updateSettings(ContentResolver resolver) {
        String[] modes = parseStoredValue(Settings.System.getString(
                resolver, Settings.System.EXPANDED_RING_MODE));
        if (modes == null || modes.length == 0) {
            mRingerValues = new int[] {
                    0, 1, 2, 3
            };
        } else {
            mRingerValues = new int[modes.length];
            for (int i = 0; i < modes.length; i++) {
                mRingerValues[i] = Integer.valueOf(modes[i]);
            }
        }
    }

    private void findCurrentState() {
        ContentResolver resolver = mContext.getContentResolver();
        boolean vibrateWhenRinging = Settings.System.getInt(resolver,
                Settings.System.VIBRATE_WHEN_RINGING, 0) == 1;
        int ringerMode = mAudioManager.getRingerMode();
        Log.e("RingerModeTile","ringerMode = "+ringerMode+"\r\n");
        Ringer ringer = new Ringer(ringerMode, vibrateWhenRinging);
        for (int i = 0; i < mRingers.length; i++) {
            if (mRingers[i].equals(ringer)) {
                mRingersIndex = i;
                break;
            }
        }
    }

    private void ensureAudioManager(Context context) {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
    }

    private class Ringer {
        final boolean mVibrateWhenRinging;
        final int mRingerMode;

        Ringer( int ringerMode, boolean vibrateWhenRinging) {
            mVibrateWhenRinging = vibrateWhenRinging;
            mRingerMode = ringerMode;
        }

        void execute(Context context) {
            // If we are setting a vibrating state, vibrate to indicate it
            if (mVibrateWhenRinging) {
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(VIBRATE_DURATION);
            }

            // Set the desired state
            ContentResolver resolver = context.getContentResolver();
            Settings.System.putInt(resolver, Settings.System.VIBRATE_WHEN_RINGING,
                    (mVibrateWhenRinging ? 1 : 0));
            mAudioManager.setRingerMode(mRingerMode);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o.getClass() != getClass()) {
                return false;
            }
            Ringer r = (Ringer) o;
            if (r.mRingerMode == AudioManager.RINGER_MODE_SILENT && this.mRingerMode == AudioManager.RINGER_MODE_SILENT) return true;
            else if (r.mRingerMode == AudioManager.RINGER_MODE_VIBRATE && this.mRingerMode == AudioManager.RINGER_MODE_VIBRATE) return true;
            else return r.mVibrateWhenRinging == mVibrateWhenRinging
                    && r.mRingerMode == mRingerMode;
        }
    }
}
