package com.projectkr.shell.utils;

import com.omarea.common.shell.KernelProrp;

public class CpuLoadUtils {
    private static String lastCpuState = "";
    private static String lastCpuStateSum = "";

    public CpuLoadUtils() {
        lastCpuState = KernelProrp.INSTANCE.getProp("/proc/stat", "^cpu");
        lastCpuStateSum = lastCpuState;
    }

    private long cpuTotalTime(String[] cols) {
        long totalTime = 0;
        for (int i = 1; i < cols.length; i++) {
            totalTime += Long.parseLong(cols[i]);
        }
        return totalTime;
    }

    private long cpuIdelTime(String[] cols) {
        return Long.parseLong(cols[4]);
    }

    public Double getCpuLoadSum() {
        String times = KernelProrp.INSTANCE.getProp("/proc/stat", "^cpu ");
        if (!times.equals("error") && times.startsWith("cpu")) {
            try {
                if (lastCpuStateSum.isEmpty()) {
                    lastCpuStateSum = times;
                    Thread.sleep(100);
                    return getCpuLoadSum();
                } else {
                    String[] curTick = times.split("\n");
                    String[] prevTick = lastCpuStateSum.split("\n");

                    for (String cpuCurrentTime : curTick) {
                        String[] cols1 = cpuCurrentTime.replaceAll(" {2}", " ").split(" ");
                        if (cols1[0].trim().equals("cpu")) {
                            String[] cols0;
                            // 根据前缀匹配上一个时段的cpu时间数据
                            for (String cpu : prevTick) {
                                // startsWith条件必须加个空格，因为搜索cpu的时候 "cpu0 ..."、"cpu1 ..."等都会匹配
                                if (cpu.startsWith("cpu ")) {
                                    lastCpuStateSum = times;
                                    cols0 = cpu.replaceAll(" {2}", " ").split(" ");
                                    long total1 = cpuTotalTime(cols1);
                                    long idel1 = cpuIdelTime(cols1);
                                    long total0 = cpuTotalTime(cols0);
                                    long idel0 = cpuIdelTime(cols0);
                                    long timePoor = total1 - total0;
                                    // 如果CPU时长是0，那就是离线咯
                                    if (timePoor == 0) {
                                        return 0d;
                                    } else {
                                        long idelTimePoor = idel1 - idel0;
                                        if (idelTimePoor < 1) {
                                            return 100d;
                                        } else {
                                            return (100 - (idelTimePoor * 100.0 / timePoor));
                                        }
                                    }
                                }
                            }
                            return 0d;
                        }
                    }
                }
            } catch (Exception ex) {
            }
        }
        return -1d;
    }
}
