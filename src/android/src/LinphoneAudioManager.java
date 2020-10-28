package com.sip.linphone;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Vibrator;

import org.linphone.core.Address;

import java.io.FileInputStream;
import java.io.IOException;

import static android.media.AudioManager.MODE_RINGTONE;
import static android.media.AudioManager.STREAM_RING;

class LinphoneAudioManager {
    private static final String TAG = "LinphoneSip";

    private Context mContext;
    private AudioManager mAudioManager;
    private MediaPlayer mRingerPlayer;
    private final Vibrator mVibrator;

    private boolean mIsRinging;
    private boolean mAudioFocused;

    public LinphoneAudioManager(Context context) {
        mContext = context;
        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void destroy() {

    }

    private void requestAudioFocus(int stream) {
        if (!mAudioFocused) {
            int res = mAudioManager.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);

            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) mAudioFocused = true;
        }
    }

    public synchronized void startRinging(Address remoteAddress) {
        routeAudioToSpeaker();
        mAudioManager.setMode(MODE_RINGTONE);

        try {
            if ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE
                    || mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL)
                    && mVibrator != null) {
                long[] patern = {0, 1000, 1000};
                mVibrator.vibrate(patern, 1);
            }

            if (mRingerPlayer == null) {
                requestAudioFocus(STREAM_RING);
                mRingerPlayer = new MediaPlayer();
                mRingerPlayer.setAudioStreamType(STREAM_RING);

                String ringtone = mContext.getFilesDir().getAbsolutePath() + "/oldphone_mono.wav";

                try {
                    if (ringtone.startsWith("content://")) {
                        mRingerPlayer.setDataSource(mContext, Uri.parse(ringtone));
                    } else {
                        FileInputStream fis = new FileInputStream(ringtone);
                        mRingerPlayer.setDataSource(fis.getFD());
                        fis.close();
                    }
                } catch (IOException e) {
                    android.util.Log.e(TAG, "[Audio Manager] Cannot set ringtone");
                }

                mRingerPlayer.prepare();
                mRingerPlayer.setLooping(true);
                mRingerPlayer.start();
            } else {
                android.util.Log.w(TAG, "[Audio Manager] Already ringing");
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "[Audio Manager] Cannot handle incoming call");
        }

        mIsRinging = true;
    }

    public synchronized void stopRinging() {
        if (mRingerPlayer != null) {
            mRingerPlayer.stop();
            mRingerPlayer.release();
            mRingerPlayer = null;
        }

        if (mVibrator != null) {
            mVibrator.cancel();
        }

        mIsRinging = false;
    }

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        mAudioManager.setSpeakerphoneOn(speakerOn);
    }

    public void setAudioManagerModeNormal() {
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
    }

    public void routeAudioToEarPiece() {
        routeAudioToSpeakerHelper(false);
    }

    public void routeAudioToSpeaker() {
        routeAudioToSpeakerHelper(true);
    }

    public boolean isAudioRoutedToSpeaker() {
        return mAudioManager.isSpeakerphoneOn();
    }

    public boolean isAudioRoutedToEarpiece() {
        return !mAudioManager.isSpeakerphoneOn();
    }
}
