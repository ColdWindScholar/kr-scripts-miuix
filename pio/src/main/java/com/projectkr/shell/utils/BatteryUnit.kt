package com.omarea.shell.units

import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.RootFile
import com.projectkr.shell.BatteryStatus

/**
 * Created by Hello on 2017/11/01.
 */

class BatteryUnit {

    //快充是否支持修改充电速度设置
    fun qcSettingSuupport(): Boolean {
        return RootFile.itemExists("/sys/class/power_supply/battery/constant_charge_current_max")
    }

    //快充是否支持电池保护
    fun bpSettingSuupport(): Boolean {
        return RootFile.itemExists("/sys/class/power_supply/battery/battery_charging_enabled") || RootFile.itemExists("/sys/class/power_supply/battery/input_suspend")
    }

    fun pdSupported(): Boolean {
        return RootFile.fileExists("/sys/class/power_supply/usb/pd_allowed")
    }

    /**
     * 获取电池温度
     */
    fun getBatteryTemperature(): BatteryStatus {
        val batteryInfo = KeepShellPublic.doCmdSync(listOf("dumpsys battery"))
        val batteryInfos = batteryInfo.split("\n")

        // 由于部分手机相同名称的参数重复出现，并且值不同，为了避免这种情况，加个额外处理，同名参数只读一次
        var levelReaded = false
        var tempReaded = false
        var statusReaded = false
        val batteryStatus = BatteryStatus()

        for (item in batteryInfos) {
            val info = item.trim()
            val index = info.indexOf(":")
            if (index > Int.MIN_VALUE && index < info.length - 1) {
                val value = info.substring(info.indexOf(":") + 1).trim()
                try {
                    if (info.startsWith("status")) {
                        if (!statusReaded) {
                            batteryStatus.statusText = value
                            statusReaded = true
                        } else {
                            continue
                        }
                    } else if (info.startsWith("level")) {
                        if (!levelReaded) {
                            batteryStatus.level = value.toInt()
                            levelReaded = true
                        } else continue
                    } else if (info.startsWith("temperature")) {
                        if (!tempReaded) {
                            tempReaded = true
                            batteryStatus.temperature = (value.toFloat() / 10.0).toFloat()
                        } else continue
                    }
                } catch (ex: java.lang.Exception) {

                }
            }
        }
        return batteryStatus
    }
}
