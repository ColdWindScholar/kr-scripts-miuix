package com.projectkr.shell

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.KrScriptActionHandler
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.omarea.vtools.FloatMonitor
import com.projectkr.shell.permissions.CheckRootStatus
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.More

enum class MainTab {
    Home, Favourites, Pages
}
enum class MenuItems {
    Graph, Info
}
@Composable
fun PowerItem(
    title: String,
    desc: String,
    iconRes: Int,
    iconBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 65.dp)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标部分
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBgColor, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .alpha(0.8f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 文本部分
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
    }
}
class MainActivity : AppCompatActivity() {
    private val progressBarDialog = ProgressBarDialog(this)
    private var handler = Handler()
    private var krScriptConfig = KrScriptConfig()

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

    @SuppressLint("ContextCastToActivity")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        krScriptConfig = KrScriptConfig()

        setContent {
            progressBarDialog.showDialog(getString(R.string.please_wait))
            val page2Config = krScriptConfig.pageListConfig
            val favoritesConfig = krScriptConfig.favoriteConfig
            var pages = getItems(page2Config)
            var favorites = getItems(favoritesConfig)
            handler.post {
                progressBarDialog.hideDialog()
            }
            val items = listOf(
                NavigationItem(
                    label = getString(R.string.tab_home),
                    icon = MiuixIcons.MapAlbum),
                NavigationItem(label = getString(R.string.tab_favorites), icon = MiuixIcons.Favorites),
                NavigationItem(label = getString(R.string.tab_pages), icon = MiuixIcons.More)
            )
            val pagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { items.size }
            )
            val coroutineScope = rememberCoroutineScope()

