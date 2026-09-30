package com.kemai.app.ui.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.palette

/** 指南里的一步 */
private data class GuideStep(
    val index: Int,
    val title: String,
    val body: String,
)

private data class GuideTopic(
    val name: String,
    /** 章节配图：应用真实界面截图，已标好序号 */
    val imageRes: Int,
    val intro: String,
    val steps: List<GuideStep>,
)

/**
 * 操作指南。
 *
 * 配图是应用真实界面的截图，步骤序号直接标在图上（左侧标号栏 + 界面上高亮横带），
 * 下面配文字说明。呈现方式参考了你给的那两张「有余记账」操作指南。
 */
@Composable
fun GuideScreen(
    repo: AppRepository,
    onBack: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current

    val topics = remember { guideTopics() }
    var current by remember { mutableStateOf(0) }
    var zoomed by remember { mutableStateOf<Int?>(null) }

    val topic = topics[current]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.canvasBg),
    ) {
        // ---------- 顶栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_back, contentDescription = "返回", tint = pal.textPrimary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "操作指南",
                style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                color = pal.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "关闭",
                style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
                color = pal.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // ---------- 主题切换（两行三列） ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topics.chunked(3).forEachIndexed { rowIdx, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { colIdx, t ->
                        val i = rowIdx * 3 + colIdx
                        val active = i == current
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) pal.accent.copy(alpha = 0.14f) else pal.surfaceVariant
                                )
                                .clickable { current = i }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${i + 1}. ${t.name}",
                                style = TextStyle(
                                    fontSize = d.tiny,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (active) pal.accent else pal.textSecondary,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "点截图可放大查看",
            style = TextStyle(fontSize = d.tiny),
            color = pal.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
        )

        // ---------- 正文 ----------
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { zoomed = topic.imageRes }
                    .background(pal.surface),
            ) {
                Image(
                    painter = painterResource(topic.imageRes),
                    contentDescription = topic.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(816f / 1600f),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = topic.intro,
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
            )

            Spacer(Modifier.height(14.dp))
            topic.steps.forEach { s ->
                Row(modifier = Modifier.padding(vertical = 7.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(pal.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${s.index}",
                            style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Bold),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.title,
                            style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
                            color = pal.textPrimary,
                        )
                        Text(
                            text = s.body,
                            style = TextStyle(fontSize = d.caption),
                            color = pal.textSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.surfaceVariant)
                    .padding(14.dp),
            ) {
                Text(
                    text = "三个内置字段的名字（默认「客户名称 / 首次办卡时间 / 使用过的产品」）"
                        + "可以在「设置 → 字段名称」里改成任何行业的叫法；"
                        + "脉络方向也能在「设置 → 外观」里切换成横向。",
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textPrimary,
                )
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(30.dp))
        }
    }

    // ---------- 放大查看 ----------
    zoomed?.let { res ->
        AlertDialog(
            onDismissRequest = { zoomed = null },
            containerColor = pal.surface,
            confirmButton = { TextButton(onClick = { zoomed = null }) { Text("关闭") } },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

/** 指南内容。配图放在 res/drawable-nodpi，都是应用真实界面（已标序号）。 */
private fun guideTopics(): List<GuideTopic> = listOf(
    GuideTopic(
        name = "认识主界面",
        imageRes = R.drawable.guide_1_main,
        intro = "打开应用就是这张客户脉络图。每位客户是一张卡片，"
            + "由谁介绍来的，就挂在谁的下方。",
        steps = listOf(
            GuideStep(1, "左上角", "应用图标、名称和宣传语。右边依次是搜索、这份操作指南、设置。"),
            GuideStep(2, "客户卡片", "卡片下方浅色小字「n 位下级」是个按钮，点它可以折叠整棵子树。"),
            GuideStep(3, "空白处", "单指拖动平移，双指捏合缩放。画布是无限大的，上下左右都能一直延伸。"),
            GuideStep(4, "右下角「适应全图」", "客户多了点一下就能看全貌。新增客户不用找加号 —— 选中卡片后用「加下级 / 加同级」。"),
        ),
    ),
    GuideTopic(
        name = "建客户填资料",
        imageRes = R.drawable.guide_2_editor,
        intro = "所有客户信息都在这一个面板里填。除了名称，其余都可以留空。",
        steps = listOf(
            GuideStep(1, "客户名称", "必填。这个名字就是图上卡片显示的文字。"),
            GuideStep(2, "首次办卡时间", "选填。填了之后卡片右上角会多一个小圆点作标记。"),
            GuideStep(3, "使用过的产品", "选填。点一下选中，可多选；搜不到的产品，右边 ＋ 直接新建。"),
            GuideStep(4, "介绍人", "决定这张卡片挂在图上的位置，随时可以改。"),
            GuideStep(5, "备注", "随手写点什么，比如「周一上午不要打电话」。"),
            GuideStep(6, "自定义信息", "想加几条加几条。标签输过一次，下次输一个字就能自动补全。"),
        ),
    ),
    GuideTopic(
        name = "折叠与快捷操作",
        imageRes = R.drawable.guide_3_collapse,
        intro = "客户多起来之后，靠折叠和底部这几个按钮把图收拾干净。",
        steps = listOf(
            GuideStep(1, "折叠按钮", "点「n 位下级」收起整棵子树，按钮变成「n 位已收起」，再点一下展开。"),
            GuideStep(2, "空白处", "拖动平移、捏合缩放。折叠状态会自动保存，下次打开还是这样。"),
            GuideStep(3, "选中后的操作条", "加下级＝这位客户介绍来的人；加同级＝和它同一个介绍人的客户。这是新增客户的主要方式。"),
        ),
    ),
    GuideTopic(
        name = "拜访清单",
        imageRes = R.drawable.guide_4_visits,
        intro = "「新客户每周拜访」是系统按办卡时间自动生成的，每周一重置；"
            + "下面的清单你可以自己建、自己起名。",
        steps = listOf(
            GuideStep(1, "右上角 ＋", "新建一张清单，名字随你起，比如「本月重点」。"),
            GuideStep(2, "清单标题行", "显示清单名和还有几位待拜访；＋ 往清单里加人，⋮ 重命名或删除。"),
            GuideStep(3, "待拜访条目", "点左边的圈＝用今天打勾标记已拜访，再点一下可取消；点整行打开详情。"),
        ),
    ),
    GuideTopic(
        name = "提醒设置",
        imageRes = R.drawable.guide_5_reminder,
        intro = "提醒可以完全按你的习惯配，改完立即生效。",
        steps = listOf(
            GuideStep(1, "拜访提醒开关", "关掉就没有系统通知；但顶部角标始终准确，不会漏跟。"),
            GuideStep(2, "通知是否正常？", "点进去逐项自检，还有一键发测试通知。"),
            GuideStep(3, "提醒频率", "每天 / 每周 / 每两周，默认每周。"),
            GuideStep(4, "提醒日", "默认周一。选「每天」时这一项会自动隐藏。"),
            GuideStep(5, "提醒时间", "默认 09:00。"),
            GuideStep(6, "新客户判定", "决定「多久内办卡的算新客户」，默认最近 6 个月。"),
        ),
    ),
    GuideTopic(
        name = "通知自检",
        imageRes = R.drawable.guide_6_check,
        intro = "系统通知可能被手机省电策略拦住。这一页把原因逐条摊开，不用猜。",
        steps = listOf(
            GuideStep(1, "通知权限", "没开的话点右边直接跳去开。"),
            GuideStep(2, "电池优化", "显示系统会不会限制后台；点「去设置」可直接修改。"),
            GuideStep(3, "厂商后台策略", "这项没法自动读取，需要你手动确认一次。"),
            GuideStep(4, "下次提醒时间", "让你知道闹钟到底排上没有。"),
            GuideStep(5, "发一条测试通知", "按一下马上知道通道通不通，不用等到下周一。"),
            GuideStep(6, "按你的手机品牌设置", "会自动匹配你的机型，给出具体的后台设置路径。"),
        ),
    ),
)
