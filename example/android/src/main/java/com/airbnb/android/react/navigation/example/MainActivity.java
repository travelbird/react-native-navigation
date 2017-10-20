package com.airbnb.android.react.navigation.example;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Toast;
import com.airbnb.android.react.navigation.ReactAwareActivity;
import com.airbnb.android.react.navigation.ReactEventListener;
import com.airbnb.android.react.navigation.ScreenCoordinator;
import com.airbnb.android.react.navigation.ScreenCoordinatorComponent;
import com.airbnb.android.react.navigation.ScreenCoordinatorLayout;

public class MainActivity extends ReactAwareActivity implements ScreenCoordinatorComponent {
  private ScreenCoordinator screenCoordinator;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ScreenCoordinatorLayout container = (ScreenCoordinatorLayout) findViewById(R.id.content);
    screenCoordinator = new ScreenCoordinator(this, container, savedInstanceState);
    screenCoordinator.registerScreen("NativeFragment2", Native2Fragment.FACTORY);

    if (savedInstanceState == null) {
      screenCoordinator.presentScreen(MainFragment.newInstance());
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    screenCoordinator.onResume();
    screenCoordinator.setReactEventListener(new ReactEventListener() {
      @Override public void onEvent(String eventName, Bundle props) {
        toast(String.format("[%s]:%s", eventName, props));
      }
    });
  }

  @Override
  protected void onPause() {
    screenCoordinator.onPause();
    screenCoordinator.setReactEventListener(null);
    super.onPause();
  }

  @Override
  public ScreenCoordinator getScreenCoordinator() {
    return screenCoordinator;
  }

  @Override
  public void onBackPressed() {
    if (!screenCoordinator.onBackPressed()) {
      super.onBackPressed();
    }
  }

  private void toast(String text) {
    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
  }
}
