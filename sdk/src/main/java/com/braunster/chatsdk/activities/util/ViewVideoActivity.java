package com.braunster.chatsdk.activities.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import com.braunster.chatsdk.R;
import com.braunster.chatsdk.Utils.Debug;
import com.braunster.chatsdk.network.BDefines;
import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import timber.log.Timber;

/**
 * Created by Erk on 24.06.2016.
 */
public class ViewVideoActivity extends Activity {

    private static final String TAG = ViewVideoActivity.class.getSimpleName();
    private static final boolean DEBUG = Debug.ViewVideoActivity;

    private ProgressBar progressBar;
    private VideoView videoView;

    private String videoPath;
    private String tempVideoPath;
    private String tempVideoPath2;

    private boolean needsReEncoding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat_sdk_popup_video);

        Intent intent = getIntent();

        videoView = (VideoView) findViewById(R.id.chat_sdk_popup_video_videoview);
        progressBar = (ProgressBar) findViewById(R.id.chat_sdk_popup_video_progressbar);

        progressBar.setVisibility(View.VISIBLE);

        String loadType = intent.getStringExtra("loadType");
        if(DEBUG) Timber.v("load from " + loadType);

        videoPath = intent.getStringExtra("url");
        if(DEBUG) Timber.v("url " + videoPath);

        String[] urls = videoPath.split("\\?")[0].split("\\.");
        String fileEnding = urls[urls.length - 1];

        if(DEBUG) Timber.v("file ending " + fileEnding);

        if(fileEnding.equals("mov")) {
            needsReEncoding = true;
        } else {
            needsReEncoding = false;
        }

        tempVideoPath = Environment.getExternalStorageDirectory() + "/tempVideo." + fileEnding;
        tempVideoPath2 = Environment.getExternalStorageDirectory() + "/tempVideo2" + BDefines.Options.VideoFormat;

        File vid1 = new File(tempVideoPath);
        if(vid1.exists()) vid1.delete();
        File vid2 = new File(tempVideoPath2);
        if(vid2.exists()) vid2.delete();

        if(loadType.equals("path")) {
            startVideo(videoPath);
        }

        if(loadType.equals("url")) {
            final DownloadTask downloadTask = new DownloadTask(ViewVideoActivity.this);
            downloadTask.execute(videoPath);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


    }

    private void startVideo(String path) {
        videoView.setVideoPath(path);
        progressBar.setVisibility(View.INVISIBLE);

        MediaController mediaControls = new MediaController(this);
        videoView.setMediaController(mediaControls);

        mediaControls.show();
        videoView.start();
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;
        private PowerManager.WakeLock mWakeLock;

        public DownloadTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(tempVideoPath);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
        }

        @Override
        protected void onPostExecute(String result) {
            mWakeLock.release();

            if (result != null) {
                if(DEBUG) Timber.v("Download error: " + result);
            } else {
                if(DEBUG) Timber.v("File downloaded");
            }

            if(needsReEncoding) {
                String cmd = "-i " + tempVideoPath + " -c:v libx264 -preset ultrafast " + tempVideoPath2;
                executeFFmpegCommand(cmd.split(" "));
            } else {
                startVideo(tempVideoPath);
            }
        }
    }

    public static void loadFFmpegBinary(Context context) {
        FFmpeg ffmpeg = FFmpeg.getInstance(context);
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onStart() {}

                @Override
                public void onFailure() {
                    if(DEBUG) Timber.v("failed to load FFmpeg binary");
                }

                @Override
                public void onSuccess() {
                    if(DEBUG) Timber.v("loaded FFmpeg binary successfully");
                }

                @Override
                public void onFinish() {}
            });
        } catch (FFmpegNotSupportedException e) {
            e.printStackTrace();
        }
    }

    private void executeFFmpegCommand(final String[] command) {
        FFmpeg ffmpeg = FFmpeg.getInstance(this);
        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
            ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {

                @Override
                public void onStart() {
                    if(DEBUG) Timber.v("encoding started");
                }

                @Override
                public void onProgress(String message) {
                    if(DEBUG) Timber.v("progress: " + message);
                }

                @Override
                public void onFailure(String message) {
                    if(DEBUG) Timber.v("encoding failed: " + message);
                }

                @Override
                public void onSuccess(String message) {
                    if(DEBUG) Timber.v("encoding successful");
                    videoView.setVideoPath(tempVideoPath2);
                    progressBar.setVisibility(View.INVISIBLE);

                    startVideo(tempVideoPath2);
                }

                @Override
                public void onFinish() {
                    if(DEBUG) Timber.v("encoding finished");
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            e.printStackTrace();
        }
    }
}
