package com.airbnb.android.react.navigation;

import android.view.Menu;
import com.facebook.react.bridge.ReadableMap;

interface NavigationImplementation {
  void reconcileNavigationProperties(
      ReactInterface component,
      ReadableMap previous,
      ReadableMap next,
      boolean firstCall
  );

  void makeTabItem(
      ReactBottomNavigation bottomNavigation,
      Menu menu,
      int index,
      Integer itemId,
      ReadableMap config
  );

  void reconcileTabBarProperties(
      ReactBottomNavigation bottomNavigation,
      Menu menu,
      ReadableMap prev,
      ReadableMap next
  );
}
