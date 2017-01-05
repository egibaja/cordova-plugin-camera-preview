package com.mbppower;

import android.Manifest;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.hardware.Camera;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.graphics.PixelFormat;

import org.apache.cordova.PermissionHelper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

public class CameraPreview extends CordovaPlugin implements CameraActivity.CameraListener {

    private final String TAG = "CameraPreview";
    private final String setOnPictureTakenHandlerAction = "setOnPictureTakenHandler";
    private final String setColorEffectAction = "setColorEffect";
    private final String startCameraAction = "startCamera";
    private final String stopCameraAction = "stopCamera";
    private final String switchCameraAction = "switchCamera";
    private final String takePictureAction = "takePicture";
    private final String showCameraAction = "showCamera";
    private final String hideCameraAction = "hideCamera";

    protected final String permission = Manifest.permission.CAMERA;
    private final int permissionsReqId = 0;
    private CallbackContext execCallback;
    private JSONArray execArgs;


    private CameraActivity fragment;
    private CallbackContext takePictureCallbackContext;
    private int containerViewId = 1;

    public CameraPreview(){
        super();
        Log.d(TAG, "Constructing");
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (setOnPictureTakenHandlerAction.equals(action)){
            Log.e(TAG, "setOnPictureTakenHandlerAction");
            return setOnPictureTakenHandler(args, callbackContext);
        }
        else if (startCameraAction.equals(action)){
            Log.e(TAG, "startCameraAction");
            execCallback = callbackContext;
            execArgs = args;
            if (cordova.hasPermission(permission)) {
              return startCamera(args, callbackContext);
            }
            else {
              cordova.requestPermission(this, permissionsReqId, permission);
              return true;
            }
        }
        else if (takePictureAction.equals(action)){
            Log.e(TAG, "takePictureAction");
            return takePicture(args, callbackContext);
        }
        else if (setColorEffectAction.equals(action)){
          return setColorEffect(args, callbackContext);
        }
        else if (stopCameraAction.equals(action)){
            Log.e(TAG, "stopCameraAction");
            return stopCamera(args, callbackContext);
        }
        else if (hideCameraAction.equals(action)){
            return hideCamera(args, callbackContext);
        }
        else if (showCameraAction.equals(action)){
            Log.e(TAG, "showCameraAction");
            return showCamera(args, callbackContext);
        }
        else if (switchCameraAction.equals(action)){
            return switchCamera(args, callbackContext);
        }

        return false;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        for(int r:grantResults)
            {
              if(r == PackageManager.PERMISSION_DENIED)
              {
                execCallback.sendPluginResult(new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION));
                execCallback = null;
                return;
              }
            }
            if (requestCode == permissionsReqId) {
              startCamera(execArgs, execCallback);
            }

    }


    private boolean startCamera(final JSONArray args, CallbackContext callbackContext) {
        Log.e(TAG, "Dentro de startCamera");
        if(fragment != null){
            return false;
        }
        fragment = new CameraActivity();
        fragment.setEventListener(this);

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                try {
                    DisplayMetrics metrics = cordova.getActivity().getResources().getDisplayMetrics();
                    int x = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(0), metrics);
                    int y = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(1), metrics);
                    int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(2), metrics);
                    int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, args.getInt(3), metrics);
                    String defaultCamera = args.getString(4);
                    Boolean tapToTakePicture = args.getBoolean(5);
                    Boolean dragEnabled = args.getBoolean(6);
                    Boolean toBack = args.getBoolean(7);

                    fragment.defaultCamera = defaultCamera;
                    fragment.tapToTakePicture = tapToTakePicture;
                    fragment.dragEnabled = dragEnabled;
                    fragment.setRect(x, y, width, height);

                    Camera.Parameters params = fragment.getCameraParameters(); 
                    List<Camera.Size> sizes = params.getSupportedPictureSizes();
                    //searches for good picture quality
                    Camera.Size bestDimens = null;
                    for(Camera.Size dimens : sizes){
                        if(dimens.width  <= 1200 && dimens.height <= 1920){
                            if (bestDimens == null || (dimens.width > bestDimens.width && dimens.height > bestDimens.height)) {
                                bestDimens = dimens;
                            }
                        }
                    }
                    params.set("jpeg-quality", 90);
                    params.setPictureFormat(PixelFormat.JPEG);
                    params.setPictureSize(bestDimens.width, bestDimens.height);
                    fragment.setCameraParameters(params);



                    //create or update the layout params for the container view
                    FrameLayout containerView = (FrameLayout)cordova.getActivity().findViewById(containerViewId);
                    if(containerView == null){
                        containerView = new FrameLayout(cordova.getActivity().getApplicationContext());
                        containerView.setId(containerViewId);

                        FrameLayout.LayoutParams containerLayoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                        cordova.getActivity().addContentView(containerView, containerLayoutParams);
                    }
                    //display camera bellow the webview
                    if(toBack){
                        webView.getView().setBackgroundColor(0x00000000);
                        ((ViewGroup)webView.getView()).bringToFront();
                    }
                    else{
                        //set camera back to front
                        containerView.setAlpha(Float.parseFloat(args.getString(8)));
                        containerView.bringToFront();
                    }

                    //add the fragment to the container
                    FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.add(containerView.getId(), fragment);
                    fragmentTransaction.commit();
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
        return true;
    }
    private boolean takePicture(final JSONArray args, CallbackContext callbackContext) {
        if(fragment == null){
            return false;
        }
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        try {
            double maxWidth = args.getDouble(0);
            double maxHeight = args.getDouble(1);
            Log.d(TAG, "en takePicture en preview las dimensiones son " + (int)Math.floor(maxWidth) + "x" + (int)Math.floor(maxHeight));
            fragment.takePicture((int)Math.floor(maxWidth), (int)Math.floor(maxHeight));
        }
        catch(Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void onPictureTaken(String originalPicturePath){
        JSONArray data = new JSONArray();
        data.put(originalPicturePath).put(null);
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, data);
        pluginResult.setKeepCallback(true);
        takePictureCallbackContext.sendPluginResult(pluginResult);
    }

    private boolean setColorEffect(final JSONArray args, CallbackContext callbackContext) {
      if(fragment == null){
        return false;
      }

    Camera camera = fragment.getCamera();
    if (camera == null){
      return true;
    }

    Camera.Parameters params = camera.getParameters();

    try {
      String effect = args.getString(0);

      if (effect.equals("aqua")) {
        params.setColorEffect(Camera.Parameters.EFFECT_AQUA);
      } else if (effect.equals("blackboard")) {
        params.setColorEffect(Camera.Parameters.EFFECT_BLACKBOARD);
      } else if (effect.equals("mono")) {
        params.setColorEffect(Camera.Parameters.EFFECT_MONO);
      } else if (effect.equals("negative")) {
        params.setColorEffect(Camera.Parameters.EFFECT_NEGATIVE);
      } else if (effect.equals("none")) {
        params.setColorEffect(Camera.Parameters.EFFECT_NONE);
      } else if (effect.equals("posterize")) {
        params.setColorEffect(Camera.Parameters.EFFECT_POSTERIZE);
      } else if (effect.equals("sepia")) {
        params.setColorEffect(Camera.Parameters.EFFECT_SEPIA);
      } else if (effect.equals("solarize")) {
        params.setColorEffect(Camera.Parameters.EFFECT_SOLARIZE);
      } else if (effect.equals("whiteboard")) {
        params.setColorEffect(Camera.Parameters.EFFECT_WHITEBOARD);
      }

      fragment.setCameraParameters(params);
        return true;
    } catch(Exception e) {
      e.printStackTrace();
      return false;
    }
    }

    private boolean stopCamera(final JSONArray args, CallbackContext callbackContext) {
        if(fragment == null){
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.remove(fragment);
        fragmentTransaction.commit();
        fragment = null;

        return true;
    }

    private boolean showCamera(final JSONArray args, CallbackContext callbackContext) {
        if(fragment == null){
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.show(fragment);
        fragmentTransaction.commit();

        return true;
    }
    private boolean hideCamera(final JSONArray args, CallbackContext callbackContext) {
        if(fragment == null) {
            return false;
        }

        FragmentManager fragmentManager = cordova.getActivity().getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.hide(fragment);
        fragmentTransaction.commit();

        return true;
    }
    private boolean switchCamera(final JSONArray args, CallbackContext callbackContext) {
        if(fragment == null){
            return false;
        }
        fragment.switchCamera();
        return true;
    }

    private boolean setOnPictureTakenHandler(JSONArray args, CallbackContext callbackContext) {
        Log.d(TAG, "setOnPictureTakenHandler");
        takePictureCallbackContext = callbackContext;
        return true;
    }
}
