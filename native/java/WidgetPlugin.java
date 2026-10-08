package app.shoreline.water;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WidgetRefresh")
public class WidgetPlugin extends Plugin {
  @PluginMethod
  public void refresh(PluginCall call) {
    WidgetBase.refresh(getContext(), -1);
    call.resolve();
  }
}
