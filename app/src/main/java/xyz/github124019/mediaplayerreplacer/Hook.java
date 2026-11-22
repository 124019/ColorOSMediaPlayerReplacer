package xyz.github124019.mediaplayerreplacer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private static final String VIDEO_PKG = "com.coloros.video";

    private static final String[] HOOK_ACTS = {
            "com.oplus.video.ui.LocalVideoPlayActivity",
            "com.oplus.music.ui.MainActivity"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!VIDEO_PKG.equals(lpparam.packageName)) return;
        XposedBridge.log("LockedAlbumHook: LOADED in " + lpparam.packageName);

        for (String actCls : HOOK_ACTS) {
            try {
                Class<?> c = XposedHelpers.findClass(actCls, lpparam.classLoader);
                XposedBridge.hookAllMethods(c, "onCreate", new RedirectHook("onCreate:" + actCls));
                XposedBridge.hookAllMethods(c, "onNewIntent", new RedirectHook("onNewIntent:" + actCls));
                XposedBridge.log("LockedAlbumHook: hooked " + actCls);
            } catch (Throwable t) {
                XposedBridge.log("LockedAlbumHook: skip " + actCls + " -> " + t);
            }
        }
    }

    static class RedirectHook extends XC_MethodHook {
        final String tag;
        RedirectHook(String tag) { this.tag = tag; }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            Object obj = param.thisObject;
            if (!(obj instanceof Activity)) return;
            Activity act = (Activity) obj;

            Intent src;
            if (tag.startsWith("onNewIntent") && param.args.length > 0 && param.args[0] instanceof Intent) {
                src = (Intent) param.args[0];
            } else {
                src = act.getIntent();
            }
            if (src == null) return;

            handleMediaIntent(act, src, tag);
        }
    }

    private static void handleMediaIntent(Activity act, Intent src, String tag) {
        Uri data = src.getData();
        if (data == null) return;

        String mime = src.getType();
        if (mime == null) {
            try { mime = act.getContentResolver().getType(data); } catch (Throwable ignored) {}
        }
        if (mime == null) mime = guessMimeFromUri(data);

        if (mime == null) {
            XposedBridge.log("LockedAlbumHook: [" + tag + "] mime unknown, skip");
            return;
        }

        String category;
        if (mime.startsWith("video/")) category = "video";
        else if (mime.startsWith("audio/")) category = "audio";
        else {
            XposedBridge.log("LockedAlbumHook: [" + tag + "] mime=" + mime + " not media, skip");
            return;
        }

        Intent probe = new Intent(Intent.ACTION_VIEW);
        probe.setDataAndType(data, mime);

        ResolveInfo ri = null;
        try {
            ri = act.getPackageManager().resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            XposedBridge.log("LockedAlbumHook: [" + tag + "] resolve failed: " + t);
        }

        // 有非 coloros.video 的默认应用 → 直接转发
        if (ri != null && ri.activityInfo != null
                && !"android".equals(ri.activityInfo.packageName)
                && !VIDEO_PKG.equals(ri.activityInfo.packageName)) {
            String defPkg = ri.activityInfo.packageName;
            String defCls = ri.activityInfo.name;
            XposedBridge.log("LockedAlbumHook: [" + tag + "] default " + category + " = " + defPkg + "/" + defCls);

            Intent dst = new Intent(Intent.ACTION_VIEW);
            dst.setComponent(new ComponentName(defPkg, defCls));
            dst.setDataAndType(data, mime);
            dst.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            dst.setClipData(ClipData.newRawUri("", data));

            try {
                act.startActivity(dst);
                XposedBridge.log("LockedAlbumHook: [" + tag + "] " + category
                        + " -> " + defPkg + "/" + defCls + " (default)");
                act.finish();
            } catch (Throwable t) {
                XposedBridge.log("LockedAlbumHook: [" + tag + "] forward failed: " + t);
            }
            return;
        }

        // 没有默认（Resolver / 系统 / coloros.video 自身）→ 弹 chooser 让用户手动选择
        XposedBridge.log("LockedAlbumHook: [" + tag + "] no default for " + category + ", show chooser");
        showChooser(act, probe, data, category, tag);
    }

    private static void showChooser(Activity act, Intent probe, Uri data, String category, String tag) {
        try {
            Intent chooser = Intent.createChooser(probe, "选择" + ("video".equals(category) ? "视频" : "音频") + "播放器");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            chooser.setClipData(ClipData.newRawUri("", data));
            act.startActivity(chooser);
            XposedBridge.log("LockedAlbumHook: [" + tag + "] chooser shown for " + category);
            act.finish();
        } catch (Throwable t) {
            XposedBridge.log("LockedAlbumHook: [" + tag + "] chooser failed: " + t);
        }
    }

    private static String guessMimeFromUri(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null) path = uri.getPath();
        if (path == null) return null;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return null;
        String ext = path.substring(dot + 1).toLowerCase();
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }
}