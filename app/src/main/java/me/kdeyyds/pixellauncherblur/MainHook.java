package me.kdeyyds.pixellauncherblur;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hook(lpparam);
    }

    private void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> quickstepTransitionManagerClass = XposedHelpers.findClass(
                    "com.android.launcher3.QuickstepTransitionManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(quickstepTransitionManagerClass,
                    "getBackgroundAnimator", new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                ObjectAnimator blurAnimator = getBackgroundBlurAnimator(param.thisObject, lpparam);
                                if (blurAnimator != null) {
                                    param.setResult(blurAnimator);
                                    XposedBridge.log("PixelLauncherBlur: Replaced with blur animator");
                                }
                            } catch (Exception e) {
                                XposedBridge.log("PixelLauncherBlur: Error creating blur animator - " + e.getMessage());
                            }
                        }
                    });

            XposedBridge.log("PixelLauncherBlur: Successfully hooked getBackgroundAnimator");

        } catch (Exception e) {
            XposedBridge.log("PixelLauncherBlur: Error - " + e.getMessage());
        }
    }

    private ObjectAnimator getBackgroundBlurAnimator(Object transitionManager, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Object mLauncher = XposedHelpers.getObjectField(transitionManager, "mLauncher");

            // Get state manager and check conditions
            Object stateManager = XposedHelpers.callMethod(mLauncher, "getStateManager");
            Object currentState = XposedHelpers.callMethod(stateManager, "getState");

            // Get OVERVIEW state for comparison
            Class<?> launcherStateClass = XposedHelpers.findClass("com.android.launcher3.LauncherState", lpparam.classLoader);
            Object overviewState = XposedHelpers.getStaticObjectField(launcherStateClass, "OVERVIEW");

            // Always allow blur
            boolean allowBlurringLauncher = !currentState.equals(overviewState);

            // Check if LaunchDepthController exists first
            Object depthController = null;
            Object animationTarget = null;
            float targetValue = 1.0f;
            String propertyName = "value";

            // Try to find the class first before using it
            Class<?> launchDepthControllerClass = null;
            try {
                launchDepthControllerClass = XposedHelpers.findClassIfExists("com.android.launcher3.QuickstepTransitionManager$LaunchDepthController", lpparam.classLoader);
            } catch (Exception e) {
                XposedBridge.log("PixelLauncherBlur: Error finding LaunchDepthController: " + e.getMessage());
                // Class doesn't exist, launchDepthControllerClass will be null
            }

            if (launchDepthControllerClass != null) {
                try {
                    depthController = XposedHelpers.newInstance(launchDepthControllerClass, mLauncher);
                    animationTarget = XposedHelpers.getObjectField(depthController, "stateDepth");
                    Object backgroundAppState = XposedHelpers.getStaticObjectField(launcherStateClass, "BACKGROUND_APP");
                    targetValue = (Float) XposedHelpers.callMethod(backgroundAppState, "getDepth", mLauncher);
                    propertyName = "value";
                    XposedBridge.log("PixelLauncherBlur: Using LaunchDepthController");
                } catch (Exception e) {
                    XposedBridge.log("PixelLauncherBlur: Error creating LaunchDepthController: " + e.getMessage());
                    depthController = null;
                }
            }

            // Fallback if LaunchDepthController doesn't exist or failed
            if (depthController == null) {
                XposedBridge.log("PixelLauncherBlur: LaunchDepthController not available, using fallback");
                View rootView = (View) XposedHelpers.callMethod(mLauncher, "getDragLayer");
                animationTarget = rootView;
                targetValue = 0.95f;
                propertyName = "alpha";
            }

            // Make variables final for inner class access
            final Object finalDepthController = depthController;
            final Object finalAnimationTarget = animationTarget;

            // Create the base animator
            ObjectAnimator backgroundRadiusAnim;
            if (depthController != null) {
                backgroundRadiusAnim = ObjectAnimator.ofFloat(animationTarget, propertyName, targetValue);
            } else {
                // Fallback: animate drag layer alpha
                backgroundRadiusAnim = ObjectAnimator.ofFloat(animationTarget, propertyName, 1.0f, targetValue, 1.0f);
            }

            backgroundRadiusAnim.setDuration(500L);

            if (allowBlurringLauncher) {
                View rootView = (View) XposedHelpers.callMethod(mLauncher, "getDragLayer");

                // Get ViewRootImpl using reflection
                Object viewRootImpl = XposedHelpers.callMethod(rootView, "getViewRootImpl");
                Object parentSurface = viewRootImpl != null ? XposedHelpers.callMethod(viewRootImpl, "getSurfaceControl") : null;

                if (parentSurface != null) {
                    // Create SurfaceControl using reflection
                    Class<?> builderClass = XposedHelpers.findClass("android.view.SurfaceControl$Builder", null);
                    Object builder = XposedHelpers.newInstance(builderClass);

                    XposedHelpers.callMethod(builder, "setName", "Blur Layer");
                    XposedHelpers.callMethod(builder, "setParent", parentSurface);
                    XposedHelpers.callMethod(builder, "setOpaque", false);

                    Object blurLayer = XposedHelpers.callMethod(builder, "build");

                    // Create Transaction using reflection
                    Class<?> transactionClass = XposedHelpers.findClass("android.view.SurfaceControl$Transaction", null);
                    Object transaction = XposedHelpers.newInstance(transactionClass);

                    // Add blur animation with OutExpo easing
                    backgroundRadiusAnim.addUpdateListener(animation -> {
                        try {
                            Object currentStateCheck = XposedHelpers.callMethod(stateManager, "getState");
                            Object allAppsState = XposedHelpers.getStaticObjectField(launcherStateClass, "ALL_APPS");

                            if (currentStateCheck.equals(allAppsState)) {
                                return;
                            }

                            float progress = animation.getAnimatedFraction(); // 0.0 to 1.0

                            // Apply OutExpo easing curve (similar to Qt's QEasingCurve::OutExpo)
                            float easedProgress;
                            if (progress == 1.0f) {
                                easedProgress = 1.0f;
                            } else {
                                easedProgress = 1.0f - (float) Math.pow(2, -10 * progress);
                            }

                            // Interpolate blur radius from 0 to 30
                            float blurRadius = easedProgress * 30.0f;

                            // Apply blur using reflection
                            boolean isValid = (Boolean) XposedHelpers.callMethod(blurLayer, "isValid");
                            if (isValid) {
                                try {
                                    XposedHelpers.callMethod(transaction, "setBackgroundBlurRadius", blurLayer, (int) blurRadius);
                                    XposedHelpers.callMethod(transaction, "setAlpha", blurLayer, 1f);
                                    XposedHelpers.callMethod(transaction, "show", blurLayer);
                                    XposedHelpers.callMethod(transaction, "apply");
                                } catch (Exception e) {
                                    // If blur methods don't exist, just apply the transaction
                                    XposedHelpers.callMethod(transaction, "apply");
                                }
                            }
                        } catch (Exception e) {
                            XposedBridge.log("PixelLauncherBlur: Error in update listener - " + e.getMessage());
                        }
                    });

                    // Set interpolator
                    try {
                        Object mOpeningInterpolator = XposedHelpers.getObjectField(transitionManager, "mOpeningInterpolator");
                        backgroundRadiusAnim.setInterpolator((android.view.animation.Interpolator) mOpeningInterpolator);
                    } catch (Exception e) {
                        // Use default interpolator if not found
                    }

                    // Cleanup on animation end/cancel
                    backgroundRadiusAnim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            cleanupBlurLayer(blurLayer, transaction);
                            cleanupDepthController(finalDepthController, mLauncher, finalAnimationTarget);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            cleanupBlurLayer(blurLayer, transaction);
                            cleanupDepthController(finalDepthController, mLauncher, finalAnimationTarget);
                        }

                        private void cleanupBlurLayer(Object blurLayer, Object transaction) {
                            try {
                                boolean isValid = (Boolean) XposedHelpers.callMethod(blurLayer, "isValid");
                                if (isValid) {
                                    XposedHelpers.callMethod(transaction, "remove", blurLayer);
                                    XposedHelpers.callMethod(transaction, "apply");
                                    XposedHelpers.callMethod(blurLayer, "release");
                                }
                            } catch (Exception e) {
                                XposedBridge.log("PixelLauncherBlur: Error in blur cleanup - " + e.getMessage());
                            }
                        }

                        private void cleanupDepthController(Object depthController, Object mLauncher, Object stateDepth) {
                            if (depthController != null) {
                                try {
                                    Object depthControllerFromLauncher = XposedHelpers.callMethod(mLauncher, "getDepthController");
                                    Object stateDepthFromLauncher = XposedHelpers.getObjectField(depthControllerFromLauncher, "stateDepth");
                                    Object currentValue = XposedHelpers.callMethod(stateDepthFromLauncher, "getValue");
                                    XposedHelpers.callMethod(stateDepth, "setValue", currentValue);
                                    XposedHelpers.callMethod(depthController, "dispose");
                                } catch (Exception e) {
                                    XposedBridge.log("PixelLauncherBlur: Error in depth cleanup - " + e.getMessage());
                                }
                            }
                        }
                    });
                }
            }

            return backgroundRadiusAnim;

        } catch (Exception e) {
            XposedBridge.log("PixelLauncherBlur: Error in getBackgroundBlurAnimator - " + e.getMessage());
            return null;
        }
    }
}