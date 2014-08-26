package com.tuxpan.foregroundvideocapture;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

public class RecorderActivity extends Activity implements OnClickListener, SurfaceHolder.Callback {

	public static final String LOGTAG = "VIDEOCAPTURE";

	private MediaRecorder recorder;
	private SurfaceHolder holder;
	private CamcorderProfile camcorderProfile;
	private Camera camera;

	private boolean recording = false;
	private boolean usecamera = true;
	private boolean previewRunning = false;

	private SurfaceView cameraView;
	private Button captureButton;
	private CountDownTimer timer;
	private int maxDuration;
	private Animation animScale;
	private TextView mTextField;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);

		setContentView(getResources().getIdentifier("main", "layout", getPackageName()));

		cameraView = (SurfaceView) findViewById(getResources().getIdentifier("CameraView", "id", getPackageName()));

		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


		captureButton = (Button) findViewById(getResources().getIdentifier("button_capture", "id", getPackageName()));
		maxDuration = (Integer) getIntent().getExtras().get("android.intent.extra.durationLimit");

		captureButton.setOnClickListener(this);
		
		animScale = AnimationUtils.loadAnimation(this, getResources().getIdentifier("anim_scale", "anim", getPackageName()));
		
		mTextField = (TextView) findViewById(getResources().getIdentifier("textoTimer", "id", getPackageName()));
		
		timer = new CountDownTimer(maxDuration * 1000, 1000) {

			public void onTick(long millisUntilFinished) {
				mTextField.setText("seconds remaining: " + millisUntilFinished / 1000);
			}

			public void onFinish() {
				mTextField.setText("done!");
			}
		};
	}

	private void prepareRecorder() {
		recorder = new MediaRecorder();
		recorder.setPreviewDisplay(holder.getSurface());

		if (usecamera) {
			camera.unlock();
			recorder.setCamera(camera);
		}

		recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
		recorder.setMaxDuration(maxDuration * 1000);

		recorder.setProfile(camcorderProfile);
		
		Uri fileUri = (Uri) getIntent().getExtras().get(MediaStore.EXTRA_OUTPUT);
		recorder.setOutputFile(fileUri.getPath());
		
		recorder.setOnInfoListener(new OnInfoListener() {
		    @Override
		    public void onInfo(MediaRecorder mr, int what, int extra) {                     
		        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
//		        	mMediaRecorder.stop();
		        	setResult(RESULT_OK);
					finish();
		        }          
		    }
		});

		try {
			recorder.prepare();
		} catch (IllegalStateException e) {
			e.printStackTrace();
			finish();
		} catch (IOException e) {
			e.printStackTrace();
			finish();
		}
	}
	
	
	@Override
	protected void onPause() {
		super.onPause();
		// if we are using MediaRecorder, release it first
		releaseMediaRecorder();
		// release the camera immediately on pause event
		releaseCamera();
	}
	
	private void releaseMediaRecorder() {
		if (recorder != null) {
			// clear recorder configuration
			recorder.reset();
			// release the recorder object
			recorder.release();
			recorder = null;
			// Lock camera for later use i.e taking it back from MediaRecorder.
			// MediaRecorder doesn't need it anymore and we will release it if
			// the activity pauses.
			camera.lock();
		}
	}
	
	private void releaseCamera() {
		if (camera != null) {
			// release the camera for other applications
			camera.release();
			camera = null;
		}
	}

	public void onClick(View v) {
		v.startAnimation(animScale);
		if (recording) {
			recorder.stop();
			if (usecamera) {
				try {
					camera.reconnect();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// recorder.release();
			recording = false;
			Log.v(LOGTAG, "Recording Stopped");
			// Let's prepareRecorder so we can record again
			prepareRecorder();
			
			timer.cancel();
			setResult(RESULT_OK);
			finish();
			
		} else {
			recording = true;
			recorder.start();
			captureButton.setText("Stop");
			timer.start();
			
			Log.v(LOGTAG, "Recording Started");
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceCreated");

		if (usecamera) {
			camera = Camera.open();

			try {
				camera.setPreviewDisplay(holder);
				camera.startPreview();
				previewRunning = true;
			} catch (IOException e) {
				Log.e(LOGTAG, e.getMessage());
				e.printStackTrace();
			}
		}

	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.v(LOGTAG, "surfaceChanged");

		if (!recording && usecamera) {
			if (previewRunning) {
				camera.stopPreview();
			}

			try {
				Camera.Parameters p = camera.getParameters();

				List<Camera.Size> mSupportedPreviewSizes = p.getSupportedPreviewSizes();
				Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(mSupportedPreviewSizes, cameraView.getWidth(), cameraView.getHeight());

				p.setPreviewSize(optimalSize.width, optimalSize.height);

				camera.setParameters(p);

				camera.setPreviewDisplay(holder);
				camera.startPreview();
				previewRunning = true;
			} catch (IOException e) {
				Log.e(LOGTAG, e.getMessage());
				e.printStackTrace();
			}

			prepareRecorder();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.v(LOGTAG, "surfaceDestroyed");
		if (recording) {
			if(recorder != null){
				recorder.stop();
				recording = false;
			}
		}
		
		if(recorder != null)
			recorder.release();
		
		if (usecamera) {
			previewRunning = false;
			// camera.lock();
			if(camera != null)
				camera.release();
		}
		// finish();
	}
}