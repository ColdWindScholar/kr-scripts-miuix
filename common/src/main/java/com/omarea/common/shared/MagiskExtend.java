package com.omarea.common.shared;

import com.omarea.common.shell.KeepShellPublic;
import com.omarea.common.shell.RootFile;

import java.util.Collections;

public class MagiskExtend {
    // source /data/adb/util_functions.sh

    public static String MAGISK_PATH = "/sbin/.core/img/scene_systemless/";
    private static String MAGISK_PATH_19 = "/data/adb/modules"; //  "/sbin/.magisk/modules";
    private static String MAGISK_ROOT_PATH1 = "/sbin/.core/img";
    private static String MAGISK_ROOT_PATH2 = "/sbin/.magisk/img";

    private static String MAGISK_MODULE_NAME = "scene_systemless";
    //magisk 19 /data/adb/modules
    private static int supported = -1;
    private static int MagiskVersion = 0;

    /**
     * 是否已经安装magisk并且版本合适
     *
     * @return 是否已安装
     */
    public static boolean magiskSupported() {
        if (supported == -1 || MagiskVersion < 1) {
            String magiskVersion = KeepShellPublic.INSTANCE.doCmdSync(Collections.singletonList("magisk -V"));
            if (!magiskVersion.equals("error")) {
                try {
                    MagiskVersion = Integer.parseInt(magiskVersion) / 1000;
                    supported = MagiskVersion >= 17 ? 1 : 0;

                    if (supported == 1) {
                        if (MagiskVersion >= 19) {
                            MAGISK_PATH = MAGISK_PATH_19 + "/" + MAGISK_MODULE_NAME + "/";
                        } else if (RootFile.INSTANCE.dirExists(MAGISK_ROOT_PATH1)) {
                            MAGISK_PATH = MAGISK_ROOT_PATH1 + "/" + MAGISK_MODULE_NAME + "/";
                        } else if (RootFile.INSTANCE.dirExists(MAGISK_ROOT_PATH2)) {
                            MAGISK_PATH = MAGISK_ROOT_PATH2 + "/" + MAGISK_MODULE_NAME + "/";
                        }
                    }
                } catch (Exception ignored) {
                }
            } else {
                supported = 0;
            }
        }
        return supported == 1;
    }

    /**
     * 是否已安装模块
     *
     * @return
     */
    public static boolean moduleInstalled() {
        return magiskSupported() && RootFile.INSTANCE.fileExists(MAGISK_PATH + "module.prop");
    }

}
