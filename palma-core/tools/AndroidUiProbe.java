import android.app.UiAutomation;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

// Independent shell UI observer. Avoids the stock dump command's mandatory
// idle wait, which times out on the original application's animated splash.
// Uses Android's installed UI automation wrapper, without an installed helper
// package, injected application code, or changes to application preferences.
public final class AndroidUiProbe {
    static void visit(AccessibilityNodeInfo node, JSONArray nodes, int depth) throws Exception {
        if (node == null || depth > 40 || nodes.length() > 1000) return;
        JSONObject out = new JSONObject();
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        out.put("text", String.valueOf(node.getText() == null ? "" : node.getText()));
        out.put("content-desc", String.valueOf(node.getContentDescription() == null ? "" : node.getContentDescription()));
        out.put("resource-id", node.getViewIdResourceName() == null ? "" : node.getViewIdResourceName());
        out.put("class", String.valueOf(node.getClassName()));
        out.put("package", String.valueOf(node.getPackageName()));
        out.put("clickable", String.valueOf(node.isClickable()));
        out.put("enabled", String.valueOf(node.isEnabled()));
        out.put("bounds", bounds.toShortString());
        if (node.getText() != null || node.getContentDescription() != null || node.isClickable()) nodes.put(out);
        for (int i=0; i<node.getChildCount(); i++) visit(node.getChild(i), nodes, depth+1);
    }
    public static void main(String[] args) {
        Object wrapper = null;
        Class<?> type = null;
        try {
            type = Class.forName("com.android.uiautomator.core.UiAutomationShellWrapper");
            wrapper = type.getConstructor().newInstance();
            type.getMethod("connect").invoke(wrapper);
            Thread.sleep(600);
            UiAutomation ui = (UiAutomation)type.getMethod("getUiAutomation").invoke(wrapper);
            JSONArray nodes = new JSONArray();
            visit(ui.getRootInActiveWindow(), nodes, 0);
            System.out.println(nodes.toString());
        } catch (Exception e) {
            System.out.println("[]");
            System.err.println(e.getClass().getSimpleName());
        } finally {
            try { if(wrapper != null) type.getMethod("disconnect").invoke(wrapper); } catch (Exception ignored) {}
        }
        System.exit(0);
    }
}
