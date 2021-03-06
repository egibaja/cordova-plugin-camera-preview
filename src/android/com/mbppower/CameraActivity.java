package com.mbppower;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CameraActivity extends Fragment {

	public interface CameraListener {
		public void onPictureTaken(String originalPicturePath);
	}

	private CameraListener eventListener;
	private static final String TAG = "CameraActivity";
	public FrameLayout mainLayout;
	public FrameLayout frameContainerLayout;

    private Preview mPreview;
	private boolean canTakePicture = true;

	private View view;
	private Camera.Parameters cameraParameters;
	private Camera mCamera;
	private int numberOfCameras;
	private int cameraCurrentlyLocked;

    // The first rear facing camera
    private int defaultCameraId;
	public String defaultCamera;
	public boolean tapToTakePicture;
	public boolean dragEnabled;

	public int width;
	public int height;
	public int x;
	public int y;

	public void setEventListener(CameraListener listener){
		eventListener = listener;
	}

	private String appResourcesPackage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    appResourcesPackage = getActivity().getPackageName();

	    // Inflate the layout for this fragment
	    view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
	    createCameraPreview();
	    return view;
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
	public void setRect(int x, int y, int width, int height){
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	private void createCameraPreview(){
        if(mPreview == null) {
            setDefaultCameraId();

	        //set box position and size
	        Log.e(TAG, "fragment layout " + width + "x" + height);
	        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
	        Log.e(TAG, "margenes del layout layout " + x + "x" + y);
	        layoutParams.setMargins(x, y, 0, 0);
	        frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
	        frameContainerLayout.setLayoutParams(layoutParams);

	        //video view
	        mPreview = new Preview(getActivity());
	        mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
	        mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
	        mainLayout.addView(mPreview);
	        mainLayout.setEnabled(false);

	        final GestureDetector gestureDetector = new GestureDetector(getActivity().getApplicationContext(), new TapGestureDetector());

	        getActivity().runOnUiThread(new Runnable() {
		        @Override
		        public void run() {
			        frameContainerLayout.setClickable(true);
			        frameContainerLayout.setOnTouchListener(new View.OnTouchListener() {

				        private int mLastTouchX;
				        private int mLastTouchY;
				        private int mPosX = 0;
				        private int mPosY = 0;

				        @Override
				        public boolean onTouch(View v, MotionEvent event) {
					        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();


					        boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
					        if (event.getAction() != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
						        if (tapToTakePicture) {
							        takePicture(0, 0);
						        }
						        return true;
					        }
					        else {
						        if (dragEnabled) {
							        int x;
							        int y;

							        switch (event.getAction()) {
								        case MotionEvent.ACTION_DOWN:
											if(mLastTouchX == 0 || mLastTouchY == 0) {
												mLastTouchX = (int)event.getRawX() - layoutParams.leftMargin;
												mLastTouchY = (int)event.getRawY() - layoutParams.topMargin;
											}
									        else{
												mLastTouchX = (int)event.getRawX();
												mLastTouchY = (int)event.getRawY();
											}
									        break;
								        case MotionEvent.ACTION_MOVE:

									        x = (int) event.getRawX();
									        y = (int) event.getRawY();

									        final float dx = x - mLastTouchX;
									        final float dy = y - mLastTouchY;

									        mPosX += dx;
									        mPosY += dy;

									        layoutParams.leftMargin = mPosX;
									        layoutParams.topMargin = mPosY;

									        frameContainerLayout.setLayoutParams(layoutParams);

									        // Remember this touch position for the next move event
									        mLastTouchX = x;
									        mLastTouchY = y;

									        break;
								        default:
									        break;
							        }
						        }
					        }
					        return true;
				        }
			        });
		        }
	        });
        }
    }
	
    private void setDefaultCameraId(){
		
		// Find the total number of cameras available
        numberOfCameras = Camera.getNumberOfCameras();
		
		int camId = defaultCamera.equals("front") ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

		// Find the ID of the default camera
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == camId) {
				defaultCameraId = camId;
				break;
			}
		}
	}
	
    @Override
    public void onResume() {
        super.onResume();
	
	// Sets the Default Camera as the current one (initializes mCamera instance)
       	setCurrentCamera(defaultCameraId);
        
        if(mPreview.mPreviewSize == null){
		mPreview.setCamera(mCamera, cameraCurrentlyLocked);
	} else {
		mPreview.switchCamera(mCamera, cameraCurrentlyLocked);
		mCamera.startPreview();
	}

	    Log.d(TAG, "cameraCurrentlyLocked:" + cameraCurrentlyLocked);

        final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
        ViewTreeObserver viewTreeObserver = frameContainerLayout.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    frameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    frameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage));

                    FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(frameContainerLayout.getWidth(), frameContainerLayout.getHeight());
                    camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
                    frameCamContainerLayout.setLayoutParams(camViewLayout);
                }
            });
        }
    }
    
    // Sets the current camera - allows to set cameraParameters from a single place (e.g. can be used to set AutoFocus and Autoflash)
    private void setCurrentCamera(int cameraId){
    	 mCamera = Camera.open(cameraId);

        if (cameraParameters != null) {
          mCamera.setParameters(cameraParameters);
        }

        //Set AutoFocus Mode if possible
        cameraParameters = mCamera.getParameters();
        if (cameraParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
          cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
          mCamera.setParameters(cameraParameters);
        }

        cameraCurrentlyLocked = cameraId;
    }

    @Override
    public void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
            mPreview.setCamera(null, -1);
            mCamera.release();
            mCamera = null;
        }
    }

    public Camera getCamera() {
      return mCamera;
    }

    public void switchCamera() {
        // check for availability of multiple cameras
        if (numberOfCameras == 1) {
            //There is only one camera available
        }
		Log.d(TAG, "numberOfCameras: " + numberOfCameras);

		// OK, we have multiple cameras.
		// Release this camera -> cameraCurrentlyLocked
		if (mCamera != null) {
			mCamera.stopPreview();
			mPreview.setCamera(null, -1);
			mCamera.release();
			mCamera = null;
		}

		// Acquire the next camera and request Preview to reconfigure
		// parameters.
		int nextCameraId = (cameraCurrentlyLocked + 1) % numberOfCameras;
		
		// Set the next camera as the current one and apply the cameraParameters
		setCurrentCamera(nextCameraId);
		
		mPreview.switchCamera(mCamera, cameraCurrentlyLocked);

	    Log.d(TAG, "cameraCurrentlyLocked new: " + cameraCurrentlyLocked);

		// Start the preview
		mCamera.startPreview();
    }

    public void setCameraParameters(Camera.Parameters params) {
      cameraParameters = params;

      if (mCamera != null && cameraParameters != null) {
        mCamera.setParameters(cameraParameters);
      }
    }

    public Camera.Parameters getCameraParameters() {
      if (mCamera != null) {
        Camera.Parameters params = mCamera.getParameters();
        return params;
      }
      return null;
    }

    public boolean hasFrontCamera(){
        return getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public Bitmap cropBitmap(Bitmap bitmap, Rect rect){
        int w = rect.right - rect.left;
        int h = rect.bottom - rect.top;
        Bitmap ret = Bitmap.createBitmap(w, h, bitmap.getConfig());
        Canvas canvas= new Canvas(ret);
        canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);
        return ret;
    }


    void takePicture(final int maxWidth, final int maxHeight) {
        Log.d(TAG, "takePicture");
        if (mPreview != null) {
            if (!canTakePicture)
                return;

            canTakePicture = false;

            PictureCallback mPicture = new PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {

                	final Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
					//scale down
					Log.e(TAG, "original image size " + (int)(pic.getWidth()) + "x" + (int)(pic.getHeight()));
					final Matrix matrix = new Matrix();
					if (cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_FRONT) {
						Log.d(TAG, "mirror y axis");
						matrix.preScale(-1.0f, 1.0f);
					}
					Log.d(TAG, "preRotate " + mPreview.getDisplayOrientation() + "deg");
					matrix.postRotate(mPreview.getDisplayOrientation());
					// tener en cuenta que la imagen está rotada para cropear
					int marginLR = (17 * pic.getWidth()) / 100;
					int marginT = (5 * pic.getHeight()) / 100;
					Bitmap originalPicture = Bitmap.createBitmap(pic, marginLR, marginT, (int)(pic.getWidth()) -  (int)(1.5 * marginLR), (int)(pic.getHeight()) - (2 * marginT), matrix, false);
					generatePictureFromBitmap(originalPicture);
                    canTakePicture = true;
                    return;
                }
            };
            Log.d(TAG, "calling original takePicture done");
            mCamera.takePicture(null, null, mPicture);
        } else {
            canTakePicture = true;
        }
    }


    private void generatePictureFromBitmap(final Bitmap originalPicture){

	    final FrameLayout cameraLoader = (FrameLayout)view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
	    cameraLoader.setVisibility(View.VISIBLE);
	    final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
	    new Thread() {
		    public void run() {

			    try {
				    final String originalPictureFile = base64JPEG(originalPicture);
            
					eventListener.onPictureTaken(originalPictureFile);

				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
			    catch(Exception e){
				    //An unexpected error occurred while saving the picture.
				    Log.e(TAG, "An error ocurred generating the picture from the Bitmap");
				    eventListener.onPictureTaken(null);
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
		    }
	    }.start();
    }
	
	/*
	public void takePicture(final double maxWidth, final double maxHeight){
		final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
		if(mPreview != null) {
			
			if(!canTakePicture)
				return;
			
			canTakePicture = false;
			Log.e(TAG, "Al llegar a takePicture en activity las dimensiones son " + (int)(maxWidth) + "x" + (int)(maxHeight));

			mPreview.setOneShotPreviewCallback(new Camera.PreviewCallback() {

				@Override
				public void onPreviewFrame(final byte[] data, final Camera camera) {

					new Thread() {
						public void run() {

							//raw picture
							byte[] bytes = mPreview.getFramePicture(data, camera);
							final Bitmap pic = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

							//scale down
							Log.e(TAG, "original image size " + (int)(pic.getWidth()) + "x" + (int)(pic.getHeight()));
							final Matrix matrix = new Matrix();
							if (cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_FRONT) {
								Log.d(TAG, "mirror y axis");
								matrix.preScale(-1.0f, 1.0f);
							}
							Log.d(TAG, "preRotate " + mPreview.getDisplayOrientation() + "deg");
							matrix.postRotate(mPreview.getDisplayOrientation());

							final Rect rect = new Rect(mPreview.mSurfaceView.getLeft(), mPreview.mSurfaceView.getTop(), mPreview.mSurfaceView.getRight(), mPreview.mSurfaceView.getBottom());

							getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									pictureView.layout(rect.left, rect.top, rect.right, rect.bottom);

									Bitmap finalPic = null;
									// If we are going to rotate the picture, width and height are reversed
									boolean swapAspects = mPreview.getDisplayOrientation() % 180 != 0;
									double rotatedWidth = swapAspects ? pic.getHeight() : pic.getWidth();
									double rotatedHeight = swapAspects ? pic.getWidth() : pic.getHeight();
									boolean shouldScaleWidth = maxWidth > 0 && rotatedWidth > maxWidth;
									boolean shouldScaleHeight = maxHeight > 0 && rotatedHeight > maxHeight;

									//scale final picture
									if(shouldScaleWidth || shouldScaleHeight){
										Log.e(TAG, "rotando ");
										double scaleHeight = shouldScaleHeight ? maxHeight / (double)rotatedHeight : 1;
										double scaleWidth = shouldScaleWidth ? maxWidth / (double)rotatedWidth : 1;

										double scale = scaleHeight < scaleWidth ? scaleHeight : scaleWidth;
										finalPic = Bitmap.createScaledBitmap(pic, (int)(pic.getWidth()*scale), (int)(pic.getHeight()*scale), false);
									}
									else{
										finalPic = pic;
									}

									Bitmap originalPicture = Bitmap.createBitmap(finalPic, 0, 0, (int)(finalPic.getWidth()), (int)(finalPic.getHeight()), matrix, false);
									Log.e(TAG, "size original " + (int)(finalPic.getWidth()) + "x" + (int)(finalPic.getHeight()));
								    //get bitmap and compress
								    Bitmap picture = loadBitmapFromView(view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage)));
								    ByteArrayOutputStream stream = new ByteArrayOutputStream();
								    //picture.compress(Bitmap.CompressFormat.PNG, 80, stream);

									generatePictureFromView(originalPicture, picture);
									canTakePicture = true;
								}
							});
						}
					}.start();
				}
			});
		}
		else{
			canTakePicture = true;
		}
	}




    private void generatePictureFromView(final Bitmap originalPicture, final Bitmap picture){

	    final FrameLayout cameraLoader = (FrameLayout)view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
	    cameraLoader.setVisibility(View.VISIBLE);
	    final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
	    new Thread() {
		    public void run() {

			    try {
				    final String picFile = base64JPEG(picture);
				    final String originalPictureFile = base64JPEG(originalPicture);
            
					  eventListener.onPictureTaken(originalPictureFile, picFile);

				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
			    catch(Exception e){
				    //An unexpected error occurred while saving the picture.
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
		    }
	    }.start();
    }
  */

    private String base64PNG(Bitmap image) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      image.compress(Bitmap.CompressFormat.PNG, 100, baos);
      byte[] b = baos.toByteArray();
      String imageEncoded = Base64.encodeToString(b,Base64.DEFAULT);
      return "data:image/png;base64," + imageEncoded;
    }

    private String base64JPEG(Bitmap image) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      image.compress(Bitmap.CompressFormat.JPEG, 90, baos);
      byte[] b = baos.toByteArray();
      String imageEncoded = Base64.encodeToString(b,Base64.DEFAULT);
      return "data:image/jpeg;base64," + imageEncoded;
    }

    private File getOutputMediaFile(String suffix){

	    File mediaStorageDir = getActivity().getApplicationContext().getFilesDir();
	    /*if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED_READ_ONLY) {
		    mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + getActivity().getApplicationContext().getPackageName() + "/Files");
	    }*/
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("dd_MM_yyyy_HHmm_ss").format(new Date());
        File mediaFile;
        String mImageName = "camerapreview_" + timeStamp + suffix + ".jpg";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    private File storeImage(Bitmap image, String suffix) {
        File pictureFile = getOutputMediaFile(suffix);
        if (pictureFile != null) {
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                image.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.close();
                return pictureFile;
            }
            catch (Exception ex) {
            }
        }
        return null;
    }

	public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}
		return inSampleSize;
	}
	
    private Bitmap loadBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap( v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        v.draw(c);
        return b;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}


