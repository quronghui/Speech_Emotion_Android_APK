package com.cokus.audiocanvaswave.emotion;


import android.content.res.AssetManager;
import android.os.Trace;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

/**
 * Created by Admin on 2017/12/27.
 */

public class MyGraphVoice {

    private static final String MODEL_FILE = "file:///android_asset/graph_voice.pb"; //模型存放路径

    //数据的维度(生成的mfcc图大小)
    private static final int HEIGHT = 198;
    private static final int WIDTH = 64;

    //模型中输出变量的名称
    private static final String inputName = "input";
    //用于存储的模型输入数据
    private float[] inputs = new float[HEIGHT * WIDTH * 3];

    //模型中输出变量的名称
    private static final String outputName = "test/prob";
    //用于存储模型的输出数据
    private float[] outputs = new float[7];



    TensorFlowInferenceInterface inferenceInterface;


    static {
        //加载库文件
        System.loadLibrary("tensorflow_inference");
    }

    public MyGraphVoice(AssetManager assetManager) {
        //接口定义
        inferenceInterface = new TensorFlowInferenceInterface(assetManager,MODEL_FILE);
    }

    //返回七种情感的置信度
    public float[] getAddResult(float[] voiceArray) {

        inputs = voiceArray;

        float[] keep_prob = new float[]{1};

        //将数据feed给tensorflow
        Trace.beginSection("feed");
        inferenceInterface.feed(inputName, inputs, 1, HEIGHT, WIDTH, 3);
        inferenceInterface.feed("Placeholder_1", keep_prob, 1);
        Trace.endSection();

        Trace.beginSection("run");
        String[] outputNames = new String[] {outputName};
        inferenceInterface.run(outputNames, false);
        Trace.endSection();

        //将输出存放到outputs中
        Trace.beginSection("fetch");
        inferenceInterface.fetch(outputName, outputs);
        Trace.endSection();

        return outputs;
    }



}
