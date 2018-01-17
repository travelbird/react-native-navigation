/*
 * Copyright (c) TravelBird, 2018
 * All rights reserved
 */
package com.facebook.react;

import com.facebook.react.bridge.*;
import com.facebook.react.uimanager.AppRegistry;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Default root view for catalyst apps. Provides the ability to listen for size changes so that a UI
 * manager can re-layout its elements.
 * It delegates handling touch events for itself and child views and sending those events to JS by
 * using JSTouchDispatcher.
 * This view is overriding {@link ViewGroup#onInterceptTouchEvent} method in order to be notified
 * about the events for all of it's children and it's also overriding
 * {@link ViewGroup#requestDisallowInterceptTouchEvent} to make sure that
 * {@link ViewGroup#onInterceptTouchEvent} will get events even when some child view start
 * intercepting it. In case when no child view is interested in handling some particular
 * touch event this view's {@link View#onTouchEvent} will still return true in order to be notified
 * about all subsequent touch events related to that gesture (in case when JS code want to handle
 * that gesture).
 */
public class TBReactRootView extends ReactRootView {

  private @Nullable
  Bundle mAppProperties;

  private @Nullable
  ReactInstanceManager reactInstanceManager;

  public TBReactRootView(Context context) {
    super(context);
  }

  public TBReactRootView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public TBReactRootView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  public void startReactApplication(ReactInstanceManager reactInstanceManager, String moduleName) {
    super.startReactApplication(reactInstanceManager, moduleName);
    this.reactInstanceManager = reactInstanceManager;
  }

  @Override
  public void startReactApplication(ReactInstanceManager reactInstanceManager, String moduleName, @Nullable Bundle initialProperties) {
    super.startReactApplication(reactInstanceManager, moduleName, initialProperties);
    mAppProperties = initialProperties;
    this.reactInstanceManager = reactInstanceManager;
  }

  public @Nullable
  Bundle getAppProperties() {
    return mAppProperties;
  }

  public void setAppProperties(@Nullable Bundle appProperties) {
    UiThreadUtil.assertOnUiThread();
    mAppProperties = appProperties;

    if (reactInstanceManager == null || reactInstanceManager.getCurrentReactContext() == null) {
      return;
    }

    this.runApplication();
  }

  /* package */
  @SuppressWarnings("ConstantConditions")
  void runApplication() {
    ReactContext reactContext = reactInstanceManager.getCurrentReactContext();
    CatalystInstance catalystInstance = reactContext.getCatalystInstance();
    int rootTag = this.getRootViewTag();
    Bundle appProperties = this.getAppProperties();
    WritableMap initialProps = Arguments.fromBundle(appProperties);
    String jsAppModuleName = this.getJSModuleName();

    WritableNativeMap appParams = new WritableNativeMap();
    appParams.putDouble("rootTag", rootTag);
    appParams.putMap("initialProps", initialProps);
    catalystInstance.getJSModule(AppRegistry.class).runApplication(jsAppModuleName, appParams);
  }
}