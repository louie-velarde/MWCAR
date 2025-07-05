package me.velc.mwcar;

import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Hook implements IXposedHookLoadPackage {

	private static final String APP_PACKAGE_NAME = "com.mfashiongallery.emag";
	private static final String NET_PACKAGE_NAME = APP_PACKAGE_NAME + ".network";

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) {
		if (APP_PACKAGE_NAME.equals(lpparam.packageName)) {
			interceptResponse(".GsonRequest", lpparam.classLoader);
			interceptResponse(".VolleyGsonRequest", lpparam.classLoader);
		}
	}

	static void interceptResponse(String className, ClassLoader classLoader) {
		XposedHelpers.findAndHookMethod(
				NET_PACKAGE_NAME + className,
				classLoader,
				"parseNetworkResponse",
				"com.android.volley.NetworkResponse",
				new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						removeAds(param);
					}
				});
	}

	static void removeAds(MethodHookParam param) throws UnsupportedEncodingException {
		//noinspection unchecked
		var headers = (Map<String, String>) getObjectField(param.args[0], "headers");
		var origData = (byte[]) getObjectField(param.args[0], "data");
		var origCharset = parseCharset(headers);

		var str = new String(origData, origCharset);
		if (TextUtils.isEmpty(str)) return;

		var rootElement = new JsonParser().parse(str);
		if (rootElement == null || !rootElement.isJsonObject()) return;

		var rootObject = rootElement.getAsJsonObject();
		if (!rootObject.has("items")) return;

		var itemsElement = rootObject.get("items");
		if (!itemsElement.isJsonArray()) return;

		if (removeAds(itemsElement.getAsJsonArray())) {
			byte[] data = rootElement.toString().getBytes(origCharset);
			XposedHelpers.setObjectField(param.args[0], "data", data);
		}
	}

	static boolean removeAds(JsonArray array) {
		boolean changed = false;
		for (var iterator = array.iterator(); iterator.hasNext(); ) {
			var itemElement = iterator.next();
			if (!itemElement.isJsonObject()) continue;

			var itemObject = itemElement.getAsJsonObject();
			if (!itemObject.has("item_type")) continue;

			var itemType = itemObject.getAsJsonPrimitive("item_type").getAsString();
			if ("ads".equals(itemType)) {
				iterator.remove();
				changed = true;
			} else if (itemObject.has("adsInfo")) {
				itemObject.remove("adsInfo");
				changed = true;
			}
		}
		return changed;
	}

	static String parseCharset(Map<String, String> headers) {
		if (headers != null) {
			var contentType = headers.get("Content-Type");
			if (contentType != null) {
				var params = contentType.split(";", 0);

				for (int i = 1; i < params.length; ++i) {
					var pair = params[i].trim().split("=", 0);
					if (pair.length == 2 && pair[0].equals("charset")) {
						return pair[1];
					}
				}
			}
		}
		return StandardCharsets.ISO_8859_1.name();
	}
}