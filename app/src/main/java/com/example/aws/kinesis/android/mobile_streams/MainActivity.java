package com.example.aws.kinesis.android.mobile_streams;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.kinesis.kinesisrecorder.KinesisRecorder;
import com.amazonaws.mobileconnectors.kinesis.kinesisrecorder.KinesisFirehoseRecorder;
import com.amazonaws.regions.Regions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //Parameters configured in res/values/application_settings.xml
    private String cognitoIdentityPoolId;
    private Regions region;
    private String kinesisStreamName;
    private String firehoseStreamName;

    protected static final String APPLICATION_NAME = "android-mobile-streams";
    protected static final double RAD2DEG = 180 / Math.PI;
    protected KinesisRecorder kinesisRecorder;
    protected KinesisFirehoseRecorder firehoseRecorder;
    protected SensorManager sensorManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ");

    private String androidId;
    private Button button1;
    private Button button2;
    private Button button3;
    private Button sensorButton;
    private TextView azimuthText;
    private TextView pitchText;
    private TextView rollText;
    private boolean sensorEnabled = false;

    private float[] rotationMatrix = new float[9];
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] attitude = new float[3];

    /**
     * Initializing
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        initUI();
        initRecorder();
        initSensor();
        saveFirehoseRecord("onCreate");
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(
                this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_GAME);
    }

    /**
     * Initializing UI components
     */
    private void initUI() {
        // Find UI components
        button1 = (Button) findViewById(R.id.button1);
        button2 = (Button) findViewById(R.id.button2);
        button3 = (Button) findViewById(R.id.button3);
        sensorButton = (Button) findViewById(R.id.sensor_button);
        azimuthText = (TextView) findViewById(R.id.azimuth);
        pitchText = (TextView) findViewById(R.id.pitch);
        rollText = (TextView) findViewById(R.id.roll);

        // Set listeners for Firehose buttons
        button1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = "Button1 pressed";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                saveFirehoseRecord(msg);
            }
        });
        button2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = "Button2 pressed";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                saveFirehoseRecord(msg);
            }
        });
        button3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = "Button3 pressed - submit events";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... v) {
                        String msg = "Button3 pressed - submit events";
                        try {
                            saveFirehoseRecord(msg);
                            firehoseRecorder.submitAllRecords();
                        } catch (AmazonClientException ace) {
                            Log.e(APPLICATION_NAME, "firehose.submitAll failed");
                            initRecorder();
                        }
                        return null;
                    }
                }.execute();
            }
        });

        // Set listeners for Sensor Stream buttons
        sensorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sensorEnabled) {
                    // Stop sensor measurement
                    sensorEnabled = false;
                    azimuthText.setText("-");
                    pitchText.setText("-");
                    rollText.setText("-");
                    sensorButton.setText("START SENSOR STREAM");
                    Toast.makeText(MainActivity.this, "Stopped sensor stream", Toast.LENGTH_LONG).show();
                } else {
                    // Start sensor measurement
                    sensorEnabled = true;
                    sensorButton.setText("STOP SENSOR STREAM");
                    Toast.makeText(MainActivity.this, "Started sensor stream", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Initializing Kinesis recorders
     *
     * @see http://docs.aws.amazon.com/ja_jp/mobile/sdkforandroid/developerguide/kinesis.html
     */
    protected void initRecorder() {
        cognitoIdentityPoolId = getString(R.string.cognito_identity_pool_id);
        region = Regions.fromName(getString(R.string.region));
        androidId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        // Get credential from Cognito Identiry Pool
        File directory = getApplicationContext().getCacheDir();
        AWSCredentialsProvider provider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                cognitoIdentityPoolId,
                region);
        // Create Kinesis recorders
        kinesisRecorder = new KinesisRecorder(directory, region, provider);
        firehoseRecorder = new KinesisFirehoseRecorder(directory, region, provider);
    }

    /**
     * Initializing sensor manager
     */
    protected void initSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    /**
     * Submit a record to Kinesis Stream
     *
     * @param azimuth
     * @param pitch
     * @param roll
     */
    public void submitKinesisRecord(int azimuth, int pitch, int roll) {
        kinesisStreamName = getString(R.string.kinesis_stream_name);
        JSONObject json = new JSONObject();
        try {
            json.accumulate("time", sdf.format(new Date()));
            json.accumulate("android_id", androidId);
            json.accumulate("azimuth", azimuth);
            json.accumulate("pitch", pitch);
            json.accumulate("roll", roll);
            Log.e(APPLICATION_NAME, json.toString());
            kinesisRecorder.saveRecord(json.toString(), kinesisStreamName);
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... v) {
                    try {
                        kinesisRecorder.submitAllRecords();
                    } catch (AmazonClientException ace) {
                        Log.e(APPLICATION_NAME, "kinesis.submitAll failed");
                        initRecorder();
                    }
                    return null;
                }
            }.execute();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save a record in Kinesis Firehose client (will not send cloud-side yet)
     *
     * @param put_string
     */
    public void saveFirehoseRecord(String put_string) {
        firehoseStreamName = getString(R.string.firehose_stream_name);
        JSONObject json = new JSONObject();
        try {
            json.accumulate("time", sdf.format(new Date()));
            json.accumulate("model", Build.MODEL);
            json.accumulate("android_id", androidId);
            json.accumulate("app_name", APPLICATION_NAME);
            json.accumulate("message", put_string);
            Log.e(APPLICATION_NAME, json.toString());
            firehoseRecorder.saveRecord(json.toString(), firehoseStreamName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Sensor data trigger
     *
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Check if sensor stream has started
        if (sensorEnabled) {
            // Measure sensor values
            switch (event.sensor.getType()) {
                case Sensor.TYPE_MAGNETIC_FIELD:
                    geomagnetic = event.values.clone();
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    gravity = event.values.clone();
                    break;
            }

            if (geomagnetic != null && gravity != null) {
                // Calculate azimuth, pitch and roll from sensor values
                SensorManager.getRotationMatrix(
                        rotationMatrix, null,
                        gravity, geomagnetic);
                SensorManager.getOrientation(
                        rotationMatrix,
                        attitude);
                int azimuth = (int) (attitude[0] * RAD2DEG);
                int pitch = (int) (attitude[1] * RAD2DEG);
                int roll = (int) (attitude[2] * RAD2DEG);

                // Submit azimuth, pitch and roll to Kinesis Stream
                submitKinesisRecord(azimuth, pitch, roll);

                // Update display to new azimuth, pitch and roll
                azimuthText.setText(Integer.toString(azimuth));
                pitchText.setText(Integer.toString(pitch));
                rollText.setText(Integer.toString(roll));
            }
        }
    }

}