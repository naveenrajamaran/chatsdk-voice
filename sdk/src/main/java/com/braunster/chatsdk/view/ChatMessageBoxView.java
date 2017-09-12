/*
 * Created by Itzik Braun on 12/3/2015.
 * Copyright (c) 2015 deluge. All rights reserved.
 *
 * Last Modification at: 3/12/15 4:27 PM
 */

package com.braunster.chatsdk.view;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.braunster.chatsdk.R;
import com.braunster.chatsdk.Utils.Debug;
import com.braunster.chatsdk.Utils.DialogUtils;
import com.braunster.chatsdk.Utils.FileUtils;
import com.braunster.chatsdk.Utils.Utils;
import com.braunster.chatsdk.Utils.helper.ChatSDKChatHelper;
import com.braunster.chatsdk.activities.abstracted.ChatSDKAbstractChatActivity;
import com.braunster.chatsdk.dao.core.DaoCore;
import com.braunster.chatsdk.network.BDefines;
import com.github.johnpersano.supertoasts.SuperToast;

import java.io.IOException;

import timber.log.Timber;

public class ChatMessageBoxView extends LinearLayout implements View.OnClickListener, View.OnTouchListener, View.OnKeyListener, TextView.OnEditorActionListener{

    public static final String TAG = ChatMessageBoxView.class.getSimpleName();
    public static final boolean DEBUG = Debug.ChatMessageBoxView;

    public static final int MODE_SEND = 100;
    public static final int MODE_RECORD = 101;
    public static final int MODE_RECORDING = 101;

    private int currentMode = MODE_SEND;

    private MediaRecorder recorder;
    private String recordingPath;
    Toast toastRecording;

    protected MessageBoxOptionsListener messageBoxOptionsListener;
    protected MessageSendListener messageSendListener;
    protected TextView btnSend;
    protected ImageButton btnOptions;
    protected EditText etMessage;
    protected PopupWindow optionPopup;

    /** The alert toast that the app will use to alert the user.*/
    protected SuperToast alertToast;

    public ChatMessageBoxView(Context context) {
        super(context);
        init();
    }

    public ChatMessageBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChatMessageBoxView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    protected void init(){
        inflate(getContext(), R.layout.chat_sdk_view_message_box, this);
    }