            // 同步 pager 状态到当前页面索引
            val currentPage by remember { derivedStateOf { pagerState.currentPage } }
            if (!(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111);
            }
            val showPowerDialog = remember { mutableStateOf(false) }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = getString(R.string.app_name),
                        actions = {
                            IconButton({onOptionsItemSelected(MenuItems.Graph.ordinal)}) { Icon(
                                painter = painterResource(R.drawable.graph), null
                            ) }
                            IconButton({
                                showPowerDialog.value = true
                            })
                            { Icon(painter = painterResource(R.drawable.power), null) }
                            IconButton({onOptionsItemSelected(MenuItems.Info.ordinal)})
                            { Icon(painter = painterResource(R.drawable.info), null) }

                        })
                },
                bottomBar = {
                    NavigationBar(
                        mode = NavigationDisplayMode.IconAndText,
                        content = {
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = item.icon,
                                    label = item.label,
                                    selected = currentPage == index,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                )
                            }
                        })
                }
            ) {
                val fragmentManager = (LocalContext.current as FragmentActivity).supportFragmentManager
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 1,
                        key = { MainTab.entries[it].name }
                    ) { page ->
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                when (MainTab.entries[page]) {
                                    MainTab.Home -> {
                                        FrameLayout(context).apply {
                                            id = View.generateViewId()
                                        if (CheckRootStatus.lastCheckResult && krScriptConfig.allowHomePage) {//fixme:just for debug: CheckRootStatus.lastCheckResult && krScriptConfig.allowHomePage
                                            val home = FragmentHome()
                                            val fragmentManager = fragmentManager
                                            val transaction = fragmentManager.beginTransaction()
                                            transaction.replace(id, home)
                                            transaction.commitAllowingStateLoss()
                                        }}
                                    }

                                    MainTab.Favourites -> {
                                        FrameLayout(context).apply {
                                            id = View.generateViewId()
                                        val favoritesFragment = ActionListFragment.create(favorites, getKrScriptActionHandler(favoritesConfig, true), null, ThemeModeState.getThemeMode())
                                        fragmentManager.beginTransaction().replace(id, favoritesFragment).commitAllowingStateLoss()
                                    }}

                                    MainTab.Pages -> {
                                        FrameLayout(context).apply {
                                            id = View.generateViewId()
                                        val allItemFragment = ActionListFragment.create(pages, getKrScriptActionHandler(page2Config, false), null, ThemeModeState.getThemeMode())
                                        fragmentManager.beginTransaction().replace(id, allItemFragment).commitAllowingStateLoss()
                                    }}
                                }
                            }
                        )

                    }
                }
            }
            SuperDialog(
                showPowerDialog,
                onDismissRequest = {
                    showPowerDialog.value = false
                },
                title = "选择操作"
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 16.dp)
                        .alpha(0.85f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PowerItem(
                            modifier = Modifier.weight(1f),
                            title = "关机",
                            desc = "正常关机",
                            iconRes = R.drawable.power_shutdown,
                            iconBgColor = Color(0xFF4BA5FF)
                        ) {
                            showPowerDialog.value = false
                            KeepShellPublic.doCmdSync(getString(R.string.power_shutdown_cmd))
                        }

                        PowerItem(
                            modifier = Modifier.weight(1f),
                            title = "重启",
                            desc = "正常重启",
                            iconRes = R.drawable.power_reboot,
                            iconBgColor = Color(0xFF8BC34A)
                        ) { showPowerDialog.value = false
                            KeepShellPublic.doCmdSync(getString(R.string.power_reboot_cmd))
                        }
                    }

                    PowerItem(
                        title = "热重启",
                        desc = "只重启系统界面而不重新引导系统（可能引发Bug）",
                        iconRes = R.drawable.power_hot_reboot,
                        iconBgColor = Color(0xFF00BCD4)
                    ) {
                        showPowerDialog.value = false
                        KeepShellPublic.doCmdSync(getString(R.string.power_hot_reboot_cmd))
                    }

                    PowerItem(
                        title = "Recovery",
                        desc = "重启到Recovery模式（俗称卡刷模式）",
                        iconRes = R.drawable.power_recovery,
                        iconBgColor = Color(0XC8787878)
                    ) { showPowerDialog.value = false
                        KeepShellPublic.doCmdSync(getString(R.string.power_recovery_cmd))
                    }

                    PowerItem(
                        title = "Fastboot",
                        desc = "重启到引导模式（俗称线刷模式）",
                        iconRes = R.drawable.power_fastboot,
                        iconBgColor = Color(0XC8787878)
                    ) { showPowerDialog.value = false
                        KeepShellPublic.doCmdSync(getString(R.string.power_fastboot_cmd))
                    }
                    PowerItem(
                        title = "9008(EDL)",
                        desc = "重启到9008模式，*此模式仅限部分骁龙设备可用",
                        iconRes = R.drawable.power_emergency,
                        iconBgColor = Color(0XC8787878)
                    ) { showPowerDialog.value = false
                        KeepShellPublic.doCmdSync(getString(R.string.power_emergency_cmd))
                    }
                }
            }
        }
    }

    private fun getItems(pageNode: PageNode): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        if (pageNode.pageConfigSh.isNotEmpty()) {
            items = PageConfigSh(this, pageNode.pageConfigSh, null).execute()
        }
        if (items == null && pageNode.pageConfigPath.isNotEmpty()) {
            items = PageConfigReader(this.applicationContext, pageNode.pageConfigPath, null).readConfigXml()
        }

        return items
    }
    private fun reloadFavoritesTab() {
        Thread {
            val favoritesConfig = krScriptConfig.favoriteConfig
            val favorites = getItems(favoritesConfig)
            favorites?.run {
                handler.post {
                    updateFavoritesTab(this, favoritesConfig)
                }
            }
        }.start()
    }
    private fun updateFavoritesTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val favoritesFragment = ActionListFragment.create(items, getKrScriptActionHandler(pageNode, true), null, ThemeModeState.getThemeMode())
       // supportFragmentManager.beginTransaction().replace(R.id.list_favorites, favoritesFragment).commitAllowingStateLoss()
    }

    private fun updateMoreTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val allItemFragment = ActionListFragment.create(items, getKrScriptActionHandler(pageNode, false), null, ThemeModeState.getThemeMode())
        //supportFragmentManager.beginTransaction().replace(R.id.list_pages, allItemFragment).commitAllowingStateLoss()
    }

    private fun reloadMoreTab() {
        Thread {
            val page2Config = krScriptConfig.pageListConfig
            val pages = getItems(page2Config)
            pages?.run {
                handler.post {
                    updateMoreTab(this, page2Config)
                }
            }
        }.start()
    }
    private fun getKrScriptActionHandler(pageNode: PageNode, isFavoritesTab: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.autoFinish ) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    // TODO:多线程优化
                    if (isFavoritesTab) {
                        reloadFavoritesTab()
                    } else {
                        reloadMoreTab()
                    }
                }
            }

            override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
                val page = clickableNode as? PageNode
                    ?: if (clickableNode is RunnableNode) {
                        pageNode
                    } else {
                        return
                    }

                val intent = Intent()

                intent.component = ComponentName(this@MainActivity.applicationContext, ActionPage::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                if (clickableNode is RunnableNode) {
                    intent.putExtra("autoRunItemId", clickableNode.key)
                }
                intent.putExtra("page", page)

                addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                _openPage(pageNode)
            }

            override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                return chooseFilePath(fileSelectedInterface)
            }
        }
    }

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300

    private fun chooseFilePath(extension: String) {
        try {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("extension", extension)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER_INNER)
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, "启动内置文件选择器失败！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(com.omarea.krscript.R.string.kr_write_external_storage), Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2);
            return false
        } else {
            return try {
                val suffix = fileSelectedInterface.suffix()
                if (!suffix.isNullOrEmpty()) {
                    chooseFilePath(suffix)
                } else {
                    val intent = Intent(Intent.ACTION_GET_CONTENT);
                    val mimeType = fileSelectedInterface.mimeType()
                    if (mimeType != null) {
                        intent.type = mimeType
                    } else {
                        intent.type = "*/*"
                    }
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER);
                }
                this.fileSelectedInterface = fileSelectedInterface
                true;
            } catch (ex: java.lang.Exception) {
                false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_FILE_PATH_CHOOSER) {
            val result = if (data == null || resultCode != RESULT_OK) null else data.data
            if (fileSelectedInterface != null) {
                if (result != null) {
                    val absPath = getPath(result)
                    fileSelectedInterface?.onFileSelected(absPath)
                } else {
                    fileSelectedInterface?.onFileSelected(null)
                }
            }
            this.fileSelectedInterface = null
        } else if (requestCode == ACTION_FILE_PATH_CHOOSER_INNER) {
            val absPath = if (data == null || resultCode != RESULT_OK) null else data.getStringExtra("file")
            fileSelectedInterface?.onFileSelected(absPath)
            this.fileSelectedInterface = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getPath(uri: Uri): String? {
        try {
            return FilePathResolver().getPath(this, uri)
        } catch (ex: Exception) {
            return null
        }
    }

    fun _openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    fun onOptionsItemSelected(item: Int): Boolean {
        when (MenuItems.entries[item]) {
            MenuItems.Info -> {
                val layoutInflater = LayoutInflater.from(this)
                val layout = layoutInflater.inflate(R.layout.dialog_about, null)
                val transparentUi = layout.findViewById<CompoundButton>(R.id.transparent_ui);
                val themeConfig = ThemeConfig(this)
                transparentUi.setOnClickListener {
                    val isChecked = (it as CompoundButton).isChecked
                    if (isChecked && !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        it.isChecked = false
                        Toast.makeText(this@MainActivity, com.omarea.krscript.R.string.kr_write_external_storage, Toast.LENGTH_SHORT).show()
                    } else {
                        themeConfig.setAllowTransparentUI(isChecked)
                    }
                }
                transparentUi.isChecked = themeConfig.getAllowTransparentUI()

                DialogHelper.customDialog(this, layout)
            }
            MenuItems.Graph -> {
                if (FloatMonitor.isShown == true) {
                    FloatMonitor(this).hidePopupWindow()
                    return false
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        FloatMonitor(this).showPopupWindow()
                        Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                    } else {
                        //若没有权限，提示获取
                        //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        //startActivity(intent);
                        val intent = Intent()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        intent.data = Uri.fromParts("package", this.packageName, null)

                        Toast.makeText(applicationContext, getString(R.string.permission_float), Toast.LENGTH_LONG).show()

                        try {
                            startActivity(intent)
                        } catch (ex: Exception) {
                        }
                    }
                } else {
                    FloatMonitor(this).showPopupWindow()
                    Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                }
            }
        }
        return true
    }
}
