package app.shoreline.water;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override public void onCreate(Bundle b) {
    // Keep the launch screen up until the icon's shoreline has finished lighting up,
    // then fade it out so the app's own shoreline can sweep in underneath.
    SplashScreen splash = SplashScreen.installSplashScreen(this);
    long shownAt = SystemClock.uptimeMillis();
    splash.setKeepOnScreenCondition(() -> SystemClock.uptimeMillis() - shownAt < 950);
    splash.setOnExitAnimationListener(view ->
        view.getView().animate().alpha(0f).setDuration(260).withEndAction(view::remove).start());

    registerPlugin(ShorePlugin.class);
    super.onCreate(b);
    // The manifest already says portrait; this keeps it even where a launcher or OS setting overrides it.
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
  }
}
