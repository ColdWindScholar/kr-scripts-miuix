package com.projectkr.shell.utils;

import static android.util.Log.println;

import android.annotation.SuppressLint;

import com.omarea.common.shell.KeepShellPublic;
import com.omarea.common.shell.KernelProrp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;

public class CpuFrequencyUtils {
    private static String lastCpuState = "";

    public static String getCurrentMaxFrequency(String core) {
        return KernelProrp.INSTANCE.getProp(Constants.scaling_max_freq.replace("cpu0", core));
    }

    public static String getCurrentFrequency(String core) {
        return KernelProrp.INSTANCE.getProp(Constants.scaling_cur_freq.replace("cpu0", core));
    }

    public static int getCurrentFrequency() {
        File freqs_file = new File("/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq");
        try{
        FileInputStream inputStream = new FileInputStream(freqs_file);
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            int max = 0;
            String line;
            while ( (line = br.readLine())!=null ){
            if (!line.equals("error")) {
                String[] freqArr = line.split("\n");
                for (String aFreqArr : freqArr) {
                    try {
                        int freq = Integer.parseInt(aFreqArr);
                        if (freq > max) {
                            max = freq;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }}
            return max;
        }  catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static String getCurrentMinFrequency(String core) {
        return KernelProrp.INSTANCE.getProp(Constants.scaling_min_freq.replace("cpu0", core));
    }

    public static int getCoreCount() {
        int cores = 0;
        while (true) {
            File file = new File(Constants.cpu_dir.replace("cpu0", "cpu" + cores));
            if (file.exists()) {
                cores++;
            } else {
                return cores;
            }
        }
    }


    private static int getCpuIndex(String[] cols) {
        int cpuIndex;
        if (cols[0].equals("cpu")) {
            cpuIndex = -1;
        } else {
            cpuIndex = Integer.parseInt(cols[0].substring(3));
        }
        return cpuIndex;
    }

    private static long cpuTotalTime(String[] cols) {
        long totalTime = 0;
        for (int i = 1; i < cols.length; i++) {
            totalTime += Long.parseLong(cols[i]);
        }
        return totalTime;
    }

    private static long cpuIdelTime(String[] cols) {
        return Long.parseLong(cols[4]);
    }

    public static HashMap<Integer, Double> getCpuLoad() {
        @SuppressLint("UseSparseArrays") HashMap<Integer, Double> loads = new HashMap<>();
        String times = KernelProrp.INSTANCE.getProp("/proc/stat", "^cpu");
        if (!times.equals("error") && times.startsWith("cpu")) {
            try {
                if (lastCpuState.isEmpty()) {
                    lastCpuState = times;
                    Thread.sleep(100);
                    return getCpuLoad();
                } else {
                    String[] cpus = times.split("\n");
                    String[] cpus0 = lastCpuState.split("\n");
                    // region 这个不能这么写，因为有时候会有CPU离线的情况...导致取不到数据
                    /*
                    if (cpus.length != cpus0.length) {
                        return loads;
                    }
                    // 由于核心的离线或上线，导致两个时间段内，核心出现的顺序并不一定一致...所以，不能用顺序来读取行
                    for (int rowIndex=0; rowIndex < cpus.length; rowIndex++) {
                        String[] cols1 = cpus[rowIndex].replaceAll("  ", " ").split(" ");
                        String[] cols0 = cpus0[rowIndex].replaceAll("  ", " ").split(" ");
                        long total1 = cpuTotalTime(cols1);
                        long idel1 = cpuIdelTime(cols1);
                        long total0 = cpuTotalTime(cols0);
                        long idel0 = cpuIdelTime(cols0);
                        long timePoor = total1 - total0;
                        long idelTimePoor = idel1 - idel0;
                        if (idelTimePoor < 1) {
                            loads.put(getCpuIndex(cols1), 100d);
                        } else {
                            double load = (100 - (idelTimePoor * 100.0 / timePoor));
                            loads.put(getCpuIndex(cols1), load);
                        }
                    }
                    lastCpuState = times;
                    return loads;
                    */
                    // endregion

                    for (String cpuCurrentTime : cpus) {
                        String[] cols1 = cpuCurrentTime.replaceAll(" {2}", " ").split(" ");
                        String[] cols0 = null;
                        // 根据前缀匹配上一个时段的cpu时间数据
                        for (String cpu : cpus0) {
                            // startsWith条件必须加个空格，因为搜索cpu的时候 "cpu0 ..."、"cpu1 ..."等都会匹配
                            if (cpu.startsWith(cols1[0] + " ")) {
                                cols0 = cpu.replaceAll(" {2}", " ").split(" ");
                                break;
                            }
                        }
                        if (cols0 != null && cols0.length != 0) {
                            long total1 = cpuTotalTime(cols1);
                            long idel1 = cpuIdelTime(cols1);
                            long total0 = cpuTotalTime(cols0);
                            long idel0 = cpuIdelTime(cols0);
                            long timePoor = total1 - total0;
                            // 如果CPU时长是0，那就是离线咯
                            if (timePoor == 0) {
                                loads.put(getCpuIndex(cols1), 0d);
                            } else {
                                long idelTimePoor = idel1 - idel0;
                                if (idelTimePoor < 1) {
                                    loads.put(getCpuIndex(cols1), 100d);
                                } else {
                                    double load = (100 - (idelTimePoor * 100.0 / timePoor));
                                    loads.put(getCpuIndex(cols1), load);
                                }
                            }
                        } else {
                            loads.put(getCpuIndex(cols1), 0d);
                        }
                    }
                    lastCpuState = times;
                    return loads;
                }
            } catch (Exception ex) {
                return loads;
            }
        } else {
            return loads;
        }
    }

}
