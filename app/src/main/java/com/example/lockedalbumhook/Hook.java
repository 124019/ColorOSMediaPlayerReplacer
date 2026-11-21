package xyz.github124019.lockedalbumhook;

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

    // === 默认播放器为空时的 fallback 偏好 ===
    private static final String FALLBACK_VIDEO_PKG = "dev.anilbeesetti.nextplayer";
    private static final String FALLBACK_VIDEO_CLS = "dev.anilbeesetti.nextplayer.feature.player.PlayerActivity";
    private static final String FALLBACK_AUDIO_PKG = null;   // 音乐 fallback 自己填
    private static final String FALLBACK_AUDIO_CLS = null;

    // 如果 fallback 也拿不到，是否弹 chooser（false = 直接放行进 coloros.video）
    private static final boolean SHOW_CHOOSER_AS_LAST_RESORT = false;

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
        probe.addCategory(Intent.CATEGORY_DEFAULT);

        ResolveInfo ri = null;
        try {
            ri = act.getPackageManager().resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            XposedBridge.log("LockedAlbumHook: [" + tag + "] resolve failed: " + t);
        }

        String defPkg = null;
        String defCls = null;
        boolean fromDefault = false;

        if (ri != null && ri.activityInfo != null
                && !"android".equals(ri.activityInfo.packageName)
                && !VIDEO_PKG.equals(ri.activityInfo.packageName)) {
            defPkg = ri.activityInfo.packageName;
            defCls = ri.activityInfo.name;
            fromDefault = true;
            XposedBridge.log("LockedAlbumHook: [" + tag + "] default " + category + " = " + defPkg + "/" + defCls);
        } else {
            // 默认为空 / Resolver / coloros.video 自身 → 走 fallback
            String fPkg = "video".equals(category) ? FALLBACK_VIDEO_PKG : FALLBACK_AUDIO_PKG;
            String fCls = "video".equals(category) ? FALLBACK_VIDEO_CLS : FALLBACK_AUDIO_CLS;

            if (fPkg != null && isPackageInstalled(act, fPkg)) {
                defPkg = fPkg;
                defCls = fCls;
                XposedBridge.log("LockedAlbumHook: [" + tag + "] no default, use fallback " + fPkg);
            } else {
                XposedBridge.log("LockedAlbumHook: [" + tag + "] no default & no fallback for " + category);
                if (SHOW_CHOOSER_AS_LAST_RESORT) {
                    showChooser(act, probe, data, mime, category, tag);
                } else {
                    XposedBridge.log("LockedAlbumHook: [" + tag + "] let coloros.video play it");
                }
                return;
            }
        }

        Intent dst = new Intent(Intent.ACTION_VIEW);
        dst.setComponent(new ComponentName(defPkg, defCls));
        dst.setDataAndType(data, mime);
        dst.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        dst.setClipData(ClipData.newRawUri("", data));

        try {
            act.startActivity(dst);
            XposedBridge.log("LockedAlbumHook: [" + tag + "] " + category
                    + " -> " + defPkg + "/" + defCls + (fromDefault ? " (default)" : " (fallback)"));
            act.finish();
        } catch (Throwable t) {
            XposedBridge.log("LockedAlbumHook: [" + tag + "] forward failed: " + t);
        }
    }

    private static void showChooser(Activity act, Intent probe, Uri data, String mime, String category, String tag) {
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

    private static boolean isPackageInstalled(Activity act, String pkg) {
        try {
            act.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
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