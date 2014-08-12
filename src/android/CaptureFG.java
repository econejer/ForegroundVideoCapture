/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package com.tuxpan.foregroundvideo;

import java.io.File;
import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

public class CaptureFG extends CordovaPlugin {

	private static final String VIDEO_3GPP = "video/3gpp";

	private static final String LOG_TAG = "CaptureFG";

	// private static final int CAPTURE_APPLICATION_BUSY = 1;
	// private static final int CAPTURE_INVALID_ARGUMENT = 2;
	private static final int CAPTURE_NO_MEDIA_FILES = 3;

	private CallbackContext callbackContext; // The callback context from which
												// we were invoked.
	private int duration; // optional max duration of video recording in seconds
	private JSONArray results; // The array of results to be returned to the
								// user
	// private CordovaInterface cordova;

	// public void setContext(Context mCtx)
	// {
	// if (CordovaInterface.class.isInstance(mCtx))
	// cordova = (CordovaInterface) mCtx;
	// else
	// LOG.d(LOG_TAG,
	// "ERROR: You must use the CordovaInterface for this to work correctly. Please implement it in your activity");
	// }

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
		this.duration = 0;
		this.results = new JSONArray();

		JSONObject options = args.optJSONObject(0);
		if (options != null) {
			duration = options.optInt("duration", 0);
		}

		this.captureVideo(duration);

		return true;
	}

	private String getTempDirectoryPath() {
		File cache = null;

		// SD Card Mounted
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			cache = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + cordova.getActivity().getPackageName() + "/cache/");
		}
		// Use internal storage
		else {
			cache = cordova.getActivity().getCacheDir();
		}

		// Create the cache directory if it doesn't exist
		cache.mkdirs();
		return cache.getAbsolutePath();
	}

	/**
	 * Sets up an intent to capture video. Result handled by onActivityResult()
	 */
	private void captureVideo(int duration) {
		// Intent intent = new
		// Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
		//
		// if(Build.VERSION.SDK_INT > 7){
		// intent.putExtra("android.intent.extra.durationLimit", duration);
		// }

		Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), RecorderActivity.class);
		intent.putExtra("android.intent.extra.durationLimit", duration);
		File video = new File(getTempDirectoryPath(), "video.3gp");
		intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(video));
		this.cordova.startActivityForResult((CordovaPlugin) this, intent, 0);
	}

	/**
	 * Called when the video view exits.
	 * 
	 * @param requestCode
	 *            The request code originally supplied to
	 *            startActivityForResult(), allowing you to identify who this
	 *            result came from.
	 * @param resultCode
	 *            The integer result code returned by the child activity through
	 *            its setResult().
	 * @param intent
	 *            An Intent, which can return result data to the caller (various
	 *            data can be attached to Intent "extras").
	 * @throws JSONException
	 */
	public void onActivityResult(int requestCode, int resultCode, final Intent intent) {

		// Result received okay
		if (resultCode == Activity.RESULT_OK) {
			// An audio clip was requested

			final CaptureFG that = this;
			Runnable captureVideo = new Runnable() {

				@Override
				public void run() {

					Uri data = null;

					if (intent != null) {
						// Get the uri of the video clip
						data = intent.getData();
					}

					if (data == null) {
						File movie = new File(getTempDirectoryPath(), "video.3gp");
						data = Uri.fromFile(movie);
					}

					// create a file object from the uri
					if (data == null) {
						that.fail(createErrorObject(CAPTURE_NO_MEDIA_FILES, "Error: data is null"));
					} else {
						results.put(createMediaFile(data));
						// Send Uri back to JavaScript for viewing video
						that.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, results));
					}
				}
			};
			this.cordova.getThreadPool().execute(captureVideo);
		}
		// If canceled
		else if (resultCode == Activity.RESULT_CANCELED) {
			// If we have partial results send them back to the user
			if (results.length() > 0) {
				this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, results));
			}
			// user canceled the action
			else {
				this.fail(createErrorObject(CAPTURE_NO_MEDIA_FILES, "Canceled."));
			}
		}
		// If something else
		else {
			// If we have partial results send them back to the user
			if (results.length() > 0) {
				this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, results));
			}
			// something bad happened
			else {
				this.fail(createErrorObject(CAPTURE_NO_MEDIA_FILES, "Did not complete!"));
			}
		}
	}

	/**
	 * Creates a JSONObject that represents a File from the Uri
	 * 
	 * @param data
	 *            the Uri of the audio/image/video
	 * @return a JSONObject that represents a File
	 * @throws IOException
	 */
	private JSONObject createMediaFile(Uri data) {
		File fp = webView.getResourceApi().mapUriToFile(data);
		JSONObject obj = new JSONObject();

		try {
			// File properties
			obj.put("name", fp.getName());
			obj.put("fullPath", fp.toURI().toString());
			// Because of an issue with MimeTypeMap.getMimeTypeFromExtension()
			// all .3gpp files
			// are reported as video/3gpp. I'm doing this hacky check of the URI
			// to see if it
			// is stored in the audio or video content store.
			obj.put("type", VIDEO_3GPP);

			obj.put("lastModifiedDate", fp.lastModified());
			obj.put("size", fp.length());
		} catch (JSONException e) {
			// this will never happen
			e.printStackTrace();
		}
		return obj;
	}

	private JSONObject createErrorObject(int code, String message) {
		JSONObject obj = new JSONObject();
		try {
			obj.put("code", code);
			obj.put("message", message);
		} catch (JSONException e) {
			// This will never happen
		}
		return obj;
	}

	/**
	 * Send error message to JavaScript.
	 * 
	 * @param err
	 */
	public void fail(JSONObject err) {
		this.callbackContext.error(err);
	}

}
