package com.omarea.common.shell

/**
 * 操作内核参数节点
 * Created by Hello on 2017/11/01.
 */
object KernelProrp {
    /**
     * 获取属性
     * @param propName 属性名称
     * @return
     */
    fun getProp(propName: String): String {
        return KeepShellPublic.doCmdSync(listOf("if [[ -e \"$propName\" ]]; then cat \"$propName\"; fi;"))
    }

    fun getProp(propName: String, grep: String): String {
        return KeepShellPublic.doCmdSync(listOf("if [[ -e \"$propName\" ]]; then cat \"$propName\" | grep \"$grep\"; fi;"))
    }

}