class Preview extends RelativeLayout implements SurfaceHolder.Callback {
    private final String TAG = "Preview";

    CustomSurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
    Camera mCamera;
    int cameraId;
    int displayOrientation;

    Preview(Context context) {
        super(context);

        mSurfaceView = new CustomSurfaceView(context);
        addView(mSurfaceView);

        requestLayout();

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(Camera camera, int cameraId) {
        mCamera = camera;
        this.cameraId = cameraId;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            setCameraDisplayOrientation();
            //mCamera.getParameters().setRotation(getDisplayOrientation());
            //requestLayout();
        }
    }

    public int getDisplayOrientation() {
    	return displayOrientation;
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info=new Camera.CameraInfo();
        int rotation=
            ((Activity)getContext()).getWindowManager().getDefaultDisplay()
                         .getRotation();
        int degrees=0;
        DisplayMetrics dm=new DisplayMetrics();

        Camera.getCameraInfo(cameraId, info);
        ((Activity)getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees=0;
                break;
            case Surface.ROTATION_90:
                degrees=90;
                break;
            case Surface.ROTATION_180:
                degrees=180;
                break;
            case Surface.ROTATION_270:
                degrees=270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        	displayOrientation=(info.orientation + degrees) % 360;
        	displayOrientation=(360 - displayOrientation) % 360;
        } else {
        	displayOrientation=(info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(TAG, (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back")
        	+ " camera is oriented -" + info.orientation + "deg from natural");
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
        mCamera.setDisplayOrientation(displayOrientation);
    }

    public void switchCamera(Camera camera, int cameraId) {
        setCamera(camera, cameraId);
        try {
            camera.setPreviewDisplay(mHolder);
	        Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
	        camera.setParameters(parameters);
        }
        catch (IOException exception) {
            Log.e(TAG, exception.getMessage());
        }
        //requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            int width = r - l;
            int height = b - t;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                if(displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = mPreviewSize.height;
                    previewHeight = mPreviewSize.width;
                }

	            LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
            }

            int nW;
            int nH;
            int top;
            int left;

            float scale = 1.0f;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally");
                int scaledChildWidth = (int)((previewWidth * height / previewHeight) * scale);
                nW = (width + scaledChildWidth) / 2;
                nH = (int)(height * scale);
                top = 0;
                left = (width - scaledChildWidth) / 2;
            }
            else {
                Log.d(TAG, "center vertically");
                int scaledChildHeight = (int)((previewHeight * width / previewWidth) * scale);
                nW = (int)(width * scale);
                nH = (height + scaledChildHeight) / 2;
                top = (height - scaledChildHeight) / 2;
                left = 0;
            }
            child.layout(left, top, nW, nH);

            Log.d("layout", "left:" + left);
            Log.d("layout", "top:" + top);
            Log.d("layout", "right:" + nW);
            Log.d("layout", "bottom:" + nH);
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mSurfaceView.setWillNotDraw(false);
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        //For Now We get the highest res image.
        //TODO: Change to allow user specified quality, and allow checking of sizes
        if (sizes == null) return null;
        
        Camera.Size optimalSize = sizes.get(0);
        for(Camera.Size s : sizes) {
          if(s.width > optimalSize.width) {
            optimalSize = s;
          }
        }

        Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	    if(mCamera != null) {
		    // Now that the size is known, set up the camera parameters and begin
		    // the preview.
		    Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
		    requestLayout();
		    //mCamera.setDisplayOrientation(90);
		    mCamera.setParameters(parameters);
		    mCamera.startPreview();
	    }
    }

    public byte[] getFramePicture(byte[] data, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        int format = parameters.getPreviewFormat();

        //YUV formats require conversion
        if (format == ImageFormat.NV21 || format == ImageFormat.YUY2 || format == ImageFormat.NV16) {
            int w = parameters.getPreviewSize().width;
            int h = parameters.getPreviewSize().height;

            // Get the YuV image
            YuvImage yuvImage = new YuvImage(data, format, w, h, null);
            // Convert YuV to Jpeg
            Rect rect = new Rect(0, 0, w, h);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(rect, 80, outputStream);
            return outputStream.toByteArray();
        }
        return data;
    }
    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        if(mCamera != null) {
            mCamera.setOneShotPreviewCallback(callback);
        }
    }
}
class TapGestureDetector extends GestureDetector.SimpleOnGestureListener{

	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		return true;
	}
}
class CustomSurfaceView extends SurfaceView implements SurfaceHolder.Callback{
    private final String TAG = "CustomSurfaceView";

    CustomSurfaceView(Context context){
        super(context);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }
}
