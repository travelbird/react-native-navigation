package com.airbnb.android.react.navigation;

import com.airbnb.android.R;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.devsupport.DoubleTapReloadRecognizer;
import com.facebook.react.modules.core.PermissionListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;

import java.util.Locale;

import static com.airbnb.android.react.navigation.ReactNativeIntents.EXTRA_IS_DISMISS;
import static com.airbnb.android.react.navigation.ReactNativeUtils.maybeEmitEvent;

public class ReactNativeFragment extends Fragment implements ReactInterface,
    ReactNativeFragmentViewGroup.KeyListener {

  static final String EXTRA_REACT_MODULE_NAME = "REACT_MODULE_NAME";

  static final String EXTRA_REACT_PROPS = "REACT_PROPS";

  static final String EXTRA_IS_MODAL = "IS_MODAL";

  static final String EXTRA_SHOW_TOOLBAR = "SHOW_TOOLBAR";

  static final String EXTRA_TOOLBAR_TITLE = "TOOLBAR_TITLE";

  static final String EXTRA_TOOLBAR_PRIMARY_COLOR = "TOOLBAR_PRIMARY_COLOR";

  static final String EXTRA_TOOLBAR_SECONDARY_COLOR = "TOOLBAR_SECONDARY_COLOR";

  static final String EXTRA_RECREATE_REACT_CONTEXT = "recereateNativeContext";

  private static final String TAG = ReactNativeFragment.class.getSimpleName();

  private static final String ON_DISAPPEAR = "onDisappear";

  private static final String ON_APPEAR = "onAppear";

  private static final String INSTANCE_ID_PROP = "nativeNavigationInstanceId";

  private static final String ON_BUTTON_PRESS = "onButtonPress";

  private static final String INITIAL_BAR_HEIGHT_PROP = "nativeNavigationInitialBarHeight";

  private static final int RENDER_TIMEOUT_IN_MS = 1700;

  // An incrementing ID to identify each ReactNativeActivity instance (used in `instanceId`)
  private static int UUID = 1;
  // TODO(lmr): put this back down when done debugging

  private final Runnable timeoutCallback = new Runnable() {
    @Override
    public void run() {
      Log.d(TAG, "render timeout callback called");
      signalFirstRenderComplete();
    }
  };

  //  private ReactInterfaceManager activityManager;
  private final Handler handler = new Handler();

  private DoubleTapReloadRecognizer mDoubleTapReloadRecognizer = new DoubleTapReloadRecognizer();

  private ReactNavigationCoordinator reactNavigationCoordinator = ReactNavigationCoordinator.sharedInstance;

  private ReactInstanceManager reactInstanceManager = reactNavigationCoordinator.getReactInstanceManager();

  private String instanceId;

  private boolean isSharedElementTransition;

  private boolean isWaitingForRenderToFinish = false;

  private ReadableMap initialConfig = ConversionUtil.EMPTY_MAP;

  private ReadableMap previousConfig = ConversionUtil.EMPTY_MAP;

  private ReadableMap renderedConfig = ConversionUtil.EMPTY_MAP;

  private ReactNativeFragmentViewGroup contentContainer;

  private ReactRootView reactRootView;

  private PermissionListener permissionListener;

  private AppCompatActivity activity;

  private View loadingView;

  static ReactNativeFragment newInstance(String moduleName, @Nullable Bundle props) {
    ReactNativeFragment frag = new ReactNativeFragment();
    Bundle args = new BundleBuilder()
        .putString(ReactNativeIntents.EXTRA_MODULE_NAME, moduleName)
        .putBundle(ReactNativeIntents.EXTRA_PROPS, props)
        .toBundle();
    frag.setArguments(args);
    return frag;
  }

  /**
   * Create a ReactNativeFragment instance that loads the specified react native component.
   *
   * @param moduleName
   *     The name of the js module
   * @param props
   *     The initial props
   * @param toolbarTitle
   *     The toolbar title
   * @param toolbarPrimaryColor
   *     The toolbar primary color (background)
   * @param toolbarSecondaryColor
   *     The toolbar secondary color (text and menu items)
   * @param recreateContextOnClose
   *     If true, the react context will be recreated and the javascript props will be cleared.
   */
  static ReactNativeFragment newInstance(
      String moduleName, @Nullable Bundle props,
      String toolbarTitle,
      int toolbarPrimaryColor,
      int toolbarSecondaryColor,
      boolean recreateContextOnClose) {
    ReactNativeFragment frag = new ReactNativeFragment();
    Bundle args = new BundleBuilder()
        .putString(ReactNativeIntents.EXTRA_MODULE_NAME, moduleName)
        .putBoolean(EXTRA_SHOW_TOOLBAR, true)
        .putString(EXTRA_TOOLBAR_TITLE, toolbarTitle)
        .putInt(EXTRA_TOOLBAR_PRIMARY_COLOR, toolbarPrimaryColor)
        .putInt(EXTRA_TOOLBAR_SECONDARY_COLOR, toolbarSecondaryColor)
        .putBundle(ReactNativeIntents.EXTRA_PROPS, props)
        .putBoolean(EXTRA_RECREATE_REACT_CONTEXT, recreateContextOnClose)
        .toBundle();
    frag.setArguments(args);
    return frag;
  }

  static ReactNativeFragment newInstance(
      String moduleName, @Nullable Bundle props,
      String toolbarTitle,
      int toolbarPrimaryColor,
      int toolbarSecondaryColor) {
    return newInstance(moduleName, props, toolbarTitle, toolbarPrimaryColor, toolbarSecondaryColor, false);
  }

  static ReactNativeFragment newInstance(Bundle intentExtras) {
    ReactNativeFragment frag = new ReactNativeFragment();
    frag.setArguments(intentExtras);
    return frag;
  }

  private void initReactNative() {
    if (reactRootView != null || getView() == null) {
      return;
    }
    if (!isSuccessfullyInitialized()) {
      reactInstanceManager.createReactContextInBackground();
      // TODO(lmr): need a different way of doing this
      // TODO(lmr): move to utils
      reactInstanceManager.addReactInstanceEventListener(
          new ReactInstanceManager.ReactInstanceEventListener() {
            @Override
            public void onReactContextInitialized(ReactContext context) {
              reactInstanceManager.removeReactInstanceEventListener(this);
              handler.post(new Runnable() {
                @Override
                public void run() {
                  onAttachWithReactContext();
                }
              });
            }
          });
    } else {
      onAttachWithReactContext();
      // in this case, we end up waiting for the first render to complete
      // doing the transition. If this never happens for some reason, we are going to push
      // anyway in 250ms. The handler should get canceled + called sooner though (it's za race).
      isWaitingForRenderToFinish = true;
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          Log.d(TAG, "render timeout callback called");
          startPostponedEnterTransition();
        }
      }, RENDER_TIMEOUT_IN_MS);
    }
    //    activityManager = new ReactInterfaceManager(this);
    reactNavigationCoordinator.registerComponent(this, instanceId);
  }

  private void onAttachWithReactContext() {
    Log.d(TAG, "onCreateWithReactContext");
    if (getView() == null) {
      return;
    }
    loadingView.setVisibility(View.GONE);

    if (!isSuccessfullyInitialized()) {
      // TODO(lmr): should we make this configurable?
      //      ReactNativeUtils.showAlertBecauseChecksFailed(getActivity(), null);
      return;
    }
    String moduleName = getArguments().getString(ReactNativeIntents.EXTRA_MODULE_NAME);
    Bundle props = getArguments().getBundle(ReactNativeIntents.EXTRA_PROPS);
    if (props == null) {
      props = new Bundle();
    }
    props.putString(INSTANCE_ID_PROP, instanceId);

    if (reactRootView == null) {
      ViewStub reactViewStub = (ViewStub) getView().findViewById(R.id.react_root_view_stub);
      reactRootView = (ReactRootView) reactViewStub.inflate();
    }

    getImplementation().reconcileNavigationProperties(
        this,
        ConversionUtil.EMPTY_MAP,
        renderedConfig,
        true
                                                     );

    reactRootView.startReactApplication(reactInstanceManager, moduleName, props);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    reactInstanceManager.onActivityResult(getActivity(), requestCode, resultCode, data);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (permissionListener != null &&
        permissionListener.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
      permissionListener = null;
    }
  }

  @Override
  public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
    if (!enter) {
      // React Native will flush the UI cache as soon as we unmount it. This will cause the view to
      // disappear unless we delay it until after the fragment animation.
      if (transit == FragmentTransaction.TRANSIT_NONE && nextAnim == 0) {
        reactRootView.unmountReactApplication();
      } else {
        contentContainer.unmountReactApplicationAfterAnimation(reactRootView);
      }
      reactRootView = null;
    }
    if (getActivity() instanceof ScreenCoordinatorComponent) {
      ScreenCoordinator screenCoordinator =
          ((ScreenCoordinatorComponent) getActivity()).getScreenCoordinator();
      if (screenCoordinator != null) {
        // In some cases such as TabConfig, the screen may be loaded before there is a screen
        // coordinator but it doesn't live inside of any back stack and isn't visible.
        return screenCoordinator.onCreateAnimation(transit, enter, nextAnim);
      }
    }
    return null;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (instanceId == null) {
      if (savedInstanceState == null) {
        String moduleName = getArguments().getString(ReactNativeIntents.EXTRA_MODULE_NAME);
        instanceId = String.format(Locale.ENGLISH, "%1s_fragment_%2$d", moduleName, UUID++);
      } else {
        instanceId = savedInstanceState.getString(INSTANCE_ID_PROP);
      }
    }

    setHasOptionsMenu(true);
    Log.d(TAG, "onCreate");
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    postponeEnterTransition();
    View v = inflater.inflate(R.layout.fragment_react_native, container, false);

    if (getArguments().getBoolean(EXTRA_SHOW_TOOLBAR)) {
      final int primary = getArguments().getInt(EXTRA_TOOLBAR_PRIMARY_COLOR, Color.WHITE);
      final int secondary = getArguments().getInt(EXTRA_TOOLBAR_SECONDARY_COLOR, Color.BLACK);
      final Toolbar toolbar = (Toolbar) v.findViewById(R.id.toolbar);
      toolbar.setVisibility(View.VISIBLE);
      v.findViewById(R.id.toolbar_shadow).setVisibility(View.VISIBLE);
      toolbar.setTitle(getArguments().getString(EXTRA_TOOLBAR_TITLE));
      toolbar.setBackgroundColor(primary);
      toolbar.setTitleTextColor(secondary);
      final Drawable backIcon = getResources().getDrawable(R.drawable.n2_ic_arrow_back);
      backIcon.setColorFilter(secondary, PorterDuff.Mode.SRC_IN);
      toolbar.setNavigationIcon(backIcon);
      toolbar.setNavigationOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          activity.onBackPressed();
        }
      });
    }

    // TODO(lmr): should we make the "loading" XML configurable?
    loadingView = v.findViewById(R.id.loading_view);
    contentContainer = (ReactNativeFragmentViewGroup) v.findViewById(R.id.content_container);
    contentContainer.setKeyListener(this);
    activity = (AppCompatActivity) getActivity();

    String moduleName = getArguments().getString(EXTRA_REACT_MODULE_NAME);
    Log.d(TAG, "onCreateView " + moduleName);

    initialConfig = reactNavigationCoordinator.getInitialConfigForModuleName(moduleName);
    // for reconciliation, we save this in "renderedConfig" until the real one comes down
    renderedConfig = initialConfig;

    if (initialConfig.hasKey("screenColor")) {
      int backgroundColor = initialConfig.getInt("screenColor");
      // TODO(lmr): do we need to create a style for this?...
      //        if (backgroundColor == Color.TRANSPARENT) {
      //            // This needs to happen before setContentView gets called
      //            setTheme(R.style.Theme_Airbnb_ReactTranslucent);
      //        }
    }

    return v;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    Log.d(TAG, "onActivityCreated");
    initReactNative();
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume");
    emitEvent(ON_APPEAR, null);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putString(INSTANCE_ID_PROP, instanceId);
  }

  @Override
  public void onPause() {
    super.onPause();
    emitEvent(ON_DISAPPEAR, null);
  }

  @Override
  public void onDestroyView() {
    if (getArguments().getBoolean(EXTRA_RECREATE_REACT_CONTEXT)) {
      reactInstanceManager.recreateReactContextInBackground();
    }
    Log.d(TAG, "onDestroyView");
    super.onDestroyView();
    reactNavigationCoordinator.unregisterComponent(instanceId);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void postponeEnterTransition() {
    super.postponeEnterTransition();
    Log.d(TAG, "postponeEnterTransition");
    getActivity().supportPostponeEnterTransition();
  }

  @Override
  public void startPostponedEnterTransition() {
    super.startPostponedEnterTransition();
    Log.d(TAG, "startPostponeEnterTransition");
    if (getActivity() != null) {
      getActivity().supportStartPostponedEnterTransition();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (/* BuildConfig.DEBUG && */keyCode == KeyEvent.KEYCODE_MENU) {
      // TODO(lmr): disable this in prod
      reactInstanceManager.getDevSupportManager().showDevOptionsDialog();
      return true;
    }
    if (keyCode == 0) { // this is the "backtick"
      // TODO(lmr): disable this in prod
      reactInstanceManager.getDevSupportManager().showDevOptionsDialog();
      return true;
    }
    if (mDoubleTapReloadRecognizer.didDoubleTapR(keyCode, activity.getCurrentFocus())) {
      reactInstanceManager.getDevSupportManager().handleReloadJS();
      return true;
    }
    return false;
  }

  @Override
  public String getInstanceId() {
    return instanceId;
  }

  @Override
  public ReactRootView getReactRootView() {
    return reactRootView;
  }

  @Override
  public boolean isDismissible() {
    return reactNavigationCoordinator.getDismissCloseBehavior(this);
  }

  @Override
  public void signalFirstRenderComplete() {
    Log.d(TAG, "signalFirstRenderComplete");
    startPostponedEnterTransition();
  }

  @Override
  public void notifySharedElementAddition() {
    Log.d(TAG, "notifySharedElementAddition");
    if (isWaitingForRenderToFinish && !ReactNativeUtils.isSharedElementTransition(getActivity())) {
      // if we are receiving a sharedElement and we have postponed the enter transition,
      // we want to cancel any existing handler and create a new one.
      // This is effectively debouncing the call.
      handler.removeCallbacksAndMessages(timeoutCallback);
      handler.post(new Runnable() {
        @Override
        public void run() {
          signalFirstRenderComplete();
        }
      });
    }
  }

  public void emitEvent(String eventName, Object object) {
    if (isSuccessfullyInitialized()) {
      String key =
          String.format(Locale.ENGLISH, "NativeNavigationScreen.%s.%s", eventName, instanceId);
      maybeEmitEvent(reactInstanceManager.getCurrentReactContext(), key, object);
    }
  }

  @Override
  public void receiveNavigationProperties(ReadableMap properties) {
    this.previousConfig = this.renderedConfig;
    this.renderedConfig = ConversionUtil.combine(this.initialConfig, properties);
    reconcileNavigationProperties();
  }

  public void dismiss() {
    Intent intent = new Intent()
        .putExtra(EXTRA_IS_DISMISS, isDismissible());
    getActivity().setResult(Activity.RESULT_OK, intent);
    getActivity().finish();
  }

  private boolean isSuccessfullyInitialized() {
    return reactNavigationCoordinator.isSuccessfullyInitialized();
  }

  private NavigationImplementation getImplementation() {
    return reactNavigationCoordinator.getImplementation();
  }

  private void reconcileNavigationProperties() {
    getImplementation().reconcileNavigationProperties(
        this,
        this.previousConfig,
        this.renderedConfig,
        false);
  }

  @TargetApi(Build.VERSION_CODES.M)
  public void requestPermissions(String[] permissions, int requestCode,
      PermissionListener listener) {
    permissionListener = listener;
    requestPermissions(permissions, requestCode);
  }
}
