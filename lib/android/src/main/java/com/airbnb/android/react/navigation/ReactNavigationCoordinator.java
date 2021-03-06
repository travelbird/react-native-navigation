package com.airbnb.android.react.navigation;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rodo.Rodo;

public class ReactNavigationCoordinator {
  public static ReactNavigationCoordinator sharedInstance = new ReactNavigationCoordinator();

  private ReactInstanceManager reactInstanceManager;
  private NavigationImplementation navigationImplementation = new DefaultNavigationImplementation();
  private boolean isSuccessfullyInitialized = false;
  private static final int APP_INITIALIZE_TOAST_DELAY = 3000;

  @Nullable ScreenCoordinator screenCoordinator;

  private ReactNavigationCoordinator() {
  }

  public ReactInstanceManager getReactInstanceManager() {
    return reactInstanceManager;
  }

  public void injectReactInstanceManager(final ReactInstanceManager reactInstanceManager) {
    if (this.reactInstanceManager != null) {
      // TODO: throw error. can only initialize once.
    }
    this.reactInstanceManager = reactInstanceManager;
    this.reactInstanceManager.addReactInstanceEventListener(
        new ReactInstanceManager.ReactInstanceEventListener() {
          @Override
          public void onReactContextInitialized(ReactContext context) {
            reactInstanceManager.removeReactInstanceEventListener(this);
            isSuccessfullyInitialized = true;
          }
        });
  }

  public void injectImplementation(NavigationImplementation implementation) {
    if (this.navigationImplementation != null) {
      // TODO: throw error. can only initialize once.
    }
    this.navigationImplementation = implementation;
  }

  public void injectRodo(Rodo rodo) {
    if (this.rodo != null) {
      // TODO: throw error. can only initialize once.
    }
    this.rodo = rodo;
  }

  public NavigationImplementation getImplementation() {
    return this.navigationImplementation;
  }

  boolean isSuccessfullyInitialized() {
    return isSuccessfullyInitialized;
  }

  public void injectExposedActivities(List<ReactExposedActivityParams> exposedActivities) {
    // TODO(lmr): would it make sense to warn or throw here if it's already set?
    this.exposedActivities = exposedActivities;
  }

  /**
   * NOTE(lmr): In the future, we would like to replace this with an annotation parser that
   * generates this map based off of the `ReactExposedActivity` annotations. For now, this should
   * work well enough in the interim.
   */
  private List<ReactExposedActivityParams> exposedActivities;
  private final Map<String /* instance id */, WeakReference<ReactInterface>> componentsMap =
      new HashMap<>();
  private final Map<String /* instance id */, Boolean> dismissCloseBehaviorMap = new HashMap<>();
  private final Map<String /* name */, ReactScreenConfig> screenMap = new HashMap<>();
  private Rodo rodo;

  public void registerComponent(ReactInterface component, String name) {
    componentsMap.put(name, new WeakReference<>(component));
  }

  public void unregisterComponent(String name) {
    componentsMap.remove(name);
  }

  /**
   * Returns an {@link Intent} used for launching an {@link Activity} exposed to React Native flows
   * based on the provided {@code key}. Will pass the provided {@code arguments} as {@link Intent}
   * extras. Activities should have been previously registered via {@code exposedActivities} in the
   * {@link ReactNavigationCoordinator} constructor.
   *
   * @see ReactExposedActivityParams#toIntent(Context, Rodo, ReadableMap)
   */
  @NonNull Intent intentForKey(Context context, String key, ReadableMap arguments) {
    if (exposedActivities == null) {
      throw new IllegalArgumentException("No Activities registered.");
    }

    for (ReactExposedActivityParams exposedActivity : exposedActivities) {
      if (exposedActivity.key().equals(key)) {
        if (rodo == null) {
          throw new IllegalStateException("Rodo not set.");
        }

        return exposedActivity.toIntent(context, rodo, arguments);
      }
    }

    throw new IllegalArgumentException(
        String.format("Tried to push Activity with key '%s', but it could not be found", key));
  }

  ReactAwareActivityFacade activityFromId(String id) {
    WeakReference<ReactInterface> ref = componentsMap.get(id);
    return ref == null ? null : (ReactAwareActivityFacade) ref.get().getActivity();
  }

  ReactInterface componentFromId(String id) {
    WeakReference<ReactInterface> ref = componentsMap.get(id);
    return ref == null ? null : ref.get();
  }

  // If set to true, the Activity will be dismissed when its Toolbar NavigationIcon (home button) is clicked,
  // instead of performing the default behavior (finish)
  public void setDismissCloseBehavior(String id, boolean dismissClose) {
    dismissCloseBehaviorMap.put(id, dismissClose);
  }

  public boolean getDismissCloseBehavior(ReactInterface reactInterface) {
    String id = reactInterface.getInstanceId();
    Boolean dismissClose = dismissCloseBehaviorMap.get(id);
    return dismissClose != null && dismissClose;
  }

  public void registerScreen(String screenName, ReadableMap initialConfig, boolean waitForRender,
      String mode) {
    screenMap.put(screenName, new ReactScreenConfig(
        initialConfig,
        waitForRender,
        ReactScreenMode.fromString(mode)
    ));
  }

  public void start(final Application application) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(application)) {
      handleOverlayPermissionsMissing(application);
      return;
    }
    reactInstanceManager.createReactContextInBackground();
  }

  private static void handleOverlayPermissionsMissing(final Application application) {
    // RN needs "OVERLAY_PERMISSION" in dev mode in order to render the menu and redbox and stuff.
    // In dev we check if we have that permission (if we've made it here, we don't) and send the user
    // to the settings page with a toast indicating why.
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
      @Override
      public void run() {
        // Delaying an arbitrary 3 seconds so that the app can bootstrap, or else this intent doesn't
        // seem to really work.
        if (AndroidVersion.isAtLeastMarshmallow()) {
          Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          application.startActivity(intent);
          Toast.makeText(application,
              "This app must have permissions to draw over other apps in order to run React Native in dev mode",
              Toast.LENGTH_LONG).show();
        }
      }
    }, APP_INITIALIZE_TOAST_DELAY);
  }

  boolean startFragmentForKey(String name, ReadableMap props, ReadableMap options) {
    if (screenCoordinator == null) {
      throw new IllegalStateException("screenCoordinator == null");
    }
    return screenCoordinator.pushNativeScreen(name, ConversionUtil.toBundle(props),
        ConversionUtil.toBundle(options));
  }

  @NonNull private ReactScreenConfig getOrDefault(String screenName) {
    ReactScreenConfig screen = screenMap.get(screenName);
    if (screen == null) {
      screen = ReactScreenConfig.EMPTY;
    }
    return screen;
  }

  ReadableMap getInitialConfigForModuleName(String screenName) {
    return getOrDefault(screenName).initialConfig;
  }

  ReactScreenMode getScreenModeForModuleName(String screenName) {
    return getOrDefault(screenName).mode;
  }

  private static final class ReactScreenConfig {
    final ReadableMap initialConfig;
    final boolean waitForRender;
    final ReactScreenMode mode;

    static final ReactScreenConfig EMPTY =
        new ReactScreenConfig(ConversionUtil.EMPTY_MAP, true, ReactScreenMode.SCREEN);

    ReactScreenConfig(ReadableMap initialConfig, boolean waitForRender, ReactScreenMode mode) {
      this.initialConfig = initialConfig;
      this.waitForRender = waitForRender;
      this.mode = mode;
    }
  }
}