    protected void initViews(){
        btnSend = (TextView) findViewById(R.id.chat_sdk_btn_chat_send_message);
        btnOptions = (ImageButton) findViewById(R.id.chat_sdk_btn_options);
        etMessage = (EditText) findViewById(R.id.chat_sdk_et_message_to_send);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        initViews();

        if (isInEditMode())
            return;

        btnSend.setOnClickListener(this);

        if(BDefines.Options.AudioEnabled) {
            btnSend.setOnTouchListener(this);
            btnSend.setText(getResources().getString(R.string.record));
            currentMode = MODE_RECORD;

            recordingPath = getContext().getCacheDir().getAbsolutePath() + "/" + DaoCore.generateEntity() + BDefines.Options.AudioFormat;

            setupRecorder();
        }

        btnOptions.setOnClickListener(this);

        etMessage.setOnEditorActionListener(this);
        etMessage.setOnKeyListener(this);

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(etMessage.getText().toString().trim().length() > 0) {
                    currentMode = MODE_SEND;
                    btnSend.setText(getResources().getString(R.string.send));
                } else {
                    currentMode = MODE_RECORD;
                    btnSend.setText(getResources().getString(R.string.record));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    /** Show the message option popup, From here the user can send images and location messages.*/
    public void showOptionPopup(){
        if (optionPopup!= null && optionPopup.isShowing())
        {
            return;
        }

        optionPopup = DialogUtils.getMenuOptionPopup(getContext(), this);
        optionPopup.showAsDropDown(btnOptions);
    }

    public void dismissOptionPopup(){
        if (optionPopup != null)
            optionPopup.dismiss();
    }

    /* Implement listeners.*/
    @Override
    public void onClick(View v) {
        int id= v.getId();

        if (id == R.id.chat_sdk_btn_chat_send_message) {
            if (messageSendListener != null && currentMode == MODE_SEND)
                messageSendListener.onSendPressed(getMessageText());
        }
        else if (id == R.id.chat_sdk_btn_options){
            boolean b = false;
            if (messageBoxOptionsListener != null) {
                b = messageBoxOptionsListener.onOptionButtonPressed();
            }

            if (!b)
                showOptionPopup();
        }
        else  if (id == R.id.chat_sdk_btn_choose_picture) {
            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onPickImagePressed();
        }
        else  if (id == R.id.chat_sdk_btn_take_picture) {
            if (!Utils.SystemChecks.checkCameraHardware(getContext()))
            {
                Toast.makeText(getContext(), "This device does not have a camera.", Toast.LENGTH_SHORT).show();
                return;
            }

            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onTakePhotoPressed();
        }
        else  if (id == R.id.chat_sdk_btn_choose_audio) {
            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onPickAudioPressed();
        }
        else  if (id == R.id.chat_sdk_btn_record_audio) {
            if (!Utils.SystemChecks.checkCameraHardware(getContext()))
            {
                Toast.makeText(getContext(), "This device does not have a camera.", Toast.LENGTH_SHORT).show();
                return;
            }

            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onRecordAudioPressed();
        }
        else  if (id == R.id.chat_sdk_btn_choose_video) {
            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onPickVideoPressed();
        }
        else  if (id == R.id.chat_sdk_btn_record_video) {
            if (!Utils.SystemChecks.checkCameraHardware(getContext()))
            {
                Toast.makeText(getContext(), "This device does not have a camera.", Toast.LENGTH_SHORT).show();
                return;
            }

            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onRecordVideoPressed();
        }
        else  if (id == R.id.chat_sdk_btn_location) {
            dismissOptionPopup();

            if (messageBoxOptionsListener != null)
                messageBoxOptionsListener.onLocationPressed();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (view.getId() == R.id.chat_sdk_btn_chat_send_message) {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN && currentMode == MODE_RECORD) {
                if(DEBUG) Timber.v("recording started");
                toastRecording = Toast.makeText(getContext(), "Recording....", Toast.LENGTH_LONG);
                toastRecording.show();

                currentMode = MODE_RECORDING;

                try {
                    recorder.prepare();
                    recorder.start();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if(motionEvent.getAction() == MotionEvent.ACTION_UP && currentMode == MODE_RECORDING) {
                if(DEBUG) Timber.v("recording finished");
                currentMode = MODE_RECORD;

                try {
                    recorder.stop();
                } catch(RuntimeException rte) { }

                recorder.release();

                // Get and check the duration of the recorded audio message
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(recordingPath);
                String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                int duration = Integer.valueOf(durationStr);

                if(duration < BDefines.Options.minAudioLength) {
                    toastRecording.cancel();
                    Toast.makeText(getContext(), "Minimum audio message length is 2 seconds", Toast.LENGTH_SHORT).show();
                } else {
                    String selectedFilePath = FileUtils.getPath(getContext().getApplicationContext(), Uri.parse(recordingPath));

                    ((ChatSDKAbstractChatActivity) getContext()).getChatSDKChatHelper().sendFileMessage(selectedFilePath, ChatSDKChatHelper.SEND_AUDIO);
                }

                recordingPath = getContext().getCacheDir().getAbsolutePath() + "/" + DaoCore.generateEntity() + BDefines.Options.AudioFormat;
                setupRecorder();
            }
        }

        return false;
    }

    private void setupRecorder() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        recorder.setOutputFile(recordingPath);
    }

    /** Send a text message when the done button is pressed on the keyboard.*/
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND)
            if (messageSendListener!=null)
                messageSendListener.onSendPressed(getMessageText());

        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // if enter is pressed start calculating
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
            int editTextLineCount = ((EditText) v).getLineCount();
            if (editTextLineCount >= getResources().getInteger(R.integer.chat_sdk_max_message_lines))
                return true;
        }
        return false;
    }

    public void setMessageBoxOptionsListener(MessageBoxOptionsListener messageBoxOptionsListener) {
        this.messageBoxOptionsListener = messageBoxOptionsListener;
    }

    public void setMessageSendListener(MessageSendListener messageSendListener) {
        this.messageSendListener = messageSendListener;
    }

    public String getMessageText(){
        return etMessage.getText().toString();
    }

    public void clearText(){
        etMessage.getText().clear();
    }

    /*Getters and Setters*/
    public void setAlertToast(SuperToast alertToast) {
        this.alertToast = alertToast;
    }

    public SuperToast getAlertToast() {
        return alertToast;
    }




    public interface MessageBoxOptionsListener{
        public void onLocationPressed();
        public void onTakePhotoPressed();
        public void onPickImagePressed();
        public void onRecordAudioPressed();
        public void onPickAudioPressed();
        public void onRecordVideoPressed();
        public void onPickVideoPressed();

        /** Invoked when the option button pressed, If returned true the system wont show the option popup.*/
        public boolean onOptionButtonPressed();
    }

    public interface MessageSendListener {
        public void onSendPressed(String text);
    }
}
