package com.braunster.chatsdk.activities.util;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.braunster.chatsdk.R;

import java.io.IOException;

/**
 * Created by Erk on 13.03.2016.
 */
public class RecordAudioActivity extends Activity implements View.OnClickListener{

    private MediaRecorder recorder;
    private boolean isRecording = false;
    private boolean recordedSomething = false;

    private String path;

    private Button playButton, recordButton, submitButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat_sdk_activity_record_audio);

        playButton = (Button) findViewById(R.id.chat_sdk_util_btn_play_audio);
        recordButton = (Button) findViewById(R.id.chat_sdk_util_btn_record_audio);
        submitButton = (Button) findViewById(R.id.chat_sdk_util_btn_submit_audio);

        playButton.setEnabled(false);

        path = getCacheDir().getAbsolutePath() + "/temp_recording.3gp";

        setupRecorder();
    }

    private void setupRecorder() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        recorder.setOutputFile(path);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.chat_sdk_util_btn_play_audio) {

            MediaPlayer mp = new MediaPlayer();

            try {
                mp.setDataSource(path);
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                mp.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mp.start();

            Toast.makeText(getApplicationContext(), "Playing audio", Toast.LENGTH_LONG).show();
        }

        if (id == R.id.chat_sdk_util_btn_record_audio) {
            if(!isRecording) {
                isRecording = true;

                try {
                    recorder.prepare();
                    recorder.start();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Toast.makeText(getApplicationContext(), "Recording started", Toast.LENGTH_LONG).show();

                recordButton.setText(R.string.stop);

                playButton.setEnabled(false);
                submitButton.setEnabled(false);
            }
            else {
                isRecording = false;

                try {
                    recorder.stop();
                } catch(RuntimeException rte) { }

                recorder.release();

                setupRecorder();

                Toast.makeText(getApplicationContext(), "Audio recorded successfully",Toast.LENGTH_LONG).show();

                recordButton.setText(R.string.record_audio);

                playButton.setEnabled(true);
                submitButton.setEnabled(true);

                if(!recordedSomething) {
                    recordedSomething = true;
                    submitButton.setText(R.string.submit_audio);
                }
            }
        }

        if (id == R.id.chat_sdk_util_btn_submit_audio) {
            if(recordedSomething && !isRecording) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(path));
                intent.putExtra("path", path);
                setResult(RESULT_OK, intent);
                finish();
            } else {
                Intent intent = new Intent();
                setResult(RESULT_CANCELED, intent);
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent();
        setResult(RESULT_CANCELED, intent);
        finish();
    }
}
