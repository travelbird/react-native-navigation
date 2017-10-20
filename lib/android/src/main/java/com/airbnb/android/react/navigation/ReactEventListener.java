package com.airbnb.android.react.navigation;

import android.os.Bundle;

/**
 * Represents an event listener, that can react to local events sent via the {@code Navigator}
 * form JS components.
 */
public interface ReactEventListener {
  /**
   * Called when an event was registered.
   *
   * @param eventName The name of the event.
   * @param props Properties sent form JS.
   */
  void onEvent(String eventName, Bundle props);
}
