package app.shoreline.water;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override public void onCreate(Bundle b) {
    registerPlugin(ShorePlugin.class);
    super.onCreate(b);
  }
}
