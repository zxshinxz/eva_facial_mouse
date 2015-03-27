package com.crea_si.eviacam.service;

import org.opencv.core.Mat;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PointF;

public class EViacamEngine implements FrameProcessor {

    // root overlay view
    private OverlayView mOverlayView;
    
    // layer for drawing the pointer and the dwell click feedback
    PointerLayerView mPointerLayer;
    
    // object in charge of capturing & processing frames
    private CameraListener mCameraListener;
    
    // object which provides the logic for the pointer motion and actions 
    private PointerControl mPointerControl;
    
    // dwell clicking function
    private DwellClick mDwellClick;
    
    // perform actions on the UI using the accessibility API
    AccessibilityAction mAccessibilityAction;
    
    // object which encapsulates rotation and orientation logic
    OrientationManager mOrientationManager;
        
    boolean mRunning= false;

    public EViacamEngine(Context c) {
        /*
         * UI stuff 
         */

        // create overlay root layer
        mOverlayView= new OverlayView(c);
        
        CameraLayerView cameraLayer= new CameraLayerView(c);
        mOverlayView.addFullScreenLayer(cameraLayer);

        ControlsLayerView controlsLayer= new ControlsLayerView(c);
        mOverlayView.addFullScreenLayer(controlsLayer);
        
        // pointer layer (should be the last one)
        mPointerLayer= new PointerLayerView(c);
        mOverlayView.addFullScreenLayer(mPointerLayer);
        
        /*
         * control stuff
         */
        
        mPointerControl= new PointerControl(c, mPointerLayer);
        
        mDwellClick= new DwellClick(c);
        
        mAccessibilityAction= new AccessibilityAction (controlsLayer);
        
        // create camera & machine vision part
        mCameraListener= new CameraListener(c, this);
        cameraLayer.addCameraSurface(mCameraListener.getCameraSurface());
        
        mOrientationManager= new OrientationManager(c, mCameraListener.getCameraOrientation());
        
        /*
         * start processing frames
         */
        mCameraListener.startCamera();

        mRunning= true;
    }
   
    public void cleanup() {
        if (!mRunning) return;
               
        mCameraListener.stopCamera();
        mCameraListener= null;
        
        mOrientationManager.cleanup();
        mOrientationManager= null;

        mDwellClick.cleanup();
        mDwellClick= null;
        
        mPointerControl.cleanup();
        mPointerControl= null;
        
        mOverlayView.cleanup();
        mOverlayView= null;

        mRunning= false;
    }
   
    public void onConfigurationChanged(Configuration newConfig) {
        if (mOrientationManager != null) mOrientationManager.onConfigurationChanged(newConfig);
    }

    /*
     * process incoming camera frame 
     * 
     * this method is called from a secondary thread 
     */
    @Override
    public void processFrame(Mat rgba) {
        int phyRotation = mOrientationManager.getPictureRotation();
        
        // call jni part to track face
        PointF motion = new PointF(0, 0);
        VisionPipeline.processFrame(rgba.getNativeObjAddr(), phyRotation, motion);
        
        // compensate mirror effect
        motion.x= -motion.x;
        
        // fix motion orientation according to device rotation and screen orientation 
        mOrientationManager.fixVectorOrientation(motion);
             
        // update pointer location given face motion
        mPointerControl.updateMotion(motion);
        
        // get new pointer location
        PointF pointerLocation= mPointerControl.getPointerLocation();
        
        // dwell clicking update
        boolean clickGenerated= 
                mDwellClick.updatePointerLocation(pointerLocation);
        
        // redraw pointer layer
        mPointerLayer.postInvalidate();
        
        // perform action when needed
        if (clickGenerated) { 
            mAccessibilityAction.performAction(pointerLocation);
        }
    }
}
