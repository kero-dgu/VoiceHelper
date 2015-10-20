package com.dgu.kero.voicehelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;


public class VoiceHelper extends Activity {
  // Constants ********************************************************************************
  private final String kTag = VoiceHelper.class.getSimpleName();
  private final VoiceHelper self = VoiceHelper.this;
  private static final int kRequestCode = 1;


  // Members ********************************************************************************
  private AudioManager audio_mgr_;
  private String prev_input_ = "";
  private Button input_button_;
  private Vibrator vibrator_;

  // 現在の音量
  private int music_vol_ = 0;
  private int ring_vol_ = 0;
  private int system_vol_ = 0;
  private int voice_call_vol_ = 0;
  // 最大音量
  private int music_max_vol_;
  private int ring_max_vol_;
  private int system_max_vol_;
  private int voice_call_max_vol_;


  // Rife cycle ********************************************************************************
  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // 起動時にキーボードを表示しない
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    // AudioManager と Vibrator のインスタンスを取得
    audio_mgr_ = (AudioManager)getSystemService(AUDIO_SERVICE);
    vibrator_  = (Vibrator)getSystemService(VIBRATOR_SERVICE);

    // 最大音量を取得
    music_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    ring_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_RING);
    system_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
    voice_call_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);

    Log.d(kTag, "debug - music_max_vol_      = "+music_max_vol_);
    Log.d(kTag, "debug - voice_call_max_vol_ = "+voice_call_max_vol_);
    Log.d(kTag, "debug - system_max_vol_     = "+system_max_vol_);
    Log.d(kTag, "debug - ring_max_vol_       = "+ring_max_vol_);

    // View の設定
    input_button_ = (Button)findViewById(R.id.input_button);
    input_button_.setOnClickListener(inputVoice);
  }

  // startActivityForResult() で発行したインテントの戻り値を取得する
  @Override
  protected void onActivityResult(int request_code, int result_code, Intent data)
  {
    super.onActivityResult(request_code, result_code, data);

    // RecognizerIntent で音声が取得できたなら
    if (request_code == kRequestCode && result_code == RESULT_OK) {
      String input_voice = null;
      ArrayList<String> candidates = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

      Log.v("Speech", "Candidate Num = "+candidates.size());
      if (candidates.size() > 0) {
        input_voice = prev_input_+candidates.get(0);
      }
      if (input_voice == null || input_voice.length() == 0) { return; }

      manipulateAndroidVoice(input_voice);  // 入力された音声を文字列として関数に渡して Android を操作する
    }
  }


  // View.OnClickListner ********************************************************************************
  /**
   * コマンドを音声で入力
   */
  private OnClickListener inputVoice = new OnClickListener() {
    @Override
    public void onClick(View v)
    {
      try {
        // 音声認識の準備
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "音声を入力して下さい。");
        // インテント発行
        startActivityForResult(intent, kRequestCode);
      }
      catch (ActivityNotFoundException e) {
        Toast.makeText(self, "Not found Activity", Toast.LENGTH_LONG).show();
      }
    }
  };


  // Methods ********************************************************************************
  /**
   * 音声で Android を操作する
   * @param {String} input_voice 入力された音声
   */
  private void manipulateAndroidVoice(String input_voice)
  {
    // マナーモードならボタンを押せたかわからないので, バイブを再生
    if (audio_mgr_.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) { vibrator_.vibrate(500); }

    switch (input_voice) {
      // マナーモード
      case "マナー":
      case "マナーモード":
        audio_mgr_.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        vibrator_.vibrate(1500);
        break;
      // サイレント
      case "サイレント":
      case "サイレントモード":
        audio_mgr_.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        vibrator_.vibrate(500);
        break;
      case "ノーマル":
      case "解除":
      case "設定解除":
        audio_mgr_.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        playBeep(system_vol_);
        break;
      // 音量
      case "大きく":
      case "上げて":
      case "音量上げて":
        upVolume();
        break;
      case "小さく":
      case "下げて":
      case "音量下げて":
        downVolume();
        break;
      case "テスト":
        break;
      default:
        long[] def_pattern = {50, 100, 50, 500, 50, 100, 50, 500};
        vibrator_.vibrate(def_pattern, -1);
        break;
    }
  }

  /**
   * 現在の各種ボリュームを取得
   */
  private void getCurrentAudioVolume()
  {
    music_vol_ = audio_mgr_.getStreamVolume(AudioManager.STREAM_MUSIC);
    ring_vol_ = audio_mgr_.getStreamVolume(AudioManager.STREAM_RING);
    system_vol_ = audio_mgr_.getStreamVolume(AudioManager.STREAM_SYSTEM);
    voice_call_vol_ = audio_mgr_.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
  }

  /**
   * ビープ音を鳴らす
   * @param {int} volume 音量
   */
  private void playBeep(int volume)
  {
    (new ToneGenerator(AudioManager.STREAM_SYSTEM, volume)).startTone(ToneGenerator.TONE_PROP_BEEP);
  }

  /**
   * 音量を上げる
   */
  private void upVolume()
  {
    getCurrentAudioVolume();

    music_vol_ += 2;
    ring_vol_  = (int)Math.floor(music_vol_/2);
    system_vol_ = (int)Math.floor(music_vol_/2);
    voice_call_vol_ = (int)Math.floor(music_vol_/3);

    // 音量が最大値を超えないようにする
    if (music_vol_ > music_max_vol_) { music_vol_ = music_max_vol_; }
    if (ring_vol_  > ring_max_vol_) { ring_vol_ = ring_max_vol_; }
    if (system_vol_ > system_max_vol_) { system_vol_ = system_max_vol_; }
    if (voice_call_vol_ > voice_call_max_vol_) { voice_call_vol_ = voice_call_max_vol_; }

    // 音量を設定
    audio_mgr_.setStreamVolume(AudioManager.STREAM_MUSIC, music_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_RING, ring_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_SYSTEM, system_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_VOICE_CALL, voice_call_vol_, AudioManager.FLAG_SHOW_UI);
  }

  /**
   * 音量を下げる
   */
  private void downVolume()
  {
    getCurrentAudioVolume();

    music_vol_ -= 2;
    ring_vol_ = (int)Math.floor(music_vol_/2);
    system_vol_ = (int)Math.floor(music_vol_/2);
    voice_call_vol_ = (int)Math.floor(music_vol_/3);

    // 音量が1以下にならないようにする
    if (music_vol_ < 1) { music_vol_ = 1; }
    if (ring_vol_  < 1) { ring_vol_ = 1; }
    if (system_vol_ < 1) { system_vol_ = 1; }
    if (voice_call_vol_ < 1) { voice_call_vol_ = 1; }

    // 音量を設定
    audio_mgr_.setStreamVolume(AudioManager.STREAM_MUSIC, music_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_RING, ring_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_SYSTEM, system_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_VOICE_CALL, voice_call_vol_, AudioManager.FLAG_SHOW_UI);
  }
}
