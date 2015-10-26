package com.dgu.kero.voicehelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class VoiceHelper extends Activity {
  private final String kTag = VoiceHelper.class.getSimpleName();
  private final VoiceHelper self = VoiceHelper.this;
  private static final int kRequestCode = 1;

  private TextToSpeech tts_;
  private AudioManager audio_mgr_;
  private String prev_input_ = "";
  private Button input_button_;
  private Vibrator vibrator_;
  private String battery_power_str_ = "";
  private NumberFormat nf_ptr_= NumberFormat.getPercentInstance();
  private int music_vol_ = 0;
  private int ring_vol_ = 0;
  private int system_vol_ = 0;
  private int voice_call_vol_ = 0;
  private int music_max_vol_;
  private int ring_max_vol_;
  private int system_max_vol_;
  private int voice_call_max_vol_;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // 起動時にキーボードを表示しない
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

    // TTS(読み上げ)
    tts_ = new TextToSpeech(this, initTextToSpeech);

    // バッテリ残量
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
    registerReceiver(my_receiver_, filter);

    // AudioManager と Vibrator のインスタンスを取得
    audio_mgr_ = (AudioManager)getSystemService(AUDIO_SERVICE);
    vibrator_ = (Vibrator)getSystemService(VIBRATOR_SERVICE);

    // 最大音量を取得
    music_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    ring_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_RING);
    system_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
    voice_call_max_vol_ = audio_mgr_.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);

    // View の設定
    input_button_ = (Button)findViewById(R.id.input_button);
    input_button_.setOnClickListener(inputVoice);
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    // TTS のリソースを解放
    if (tts_ != null) { tts_.shutdown(); }
    if (my_receiver_ != null) { unregisterReceiver(my_receiver_); }
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

      Log.v("Speech", "Candidate Num = " + candidates.size());
      if (candidates.size() > 0) {
        input_voice = prev_input_ + candidates.get(0);
      }
      if (input_voice == null || input_voice.length() == 0) {
        return;
      }

      manipulateAndroidVoice(input_voice);  // 入力された音声を文字列として関数に渡して Android を操作する
    }
  }

  /**
   * tts の初期化
   */
  private TextToSpeech.OnInitListener initTextToSpeech = new TextToSpeech.OnInitListener() {
    @Override
    public void onInit(int status)
    {
      if (status == TextToSpeech.SUCCESS) {
        Log.d(kTag, "Success of the TextToSeech");
        Locale locale = Locale.JAPAN;
        if (tts_.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
          Log.d(kTag, "Avaiable is Locale.JAPAN");
          tts_.setLanguage(locale);
        }
        else {
          Log.d(kTag, "Not avaiable is Locale.JAPAN");
        }
      }
      else {
        Log.d(kTag, "Error of the TextToSeepch");
      }
    }
  };

  /**
   * バッテリ残量の変化を受信
   */
  private BroadcastReceiver my_receiver_ = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent)
    {
      if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
        int level = intent.getIntExtra("level", 0);
        battery_power_str_ = String.valueOf(level) + "%";
      }
    }
  };

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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "音声を入力して下さい。");
        // インテント発行
        startActivityForResult(intent, kRequestCode);
      }
      catch (ActivityNotFoundException e) {
        Toast.makeText(self, "Not found Activity", Toast.LENGTH_LONG).show();
      }
    }
  };

  /**
   * 音声で Android を操作する
   * @param {String} input_voice 入力された音声
   */
  private void manipulateAndroidVoice(String input_voice)
  {
    // マナーモードならボタンを押せたかわからないので, バイブを再生
    if (audio_mgr_.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
      vibrator_.vibrate(500);
    }

    // debug
    Log.d(kTag, "debug - input_voice = " + input_voice);
    Log.d(kTag, "debug - input_voice.length() = " + input_voice.length());
    //

    // 正規表現で日付をチェック
    if (checkDayOfTheWeek(input_voice)) { return; }

    switch (input_voice) {
      // 充電確認
      case "充電":
        speak(battery_power_str_ + "です。");
        break;
      // 時間確認
      case "何時":
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("H時m分");
        speak(sdf.format(cal.getTime()) + "です。");
        break;
      // 曜日確認
      case "何曜日":
      case "何日":
        speak(getDayOfTheWeek() + "です。");
        break;
      // マナーモード
      case "マナー":
      case "マナーモード":
        speak("マナーモードを設定します。");
        audio_mgr_.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        vibrator_.vibrate(1500);
        break;
      // サイレント
      case "サイレント":
      case "サイレントモード":
        speak("サイレントモードをします。");
        audio_mgr_.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        vibrator_.vibrate(500);
        break;
      case "ノーマル":
      case "解除":
      case "設定解除":
        audio_mgr_.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        playBeep(system_vol_);
        speak("マナーモードを解除しました。");
        break;
      // 音量
      case "大きく":
      case "上げて":
      case "音量上げて":
        upVolume();
        speak("大きく。");
        break;
      case "小さく":
      case "下げて":
      case "音量下げて":
        downVolume();
        speak("小さく。");
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
   * 日付を渡して曜日を読み上げる
   * @param {String} str 曜日を求めたい日付
   * @return {boolean} 渡された文字列が日付なら true
   */
  private boolean checkDayOfTheWeek(String date_str)
  {
    Locale.setDefault(new Locale("ja", "JP", "JP"));
    Calendar cal = Calendar.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月d日");
    sdf.setLenient(false);

    try {
      Date date = null;

      // 今年の曜日を求める
      if (date_str.length() <= 6) {
        SimpleDateFormat this_year_sdf = new SimpleDateFormat("yyyy年");
        this_year_sdf.setLenient(false);
        String this_year_str = this_year_sdf.format(cal.getTime());
        date = sdf.parse(this_year_str + date_str);
      }
      // 今年以外の曜日を求める
      else {
        date = sdf.parse(date_str);
      }

      cal.setTime(date);
      speak(date_str + "は" + getDayOfTheWeek(cal) + "です。");
      return true;
    }
    catch (ParseException e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * 渡した文字列の内容を読み上げる
   * @param str
   */
  private void speak(String str)
  {
    if (tts_.isSpeaking()) { tts_.stop(); }
    tts_.speak(str, TextToSpeech.QUEUE_FLUSH, null);
  }

  /**
   * 今日の曜日を取得
   */
  private String getDayOfTheWeek()
  {
    Calendar cal = Calendar.getInstance();
    switch (cal.get(Calendar.DAY_OF_WEEK)) {
      case Calendar.SUNDAY: return "日曜日";
      case Calendar.MONDAY: return "月曜日";
      case Calendar.TUESDAY: return "火曜日";
      case Calendar.WEDNESDAY: return "水曜日";
      case Calendar.THURSDAY: return "木曜日";
      case Calendar.FRIDAY: return "金曜日";
      case Calendar.SATURDAY: return "土曜日";
    }
    throw new IllegalStateException();
  }

  /**
   * 指定した日付の曜日を取得
   * @param {Calendar} cal cal.set() で日付を指定済みのカレンダーオブジェクト
   */
  private String getDayOfTheWeek(Calendar cal)
  {
    switch (cal.get(Calendar.DAY_OF_WEEK)) {
      case Calendar.SUNDAY: return "日曜日";
      case Calendar.MONDAY: return "月曜日";
      case Calendar.TUESDAY: return "火曜日";
      case Calendar.WEDNESDAY: return "水曜日";
      case Calendar.THURSDAY: return "木曜日";
      case Calendar.FRIDAY: return "金曜日";
      case Calendar.SATURDAY: return "土曜日";
    }
    throw new IllegalStateException();
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
    ring_vol_ = (int)Math.floor(music_vol_/2);
    system_vol_ = (int)Math.floor(music_vol_/2);
    voice_call_vol_ = (int)Math.floor(music_vol_/3);

    // 音量が最大値を超えないようにする
    if (music_vol_ > music_max_vol_) {
      music_vol_ = music_max_vol_;
    }
    if (ring_vol_ > ring_max_vol_) {
      ring_vol_ = ring_max_vol_;
    }
    if (system_vol_ > system_max_vol_) {
      system_vol_ = system_max_vol_;
    }
    if (voice_call_vol_ > voice_call_max_vol_) {
      voice_call_vol_ = voice_call_max_vol_;
    }

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
    if (music_vol_ < 1) {
      music_vol_ = 1;
    }
    if (ring_vol_ < 1) {
      ring_vol_ = 1;
    }
    if (system_vol_ < 1) {
      system_vol_ = 1;
    }
    if (voice_call_vol_ < 1) {
      voice_call_vol_ = 1;
    }

    // 音量を設定
    audio_mgr_.setStreamVolume(AudioManager.STREAM_MUSIC, music_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_RING, ring_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_SYSTEM, system_vol_, AudioManager.FLAG_SHOW_UI);
    audio_mgr_.setStreamVolume(AudioManager.STREAM_VOICE_CALL, voice_call_vol_, AudioManager.FLAG_SHOW_UI);
  }
}
