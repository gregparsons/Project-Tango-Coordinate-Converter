/*
 * Copyright 2014 Google Inc. All Rights Reserved.
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

package com.swimr.tango.coordinateconverter;

import java.util.ArrayList;

import android.content.Context;
import android.hardware.*;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.swimr.tango.coordinateconverter.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import com.swimr.MathUtils.GpMath;


/**
 * Main Activity for the Tango Java Quickstart. Demonstrates establishing a
 * connection to the {@link Tango} service and printing the
 * data to the LogCat. Also demonstrates Tango lifecycle management through
 * {@link TangoConfig}.
 */
public class MainActivity extends Activity implements SensorEventListener, LocationListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String sTranslationFormat = "Tango ENU Coordinate Frame: %f, %f, %f";
    private static final String sRotationFormat = "Rotation: %f, %f, %f, %f";
	private static final String nedFrameFormat = "World NED Coordinate Frame: %f, %f, %f";
	private static final String sPoseStatusFormat = "Status: %s";
	private static final String headingFormat = "Heading Mag: %f, True: %f";
	private static final String wgs84TextFormat = "WGS84:\nGPS: \t\t%f/%f, Alt: %f, Accuracy: %f";

	private TextView statusTextView;
	private TextView headingTextView;

	private TextView tangoTranslationTextView;
    private TextView tangoRotationTextView;
	private TextView worldFrameCoordsTextView;
	private TextView wgs84CoordsTextview;

    private Tango mTango;
    private TangoConfig mConfig;
    private boolean mIsTangoServiceConnected;
    private boolean isProcessingPoseData = false;


	// Compass
	// http://www.ssaurel.com/blog/learn-how-to-make-a-compass-application-for-android/
	private SensorManager sensorManager;
	private Sensor sensorMagnetic;
	private Sensor sensorAccelerometer;
	private float[] gravityVector; // = new float[3];
	private float[] geomagneticVector; // = new float[3];
	private float[] magOrientationVector = new float[3];
	private float[] latestMagRotMatrix = new float[9];
	private boolean isLatestRotationMatrixReady = false;
	private GeomagneticField mGeomagneticField;			//use to get declination of magnetic from true north...if GPS available
	static final float GP_DEFAULT_DECLINATION = 12.5f; 	// Goleta, CA, http://www.magnetic-declination.com/
	private double latestHeadingTrue = 0.0f;
	private double permanentHeadingTrue = 0.0f;
	static int sPreviousPoseStatus = TangoPoseData.POSE_UNKNOWN;

	//GPS
	protected  LocationManager locationMgr;
	static Location lastLocation;



	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


		statusTextView = (TextView)findViewById(R.id.tango_status_textview);
		headingTextView = (TextView)findViewById(R.id.heading_text_view);
        tangoTranslationTextView = (TextView) findViewById(R.id.translation_text_view);
        tangoRotationTextView = (TextView) findViewById(R.id.rotation_text_view);
		worldFrameCoordsTextView = (TextView) findViewById(R.id.ned_frame_text_view);
		wgs84CoordsTextview = (TextView) findViewById(R.id.wgs84_textview);

		//Initialize textviews
		wgs84CoordsTextview.setText(String.format(wgs84TextFormat, 0.0, 0.0, 0.0, 0.0));

        // Instantiate Tango client
        mTango = new Tango(this);

        // Set up Tango configuration for motion tracking
        // If you want to use other APIs, add more appropriate to the config
        // like: mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true)
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);		// reqd for area description base frame
		mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
			// otherwise doesn't try to restart...does this create
			// a new origin?

		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensorMagnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		locationMgr = (LocationManager)getSystemService(Context.LOCATION_SERVICE);


    }

    @Override
    protected void onResume() {

        // Lock the Tango configuration and reconnect to the service each time
        // the app
        // is brought to the foreground.
        super.onResume();
        if (!mIsTangoServiceConnected) {
            startActivityForResult(Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_MOTION_TRACKING), Tango.TANGO_INTENT_ACTIVITYCODE);
        }


		// Compass
		sensorManager.registerListener(this, sensorMagnetic, SensorManager.SENSOR_DELAY_NORMAL);
		sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

		if(locationMgr!=null){

			locationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 300, 1, this);
			Location location = locationMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if(location!=null)
				updateGpsUi(location);
		}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == Tango.TANGO_INTENT_ACTIVITYCODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this,"This app requires Motion Tracking permission!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            try {
                setTangoListeners();
            } catch (TangoErrorException e) {
                Toast.makeText(this, "Tango Error! Restart the app!", Toast.LENGTH_SHORT).show();
            }
            try {
                mTango.connect(mConfig);
                mIsTangoServiceConnected = true;
            } catch (TangoOutOfDateException e) {
                Toast.makeText(getApplicationContext(), "Tango Service out of date!", Toast.LENGTH_SHORT).show();
            } catch (TangoErrorException e) {
                Toast.makeText(getApplicationContext(), "Tango Error! Restart the app!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When the app is pushed to the background, unlock the Tango
        // configuration and disconnect
        // from the service so that other apps will behave properly.
        try {
            mTango.disconnect();
            mIsTangoServiceConnected = false;
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), "Tango Error During Disconnect!", Toast.LENGTH_SHORT).show();
        }

		//Compass
		sensorManager.unregisterListener(this);

		stopGps();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

		stopGps();
    }




    private void setTangoListeners() {
        // Select coordinate frame pairs
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();

		// base, target frames
        //framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE, TangoPoseData.COORDINATE_FRAME_DEVICE));
		framePairs.add((new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION, TangoPoseData.COORDINATE_FRAME_DEVICE)));

        // Tango Event Listener w/ methods
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

			/**
			 * Called as fast as hardware can generate a new TangoPoseData, depending on the target and base coordinate frames used.
			 * @param pose
			 */
			@SuppressLint("DefaultLocale")
			@Override
			public void onPoseAvailable(TangoPoseData pose) {

				// pose: pose of the target frame with respect to the base frame
				if (isProcessingPoseData) {
					// Log.i(TAG, "Processing UI");
					return;
				}
				isProcessingPoseData = true;

				// Freeze magnetic rotation matrix:
				// If status wasn't "valid" before but now is means Tango just initialized. Save the magnetic rotation matrix for
				// use in conversion of this pose to world grid.
				if (sPreviousPoseStatus != TangoPoseData.POSE_VALID && pose.statusCode == TangoPoseData.POSE_VALID) {

					if (isLatestRotationMatrixReady == true) {

						//freeze this rotation matrix. this is azimuth the start-of-service grid is oriented on versus world
						//permanentRotationMatrix = latestMagRotMatrix.clone();
						permanentHeadingTrue = latestHeadingTrue;
					}
				}
				sPreviousPoseStatus = pose.statusCode;

				//if previous pose was VALID but now it's NOT
				//set permanentRotationMatrix to null, or just leave it be.

				// UI: Status text
				final String statusMsg = String.format(sPoseStatusFormat, getPoseDataStatusString(pose.statusCode));

				// UI: Translation and Rotation text
				final String translationMsg = String.format(sTranslationFormat, pose.translation[0], pose.translation[1], pose.translation[2]);
				final String rotationMsg = String.format(sRotationFormat, pose.rotation[0], pose.rotation[1], pose.rotation[2], pose.rotation[3]);

				// UI: World NED text
				//Rotate the pose x,y plane by the heading degrees. X/Y plane is supposed to be perpendicular to Z so
				//should be able to rotate by degrees off of North to get world x, y.
				// Tango_ENU_xyz => World_NED_xyz = (tangoY, tangoX, -tangoZ)
				Double[] worldXY = GpMath.rotate2dClockwise((double) pose.translation[1], (double) pose.translation[0], (double) permanentHeadingTrue);
				final String nedFrameMsg = String.format(nedFrameFormat, worldXY[0].floatValue(), worldXY[1].floatValue(), -pose.translation[2]);

				// Update UI
				runOnUiThread(new Runnable() {
					@Override
					public void run() {

						//Update UI
						statusTextView.setText(statusMsg);
						tangoTranslationTextView.setText(translationMsg);
						tangoRotationTextView.setText(rotationMsg);
						worldFrameCoordsTextView.setText(nedFrameMsg);

						isProcessingPoseData = false;
					}
				});
			}

			@Override
			public void onXyzIjAvailable(TangoXyzIjData arg0) {
				// Ignore XyzIj data
			}

			@Override
			public void onTangoEvent(TangoEvent arg0) {
				// Ignore TangoEvents

			}

			@Override
			public void onFrameAvailable(int arg0) {
				// Ignore onFrameAvailable Events

			}
		});
    }



	// Compass

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		Log.i(TAG, "[onAccuracyChanged] " + sensor.getName());
	}

	void updateHeading(){
		// update heading
		if(geomagneticVector != null && gravityVector != null) {

			// https://developer.android.com/reference/android/hardware/SensorManager.html#getRotationMatrix(float[], float[], float[], float[])
			if (SensorManager.getRotationMatrix(latestMagRotMatrix, null, gravityVector, geomagneticVector) == true) {

				//only do this once? No do it all the time, capture it only when Tango's Start-of-Service frame is fixed
				// do this above when tango base frame is set
				//if (isLatestRotationMatrixReady == false)
				//	permanentRotationMatrix = latestMagRotMatrix.clone();
				isLatestRotationMatrixReady = true;

			}
			else{
				Log.i(TAG, "[onSensorChanged] Can't get heading.");
				return;
			}

			// https://developer.android.com/reference/android/hardware/SensorManager.html#getOrientation(float[], float[])
			// Gets magnetic orientation. Y axis is compass heading from north, in radians? Or X?
			//bearing in radians
			//[0]: azimuth
			//[1]: pitch
			//[2]: roll
			SensorManager.getOrientation(latestMagRotMatrix, magOrientationVector);
			double headingMagnetic = Math.toDegrees(magOrientationVector[0]);

			//Convert magnetic to true
			double headingTrue = headingMagnetic;
			if (mGeomagneticField == null) {
				// use Goleta Ca
				headingTrue -= GP_DEFAULT_DECLINATION;
			} else {
				headingTrue -= mGeomagneticField.getDeclination();

			}

			// not sure this will work near 360
			//LowPassFilter.lowPassFilterFloat(headingTrue, latestHeadingTrue);

			//make sure between 0 and 359
			headingTrue = GpMath.fixHeadingBetween0and359((double)headingTrue);
			latestHeadingTrue = headingTrue;
			headingMagnetic = GpMath.fixHeadingBetween0and359(headingMagnetic);


			// Update UI
			//Create a new thread every time??? Seems like a bad use of resources.
			final String headingString = String.format(headingFormat, headingMagnetic, headingTrue);
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					headingTextView.setText(headingString);
				}
			});
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		// update static sensor data
		switch(event.sensor.getType()){
			case Sensor.TYPE_MAGNETIC_FIELD:{
				geomagneticVector = event.values.clone();
				updateHeading();
				break;
			}
			case Sensor.TYPE_ACCELEROMETER:{
				gravityVector = event.values.clone();
				updateHeading();
 				break;
			}
		}
	}



	//GPS


	@Override
	public void onLocationChanged(final Location location) {
		if(location!=null)
			updateGpsUi(location);
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.i(TAG, "[location.onStatusChanged] status (OOS, Temp Unavail, Avail): " + status);
	}


	@Override
	public void onProviderEnabled(String provider) {
		Log.i(TAG, "[onProviderEnabled]");
	}

	@Override
	public void onProviderDisabled(String provider) {
		Log.i(TAG, "[onProviderDisabled]");

	}

	void updateGpsUi(final Location location){
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(location!=null) {
					String locText = String.format(wgs84TextFormat, location.getLatitude(), location.getLongitude(),
							location.getAltitude(), location.getAccuracy());
					Log.i(TAG, locText);
					wgs84CoordsTextview.setText(locText);
				}
			}
		});

	}

	void stopGps(){
		if(locationMgr!=null){
			locationMgr.removeUpdates(this);
		}
	}


	// TANGO

	/**
	 * Get a real string from the TangoPoseData status code.
	 *
	 * @param
	 * @return
	 */
	String getPoseDataStatusString(final int statusCode){

		switch (statusCode){
			case TangoPoseData.POSE_INITIALIZING:
				return "Initializing";
			//break;
			case TangoPoseData.POSE_UNKNOWN:
				return "Unknown";
			//break;
			case TangoPoseData.POSE_INVALID:
				return "Invalid";
			//break;
			case TangoPoseData.POSE_VALID:
				return "Valid";
			//break;
			default:
				break;

		}
		return "No known status";
	}

}
