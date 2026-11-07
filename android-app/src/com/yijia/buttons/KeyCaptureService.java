package com.yijia.buttons;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

/** Only learned external-device keys are consumed; ordinary keys pass through. */
public class KeyCaptureService extends AccessibilityService {
  public static volatile KeyCaptureService instance;
  private final Map<String, Press> pressed = new HashMap<>();
  private ConfigStore store;

  private static class Press {
    long time;
    String mapping;

    Press(long time, String mapping) {
      this.time = time;
      this.mapping = mapping;
    }
  }

  @Override
  protected void onServiceConnected() {
    store = new ConfigStore(getFilesDir());
    instance = this;
  }

  @Override
  public boolean onKeyEvent(KeyEvent event) {
    if (store == null || !KeyLearning.external(event)) return false;
    String identity = event.getDeviceId() + ":" + event.getKeyCode();
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (pressed.containsKey(identity)) return true;
      int modifiers = KeyLearning.modifiers(event);
      String device = event.getDevice().getDescriptor();
      boolean learning =
          !KeyEvent.isModifierKey(event.getKeyCode())
              && KeyLearning.capture(
                  store,
                  "keyboard",
                  device,
                  event.getDevice().getName(),
                  event.getKeyCode(),
                  modifiers,
                  KeyLearning.label(event.getKeyCode(), modifiers));
      String mapping =
          learning
              ? null
              : ConfigStore.findMapping(
                  store.read(), "keyboard", device, event.getKeyCode(), modifiers);
      if (!learning && mapping == null) return false;
      pressed.put(identity, new Press(event.getEventTime(), mapping));
      return true;
    }
    if (event.getAction() == KeyEvent.ACTION_UP) {
      Press press = pressed.remove(identity);
      if (press == null) return false;
      if (press.mapping != null && !event.isCanceled()) {
        ActionRunner.mapping(
            this, store, press.mapping, Math.max(0, event.getEventTime() - press.time));
      }
      return true;
    }
    return false;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    /* No screen content is read. */
  }

  @Override
  public void onInterrupt() {
    pressed.clear();
  }

  @Override
  public void onDestroy() {
    instance = null;
    pressed.clear();
    super.onDestroy();
  }
}
