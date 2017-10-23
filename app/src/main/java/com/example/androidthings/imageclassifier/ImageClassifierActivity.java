/*
 * Copyright 2017 The Android Things Samples Authors.
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
package com.example.androidthings.imageclassifier;

import android.app.Activity;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;




import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImageClassifierActivity extends Activity implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "ImageClassifierActivity";

    private ImagePreprocessor mImagePreprocessor;
    private TextToSpeech mTtsEngine;
    private TtsSpeaker mTtsSpeaker;
    //private TensorFlowImageClassifier mTensorFlowClassifier;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private ImageView mImage;
    private TextView[] mResultViews;

    private AtomicBoolean mReady = new AtomicBoolean(false);
    //private ButtonInputDriver mButtonDriver;
    //private Gpio mReadyLED;
    private Button mTakePicture;
    private Button mFinish;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        mImage = (ImageView) findViewById(R.id.imageView);
        mResultViews = new TextView[3];
        mResultViews[0] = (TextView) findViewById(R.id.result1);
        mResultViews[1] = (TextView) findViewById(R.id.result2);
        mResultViews[2] = (TextView) findViewById(R.id.result3);
        mTakePicture = (Button) findViewById(R.id.take_picture);
        mFinish = (Button) findViewById(R.id.finish);

        init();
    }

    private void init() {
        //initPIO();

        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);

        mFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        mTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mReady.get()) {
                    setReady(false);
                    mBackgroundHandler.post(mBackgroundClickHandler);
                } else {
                    Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds");
                }
            }
        });
    }

//    private void initPIO() {
//        PeripheralManagerService pioService = new PeripheralManagerService();
//        try {
//            mReadyLED = pioService.openGpio(BoardDefaults.getGPIOForLED());
//            mReadyLED.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
//            mButtonDriver = new ButtonInputDriver(
//                    BoardDefaults.getGPIOForButton(),
//                    com.google.android.things.contrib.driver.button.Button.LogicState.PRESSED_WHEN_LOW,
//                    KeyEvent.KEYCODE_ENTER);
//            mButtonDriver.register();
//        } catch (IOException e) {
//            mButtonDriver = null;
//            Log.w(TAG, "Could not open GPIO pins", e);
//        }
//    }

    private DoorbellCamera mCamera;
    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private Runnable mInitializeOnBackground = new Runnable() {
        @Override
        public void run() {
            mImagePreprocessor = new ImagePreprocessor();

            mTtsSpeaker = new TtsSpeaker();
            mTtsSpeaker.setHasSenseOfHumor(true);
            mTtsEngine = new TextToSpeech(ImageClassifierActivity.this,
                    new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS) {
                                mTtsEngine.setLanguage(Locale.US);
                                mTtsEngine.setOnUtteranceProgressListener(utteranceListener);
                                mTtsSpeaker.speakReady(mTtsEngine);
                            } else {
                                Log.w(TAG, "Could not open TTS Engine (onInit status=" + status
                                        + "). Ignoring text to speech");
                                mTtsEngine = null;
                            }
                        }
                    });
            // Creates new handlers and associated threads for camera and networking operations.
            mCameraThread = new HandlerThread("CameraBackground");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());

            // Camera code is complicated, so we've shoved it all in this closet class for you.
            mCamera = DoorbellCamera.getInstance();
            mCamera.initializeCamera(ImageClassifierActivity.this, mCameraHandler, ImageClassifierActivity.this, new Runnable() {
                @Override
                public void run() {

                      mBackgroundHandler.postDelayed(new Runnable() {
                          @Override
                          public void run() {
                              runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      mTakePicture.performClick();
                                  }
                              });
                          }
                      }, 5000);

                }
            });


            //mTensorFlowClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this);

            setReady(true);
        }
    };

    private Runnable mBackgroundClickHandler = new Runnable() {
        @Override
        public void run() {
            if (mTtsEngine != null) {
                mTtsSpeaker.speakShutterSound(mTtsEngine);
            }
            mCamera.takePicture();
        }
    };

    private UtteranceProgressListener utteranceListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            setReady(false);
        }

        @Override
        public void onDone(String utteranceId) {
            setReady(true);
        }

        @Override
        public void onError(String utteranceId) {
            setReady(true);
        }
    };

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "Received key up: " + keyCode + ". Ready = " + mReady.get());
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (mReady.get()) {
                setReady(false);
                mBackgroundHandler.post(mBackgroundClickHandler);
            } else {
                Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds");
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void setReady(boolean ready) {
        Log.w(TAG, "setReady, ready = " + ready);
        mReady.set(ready);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Log.d(TAG, "onImageAvailable() called!");

        final Bitmap bitmap;
        try (Image image = reader.acquireNextImage()) {
            bitmap = mImagePreprocessor.preprocessImage(image);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Log.d(TAG, "Got the bitmap and seted: " + bitmap.getByteCount());
                mImage.setImageBitmap(bitmap);
            }
        });

//        final List<Classifier.Recognition> results = mTensorFlowClassifier.doRecognize(bitmap);
//
//        Log.d(TAG, "Got the following results from Tensorflow: " + results);
//        if (mTtsEngine != null) {
//            // speak out loud the result of the image recognition
//            mTtsSpeaker.speakResults(mTtsEngine, results);
//        } else {
//            // if theres no TTS, we don't need to wait until the utterance is spoken, so we set
//            // to ready right away.
//            setReady(true);
//        }
//
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                for (int i = 0; i < mResultViews.length; i++) {
//                    if (results.size() > i) {
//                        Classifier.Recognition r = results.get(i);
//                        mResultViews[i].setText(r.getTitle() + " : " + r.getConfidence().toString());
//                    } else {
//                        mResultViews[i].setText(null);
//                    }
//                }
//            }
//        });
    }

    public boolean isValid(String braces) {
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mBackgroundThread != null) mBackgroundThread.quit();
        } catch (Throwable t) {
            // close quietly
        }
        mBackgroundThread = null;
        mBackgroundHandler = null;

        try {
            if (mCamera != null) mCamera.shutDown();
        } catch (Throwable t) {
            // close quietly
        }
        try {
           // if (mTensorFlowClassifier != null) mTensorFlowClassifier.destroyClassifier();
        } catch (Throwable t) {
            // close quietly
        }
        try {
           // if (mButtonDriver != null) mButtonDriver.close();
        } catch (Throwable t) {
            // close quietly
        }

        if (mTtsEngine != null) {
            mTtsEngine.stop();
            mTtsEngine.shutdown();
        }
    }

}
