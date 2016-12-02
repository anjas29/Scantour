/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.me.threemusketeer.scantour.scanar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;

import com.vuforia.CameraDevice;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.TargetFinder;
import com.vuforia.TargetSearchResult;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.me.threemusketeer.scantour.vuforia.SampleApplicationControl;
import com.me.threemusketeer.scantour.vuforia.SampleApplicationException;
import com.me.threemusketeer.scantour.vuforia.SampleApplicationSession;
import com.me.threemusketeer.scantour.vuforia.utils.LoadingDialogHandler;
import com.me.threemusketeer.scantour.vuforia.utils.SampleApplicationGLView;
import com.me.threemusketeer.scantour.vuforia.utils.Texture;
import com.me.threemusketeer.scantour.R;

import com.me.threemusketeer.scantour.vuforia.ui.SampleAppMenu.SampleAppMenu;
import com.me.threemusketeer.scantour.vuforia.ui.SampleAppMenu.SampleAppMenuGroup;
import com.me.threemusketeer.scantour.vuforia.ui.SampleAppMenu.SampleAppMenuInterface;
import com.me.threemusketeer.scantour.scanar.VideoPlayerHelper.MEDIA_STATE;

import java.util.Vector;


// The main activity for the CloudReco sample. 
public class CloudReco extends Activity implements SampleApplicationControl,
    SampleAppMenuInterface
{
    private static final String LOGTAG = "CloudReco";

    //ebm
    public static final int NUM_TARGETS = 4;
    public static final int STONES = 0;
    public static final int CHIPS = 1;

    private boolean mPlayFullscreenVideo = false;

    private int mSeekPosition[] = null;
    private boolean mWasPlaying[] = null;

    private VideoPlayerHelper mVideoPlayerHelper[] = null;
    private GestureDetector.SimpleOnGestureListener mSimpleListener = null;

    public String [] mMovieName = null;
    
    SampleApplicationSession vuforiaAppSession;
    
    // These codes match the ones defined in TargetFinder in Vuforia.jar
    static final int INIT_SUCCESS = 2;
    static final int INIT_ERROR_NO_NETWORK_CONNECTION = -1;
    static final int INIT_ERROR_SERVICE_NOT_AVAILABLE = -2;
    static final int UPDATE_ERROR_AUTHORIZATION_FAILED = -1;
    static final int UPDATE_ERROR_PROJECT_SUSPENDED = -2;
    static final int UPDATE_ERROR_NO_NETWORK_CONNECTION = -3;
    static final int UPDATE_ERROR_SERVICE_NOT_AVAILABLE = -4;
    static final int UPDATE_ERROR_BAD_FRAME_QUALITY = -5;
    static final int UPDATE_ERROR_UPDATE_SDK = -6;
    static final int UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE = -7;
    static final int UPDATE_ERROR_REQUEST_TIMEOUT = -8;
    
    static final int HIDE_LOADING_DIALOG = 0;
    static final int SHOW_LOADING_DIALOG = 1;
    
    // Our OpenGL view:
    private SampleApplicationGLView mGlView;
    
    // Our renderer:
    private VideoPlaybackRenderer mRenderer;
    
    private SampleAppMenu mSampleAppMenu;
    
    private boolean mExtendedTracking = false;
    boolean mFinderStarted = false;
    boolean mStopFinderIfStarted = false;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private static final String kAccessKey = "526e116e40199fad6505bc50eea7628644b74864";
    private static final String kSecretKey = "4c70d9f57d9c93f32260b3892775c97433ccd474";

    
    // View overlays to be displayed in the Augmented View
    private RelativeLayout mUILayout;
    
    // Error message handling:
    private int mlastErrorCode = 0;
    private int mInitErrorCode = 0;
    private boolean mFinishActivityOnError;
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private GestureDetector mGestureDetector;
    
    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
        this);

    // declare scan line and its animation
    private View scanLine;
    private TranslateAnimation scanAnimation;
    
    private double mLastErrorTime;
    
    boolean mIsDroidDevice = false;
    
    
    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Creates the GestureDetector listener for processing double tap
        //mGestureDetector = new GestureDetector(this, new GestureListener());
        
        mTextures = new Vector<Texture>();

        loadTextures();

        //ebm

        mSeekPosition = new int[NUM_TARGETS];
        mWasPlaying = new boolean[NUM_TARGETS];
        mMovieName = new String[NUM_TARGETS];

        mSimpleListener = new GestureDetector.SimpleOnGestureListener();
        mGestureDetector = new GestureDetector(getApplicationContext(),
                mSimpleListener);

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener()
        {
            public boolean onDoubleTap(MotionEvent e)
            {
                // We do not react to this event
                return false;
            }


            public boolean onDoubleTapEvent(MotionEvent e)
            {
                // We do not react to this event
                return false;
            }


            // Handle the single tap
            public boolean onSingleTapConfirmed(MotionEvent e)
            {
                boolean isSingleTapHandled = false;
                // Do not react if the StartupScreen is being displayed
                for (int i = 0; i < NUM_TARGETS; i++)
                {
                    // Verify that the tap happened inside the target
                    if (mRenderer!= null && mRenderer.isTapOnScreenInsideTarget(i, e.getX(),
                            e.getY()))
                    {
                        // Check if it is playable on texture
                        if (mVideoPlayerHelper[i].isPlayableOnTexture())
                        {
                            // We can play only if the movie was paused, ready
                            // or stopped
                            if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PAUSED)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.READY)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.STOPPED)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END))
                            {
                                // Pause all other media
                                pauseAll(i);

                                // If it has reached the end then rewind
                                if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END))
                                    mSeekPosition[i] = 0;

                                mVideoPlayerHelper[i].play(mPlayFullscreenVideo,
                                        mSeekPosition[i]);
                                mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                            } else if (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING)
                            {
                                // If it is playing then we pause it
                                mVideoPlayerHelper[i].pause();
                            }
                        } else if (mVideoPlayerHelper[i].isPlayableFullscreen())
                        {
                            // If it isn't playable on texture
                            // Either because it wasn't requested or because it
                            // isn't supported then request playback fullscreen.
                            mVideoPlayerHelper[i].play(true,
                                    VideoPlayerHelper.CURRENT_POSITION);
                        }

                        isSingleTapHandled = true;

                        // Even though multiple videos can be loaded only one
                        // can be playing at any point in time. This break
                        // prevents that, say, overlapping videos trigger
                        // simultaneously playback.
                        break;
                    }
                }

                return isSingleTapHandled;
            }
        });

        mMovieName = new String[4];
        mMovieName[STONES] = "VideoPlayback/sample 1.mp4";
        mMovieName[CHIPS] = "VideoPlayback/sample 2.mp4";
        mMovieName[2] = "VideoPlayback/sample 3.mp4";
        mMovieName[3] = "VideoPlayback/sample 4.mp4";

        mVideoPlayerHelper = new VideoPlayerHelper[NUM_TARGETS];

        for (int i = 0; i < NUM_TARGETS; i++)
        {
            mVideoPlayerHelper[i] = new VideoPlayerHelper();
            mVideoPlayerHelper[i].init();
            mVideoPlayerHelper[i].setActivity(this);
        }
        
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");
        
    }
    
    // Process Single Tap event to trigger autofocus
    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();
        
        
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    boolean result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
                    
                    if (!result)
                        Log.e("SingleTapUp", "Unable to trigger focus");
                }
            }, 1000L);
            
            return true;
        }
    }
    
    
    // We want to load specific textures from the APK, which we will later use
    // for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk(
                "VideoPlayback/ScantourSizzleReel.jpg", getAssets()));
        mTextures.add(Texture.loadTextureFromApk(
                "VideoPlayback/ScantourSizzleReel.jpg", getAssets()));
        mTextures.add(Texture.loadTextureFromApk(
                "VideoPlayback/ScantourSizzleReel.jpg", getAssets()));
        mTextures.add(Texture.loadTextureFromApk(
                "VideoPlayback/ScantourSizzleReel.jpg", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/play.png",
                getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/busy.png",
                getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/error.png",
                getAssets()));
    }
    
    
    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        
        try
        {
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
        
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
    }
    
    
    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        // Store the playback state of the movies and unload them:
        for (int i = 0; i < NUM_TARGETS; i++)
        {
            // If the activity is paused we need to store the position in which
            // this was currently playing:
            if (mVideoPlayerHelper[i].isPlayableOnTexture())
            {
                mSeekPosition[i] = mVideoPlayerHelper[i].getCurrentPosition();
                mWasPlaying[i] = mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING ? true
                        : false;
            }

            // We also need to release the resources used by the helper, though
            // we don't need to destroy it:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].unload();
        };

        try
        {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
    }
    
    
    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        for (int i = 0; i < NUM_TARGETS; i++)
        {
            // If the activity is destroyed we need to release all resources:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].deinit();
            mVideoPlayerHelper[i] = null;
        }

        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }
    
    
    public void deinitCloudReco()
    {
        // Get the object tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            Log.e(LOGTAG,
                "Failed to destroy the tracking data set because the ObjectTracker has not"
                    + " been initialized.");
            return;
        }
        
        // Deinitialize target finder:
        TargetFinder finder = objectTracker.getTargetFinder();
        finder.deinit();
    }

    public void onBackPressed()
    {
        pauseAll(-1);
        super.onBackPressed();
    }


    private void startLoadingAnimation()
    {
        // Inflates the Overlay Layout to be displayed above the Camera View
        LayoutInflater inflater = LayoutInflater.from(this);
        mUILayout = (RelativeLayout) inflater.inflate(R.layout.camera_overlay_with_scanline,
            null, false);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);
        
        // By default
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        loadingDialogHandler.mLoadingDialogContainer
            .setVisibility(View.VISIBLE);

        scanLine = mUILayout.findViewById(R.id.scan_line);
        scanLine.setVisibility(View.GONE);
        scanAnimation = new TranslateAnimation(
            TranslateAnimation.ABSOLUTE, 0f,
            TranslateAnimation.ABSOLUTE, 0f,
            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
            TranslateAnimation.RELATIVE_TO_PARENT, 1.0f);
        scanAnimation.setDuration(4000);
        scanAnimation.setRepeatCount(-1);
        scanAnimation.setRepeatMode(Animation.REVERSE);
        scanAnimation.setInterpolator(new LinearInterpolator());

        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        
    }
    
    
    // Initializes AR application components.
    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        // Initialize the GLView with proper flags
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        
        // Setups the Renderer of the GLView
        mRenderer = new VideoPlaybackRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);

        for (int i = 0; i < NUM_TARGETS; i++)
        {
            mRenderer.setVideoPlayerHelper(i, mVideoPlayerHelper[i]);
            mRenderer.requestLoad(i, mMovieName[i], 0, false);
        }

        mGlView.setRenderer(mRenderer);

        for (int i = 0; i < NUM_TARGETS; i++)
        {
            float[] temp = { 0f, 0f, 0f };
            mRenderer.targetPositiveDimensions[i].setData(temp);
            mRenderer.videoPlaybackTextureID[i] = -1;
        }
        
    }
    
    
    // Returns the error message for each error code
    private String getStatusDescString(int code)
    {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_DESC);
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_DESC);
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_DESC);
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_DESC);
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_DESC);
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_DESC);
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_DESC);
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_DESC);
        else
        {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_DESC);
        }
    }
    
    
    // Returns the error message for each error code
    private String getStatusTitleString(int code)
    {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_TITLE);
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_TITLE);
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_TITLE);
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_TITLE);
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_TITLE);
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_TITLE);
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_TITLE);
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_TITLE);
        else
        {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_TITLE);
        }
    }
    
    
    // Shows error messages as System dialogs
    public void showErrorMessage(int errorCode, double errorTime, boolean finishActivityOnError)
    {
        if (errorTime < (mLastErrorTime + 5.0) || errorCode == mlastErrorCode)
            return;
        
        mlastErrorCode = errorCode;
        mFinishActivityOnError = finishActivityOnError;
        
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    CloudReco.this);
                builder
                    .setMessage(
                        getStatusDescString(CloudReco.this.mlastErrorCode))
                    .setTitle(
                        getStatusTitleString(CloudReco.this.mlastErrorCode))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                if(mFinishActivityOnError)
                                {
                                    finish();
                                }
                                else
                                {
                                    dialog.dismiss();
                                }
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    
    
    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    CloudReco.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    
    
    public void startFinderIfStopped()
    {
        if(!mFinderStarted)
        {
            mFinderStarted = true;
            
            // Get the object tracker:
            TrackerManager trackerManager = TrackerManager.getInstance();
            ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
            
            // Initialize target finder:
            TargetFinder targetFinder = objectTracker.getTargetFinder();
            
            targetFinder.clearTrackables();
            targetFinder.startRecognition();
            scanlineStart();
            scanlineStop();
        }
    }
    
    
    public void stopFinderIfStarted()
    {
        if(mFinderStarted)
        {
            mFinderStarted = false;
            
            // Get the object tracker:
            TrackerManager trackerManager = TrackerManager.getInstance();
            ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
            
            // Initialize target finder:
            TargetFinder targetFinder = objectTracker.getTargetFinder();
            
            targetFinder.stop();
            scanlineStop();
        }
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
            return true;
        
        return mGestureDetector.onTouchEvent(event);
    }
    
    
    @Override
    public boolean doLoadTrackersData()
    {
        Log.d(LOGTAG, "initCloudReco");
        
        // Get the object tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        // Initialize target finder:
        TargetFinder targetFinder = objectTracker.getTargetFinder();
        
        // Start initialization:
        if (targetFinder.startInit(kAccessKey, kSecretKey))
        {
            targetFinder.waitUntilInitFinished();
        }
        
        int resultCode = targetFinder.getInitState();
        if (resultCode != TargetFinder.INIT_SUCCESS)
        {
            if(resultCode == TargetFinder.INIT_ERROR_NO_NETWORK_CONNECTION)
            {
                mInitErrorCode = UPDATE_ERROR_NO_NETWORK_CONNECTION;
            }
            else
            {
                mInitErrorCode = UPDATE_ERROR_SERVICE_NOT_AVAILABLE;
            }
                
            Log.e(LOGTAG, "Failed to initialize target finder.");
            return false;
        }
        
        return true;
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        return true;
    }
    
    
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        
        if (exception == null)
        {
            initApplicationAR();
            mRenderer.mIsActive = true;
            
            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Start the camera:
            try
            {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e)
            {
                Log.e(LOGTAG, e.getString());
            }
            
            boolean result = CameraDevice.getInstance().setFocusMode(
                CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);
            
            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus");
            
            mUILayout.bringToFront();
            
            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG);
            
            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            mSampleAppMenu = new SampleAppMenu(this, this, "Cloud Reco",
                mGlView, mUILayout, null);
            setSampleAppMenuSettings();
            
        } else
        {
            Log.e(LOGTAG, exception.getString());
            if(mInitErrorCode != 0)
            {
                showErrorMessage(mInitErrorCode,10, true);
            }
            else
            {
                showInitializationErrorMessage(exception.getString());
            }
        }
    }
    
    
    @Override
    public void onVuforiaUpdate(State state)
    {
        // Get the tracker manager:
        TrackerManager trackerManager = TrackerManager.getInstance();
        
        // Get the object tracker:
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        // Get the target finder:
        TargetFinder finder = objectTracker.getTargetFinder();
        
        // Check if there are new results available:
        final int statusCode = finder.updateSearchResults();
        
        // Show a message if we encountered an error:
        if (statusCode < 0)
        {
            
            boolean closeAppAfterError = (
                statusCode == UPDATE_ERROR_NO_NETWORK_CONNECTION ||
                statusCode == UPDATE_ERROR_SERVICE_NOT_AVAILABLE);
            
            showErrorMessage(statusCode, state.getFrame().getTimeStamp(), closeAppAfterError);
            
        } else if (statusCode == TargetFinder.UPDATE_RESULTS_AVAILABLE)
        {
            // Process new search results
            if (finder.getResultCount() > 0)
            {
                TargetSearchResult result = finder.getResult(0);
                
                // Check if this target is suitable for tracking:
                if (result.getTrackingRating() > 0)
                {
                    Trackable trackable = finder.enableTracking(result);
                    
                    if (mExtendedTracking)
                        trackable.startExtendedTracking();
                }
            }
        } 
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        
        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;
        
        // Start the tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        objectTracker.start();
        
        // Start cloud based recognition if we are in scanning mode:
        TargetFinder targetFinder = objectTracker.getTargetFinder();
        targetFinder.startRecognition();
        scanlineStart();
        scanlineStop();
        mFinderStarted = true;
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;
        
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if(objectTracker != null)
        {
            objectTracker.stop();
            
            // Stop cloud based recognition:
            TargetFinder targetFinder = objectTracker.getTargetFinder();
            targetFinder.stop();
            scanlineStop();
            mFinderStarted = false;
            
            // Clears the trackables
            targetFinder.clearTrackables();
        }
        else
        {
            result = false;
        }
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());
        
        return result;
    }
    
    final public static int CMD_BACK = -1;
    final public static int CMD_EXTENDED_TRACKING = 1;
    
    // This method sets the menu's settings
    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);

        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_extended_tracking),
            CMD_EXTENDED_TRACKING, false);
        
        mSampleAppMenu.attachMenu();
    }
    
    
    @Override
    public boolean menuProcess(int command)
    {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;
            
            case CMD_EXTENDED_TRACKING:
                TrackerManager trackerManager = TrackerManager.getInstance();
                ObjectTracker objectTracker = (ObjectTracker) trackerManager
                    .getTracker(ObjectTracker.getClassType());
                
                TargetFinder targetFinder = objectTracker.getTargetFinder();
                
                if (targetFinder.getNumImageTargets() == 0)
                {
                    result = true;
                }
                
                for (int tIdx = 0; tIdx < targetFinder.getNumImageTargets(); tIdx++)
                {
                    Trackable trackable = targetFinder.getImageTarget(tIdx);
                    
                    if (!mExtendedTracking)
                    {
                        if (!trackable.startExtendedTracking())
                        {
                            Log.e(LOGTAG,
                                "Failed to start extended tracking target");
                            result = false;
                        } else
                        {
                            Log.d(LOGTAG,
                                "Successfully started extended tracking target");
                        }
                    } else
                    {
                        if (!trackable.stopExtendedTracking())
                        {
                            Log.e(LOGTAG,
                                "Failed to stop extended tracking target");
                            result = false;
                        } else
                        {
                            Log.d(LOGTAG,
                                "Successfully started extended tracking target");
                        }
                    }
                }
                
                if (result)
                    mExtendedTracking = !mExtendedTracking;
                
                break;
            
        }
        
        return result;
    }

    private void scanlineStart() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanLine.setVisibility(View.VISIBLE);
                scanLine.setAnimation(scanAnimation);
            }
        });
    }

    private void scanlineStop() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanLine.setVisibility(View.GONE);
                scanLine.clearAnimation();
            }
        });
    }

    private void pauseAll(int except)
    {
        // And pause all the playing videos:
        for (int i = 0; i < NUM_TARGETS; i++)
        {
            // We can make one exception to the pause all calls:
            if (i != except)
            {
                // Check if the video is playable on texture
                if (mVideoPlayerHelper[i].isPlayableOnTexture())
                {
                    // If it is playing then we pause it
                    mVideoPlayerHelper[i].pause();
                }
            }
        }
    }

}
