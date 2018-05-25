package com.cokus.audiocanvaswave;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.cokus.audiocanvaswave.util.ImageUtil;
import com.cokus.audiocanvaswave.emotion.MyGraphVoice;
import com.cokus.audiocanvaswave.emotion.SpeechPreprocessUtil;
import com.cokus.audiocanvaswave.util.MediaPlayerSingleUtil;
import com.cokus.audiocanvaswave.util.U;
import com.cokus.wavelibrary.draw.WaveCanvas;
import com.cokus.wavelibrary.utils.SamplePlayer;
import com.cokus.wavelibrary.utils.SoundFile;
import com.cokus.wavelibrary.view.WaveSurfaceView;
import com.cokus.wavelibrary.view.WaveformView;

import org.opencv.android.OpenCVLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;


@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    private String tag = "MainActivity";

    private final static String[] EMOTIONS = {"Angry" , "Disgust" , "Fear", "Happy", "Sad", "Surprise", "Neutral"};
    private TextView textView;

    @BindView(R.id.wavesfv) WaveSurfaceView waveSfv;
    @BindView(R.id.switchbtn) Button switchBtn;
    @BindView(R.id.status)TextView status;
    @BindView(R.id.waveview)WaveformView waveView;
    @BindView(R.id.recognize)Button reconizeBtn;
    @BindView(R.id.upload)Button uploadBtn;
    @BindView(R.id.play)Button playBtn;
    @BindView(R.id.listview)ListView listView;

    // voice sampling sets. 语音录制采样的设置
    private static final int FREQUENCY = 16000;// 设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    private static final int CHANNELCONGIFIGURATION = AudioFormat.CHANNEL_IN_MONO;// 设置单声道声道
    private static final int AUDIOENCODING = AudioFormat.ENCODING_PCM_16BIT;// 音频数据格式：每个样本16位
    public final static int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;// 音频获取源
    private int recBufSize;// 录音最小buffer大小
    private AudioRecord audioRecord;
    private WaveCanvas waveCanvas;
    private String mFileName = "test";//文件名
    private float[] voiceProbabilities;
    private SimpleAdapter simpleAdapter;
    private List<Map<String, String>> listmaps=new ArrayList<Map<String,String>>();
    private String path;

    // ADD opencv library ,by OpenCVLoader.initDebug() ways
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_main);
        //获取权限
        initPermission();
        //加载opencv库
        if (!OpenCVLoader.initDebug()) {
            Log.d(tag, "OpenCV init error");
        }
        ButterKnife.bind(this);
        if(waveSfv != null) {
            waveSfv.setLine_off(42);
            //解决surfaceView黑色闪动效果
            waveSfv.setZOrderOnTop(true);
            waveSfv.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        }
        waveView.setLine_offset(42);

        //设置显示情感置信度标签的布局
        simpleAdapter=new SimpleAdapter(MainActivity.this, listmaps, android.R.layout.simple_expandable_list_item_2,
                new String[]{"first","second"}, new int[]{android.R.id.text1,android.R.id.text2});
        listView.setAdapter(simpleAdapter);
    }


    // Create a switchbutton , aim to start or end recoed
    // WveCanvas.isRecording drawing wave and displaying it
    // The file is named by time. Saved two format about pcm and wav.
    @OnClick({R.id.switchbtn,R.id.recognize,R.id.upload,R.id.play})
    void click(View view){
        switch (view.getId()) {
            case R.id.switchbtn:
            if (waveCanvas == null || !waveCanvas.isRecording) {
                Date date = new Date();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH_mm_ss");
                mFileName = simpleDateFormat.format(date);
                status.setText("录音中...");
                switchBtn.setText("停止录音");
                waveSfv.setVisibility(View.VISIBLE);
                waveView.setVisibility(View.INVISIBLE);
                // Init recode setting
                initAudio();
                startAudio();

            } else {
                status.setText("停止录音");
                switchBtn.setText("开始录音");
                waveCanvas.Stop();
                waveCanvas = null;
                initWaveView();
                path = Environment.getExternalStorageDirectory()+"/record/"+mFileName+".wav";
            }
                break;
            case R.id.recognize:

                new VoiceThread(getAudioBytes()).start();
                listmaps.clear();
                simpleAdapter.notifyDataSetChanged();
                break;
            case R.id.upload:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");//选择音频
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);

                break;
            case R.id.play:
               MediaPlayerSingleUtil.start(path);

                break;
        }
    }

    /** 根据返回选择的文件，来进行操作 **/
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String path = uri.getPath();
            int index = path.indexOf("/",1);
            this.path = Environment.getExternalStorageDirectory()+path.substring(index,path.length());
            Log.d(tag,this.path);

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //将语音转换成byte[]
    public byte[] getAudioBytes(){
        //
        File file = new File(path);
        FileInputStream fis;
        ByteArrayOutputStream baos;

        try {
            fis = new FileInputStream(file);
            baos = new ByteArrayOutputStream();

            byte[] buf = new byte[1024];
            int len = 0;
            while ((len = fis.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }

            byte[] voiceBytes = baos.toByteArray();

            fis.close();
            baos.close();

            return voiceBytes;
        } catch (Exception e) {
            e.printStackTrace();

        }

        return null;

    }

    // Load wave.file and display it
    private void  initWaveView(){
     loadFromFile();
    }
    File mFile;
    Thread mLoadSoundFileThread;
    SoundFile mSoundFile;
    boolean mLoadingKeepGoing;
    SamplePlayer mPlayer;
    /** 载入wav文件显示波形 */
    private void loadFromFile() {
        try {
            Thread.sleep(300);//让文件写入完成后再载入波形 适当的休眠下
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mFile = new File(U.DATA_DIRECTORY + mFileName + ".wav");
        mLoadingKeepGoing = true;
        // Load the sound file in a background thread
        mLoadSoundFileThread = new Thread() {
            public void run() {
                try {
                    mSoundFile = SoundFile.create(mFile.getAbsolutePath(),null);
                    if (mSoundFile == null) {
                        return;
                    }
                    mPlayer = new SamplePlayer(mSoundFile);
                    // mPlayer = new SamplePlayer();
                } catch (final Exception e) {
                    e.printStackTrace();
                    return;
                }
                if (mLoadingKeepGoing) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            finishOpeningSoundFile();
                            waveSfv.setVisibility(View.INVISIBLE);
                            waveView.setVisibility(View.VISIBLE);
                        }
                    };
                    MainActivity.this.runOnUiThread(runnable);
                }
            }
        };
        mLoadSoundFileThread.start();
    }



    float mDensity;
    /**waveview载入波形完成*/
    private void finishOpeningSoundFile() {
        waveView.setSoundFile(mSoundFile);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mDensity = metrics.density;
        waveView.recomputeHeights(mDensity);
    }

    /**
     * 开始录音
     */
    private void startAudio(){
        waveCanvas = new WaveCanvas();
        waveCanvas.baseLine = waveSfv.getHeight() / 2;
        waveCanvas.Start(audioRecord, recBufSize, waveSfv, mFileName, U.DATA_DIRECTORY, new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return true;
            }
        });
    }

    /**
     * 初始化权限
     */
    public void initPermission(){
        MainActivityPermissionsDispatcher.initAudioWithCheck(this);

    }
    /**
     * 初始化录音  申请录音权限
     */
    @NeedsPermission({Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE})
    public void initAudio(){
        recBufSize = AudioRecord.getMinBufferSize(FREQUENCY,
                CHANNELCONGIFIGURATION, AUDIOENCODING);// 录音组件
        audioRecord = new AudioRecord(AUDIO_SOURCE,// 指定音频来源，这里为麦克风
                FREQUENCY, // 16000HZ采样频率
                CHANNELCONGIFIGURATION,// 录制通道
                AUDIO_SOURCE,// 录制编码格式
                recBufSize);// 录制缓冲区大小 //先修改
        U.createDirectory();
    }



    // Asking some APK permission
    @OnShowRationale({Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE})
    void showRationaleForRecord(final PermissionRequest request){
        new AlertDialog.Builder(this)
                .setPositiveButton("好的", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.proceed();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        request.cancel();
                    }
                })
                .setCancelable(false)
                .setMessage("是否开启录音权限")
                .show();
    }

    @OnPermissionDenied({Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE})
    void showRecordDenied(){
        Toast.makeText(MainActivity.this,"拒绝录音权限将无法进行录音",Toast.LENGTH_LONG).show();
    }

    @OnNeverAskAgain({Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE})
    void onRecordNeverAskAgain() {
        new AlertDialog.Builder(this)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.cancel();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setCancelable(false)
                .setMessage("您已经禁止了录音权限,是否现在去开启")
                .show();
    }

    /* Speech emotion recognize set a thread */
    class VoiceThread extends Thread{

        private byte[] voiceByte;

        public VoiceThread(byte[] voiceByte){
            this.voiceByte = voiceByte;
        }
        @Override
        public void run() {
            super.run();

            try {

                long voiceStartTime = System.currentTimeMillis();
                invokeVoiceModel(voiceByte);
                long voiceEndTime = System.currentTimeMillis();
                Log.d("Thread id"+this.getId()+"调用语音情感识别算法所需时间", (voiceEndTime - voiceStartTime)+"ms");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Speech emotion
        private int invokeVoiceModel(byte[] voiceByte) throws Exception{
            //
            MyGraphVoice myGraphVoice = new MyGraphVoice(getAssets());//加载tensorflow库文件，初始化tensorflow对象
            SpeechPreprocessUtil speechPreprocessUtils = new SpeechPreprocessUtil(getAssets());
            String returnstr = speechPreprocessUtils.initPreprocess(voiceByte);
            Log.d(tag, "voiceByte length : " + voiceByte.length + "   returnstr :　" + returnstr);
            File file = new File(returnstr);
            if (file.exists()) {
                float[] voiceFixes = ImageUtil.bitmap2Array(BitmapFactory.decodeFile(returnstr));
                Log.d(tag, "voiceThread run test " + voiceFixes.length);
                voiceProbabilities = myGraphVoice.getAddResult(voiceFixes);
                Log.d(tag, "voiceProbabilities: " + Arrays.toString(voiceProbabilities));
                //选取最大的作为其情感
                // Select maximum number as the index
                float max = 0;
                int index = 0;
                for (int i = 0; i < voiceProbabilities.length; i++) {
                    if (voiceProbabilities[i] > max) {
                        index = i;
                        max = voiceProbabilities[i];
                    }
                }
                Log.d("index:",index + ":"+max);
                Message message = new Message();
                message.obj = index;
                handler.sendMessage(message);

                return index;
            }

            return -1;
        }
    }
    // Sitting the handler 线程
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int index = Integer.parseInt(msg.obj+"");
           // textView.setText(EMOTIONS[index]);

            for (int i =0;i<EMOTIONS.length;i++){
                HashMap<String,String> map = new HashMap<>();
                map.put("first",EMOTIONS[i]);
                map.put("second",voiceProbabilities[i]+"");
                listmaps.add(map);
            }
            HashMap<String,String> map = new HashMap<>();
            map.put("first","Result");
            map.put("second",EMOTIONS[index]);
            listmaps.add(map);

            simpleAdapter.notifyDataSetChanged();
        }
    };

}
