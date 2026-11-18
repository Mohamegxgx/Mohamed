package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.engine.MoneyFormatter
import com.example.domain.model.AppLanguage
import com.example.domain.model.ThemePreset
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ElbagorrySummaryWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(310.dp, 170.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialSnapshot = withContext(Dispatchers.IO) {
            runCatching { ElbagorryWidgetUpdater.loadCurrentSnapshot(context.applicationContext) }
                .getOrDefault(WidgetSnapshot())
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val snapshot = if (prefs[WidgetStateKeys.HAS_DATA] == true) {
                prefs.toWidgetSnapshot()
            } else {
                initialSnapshot
            }
            val size = LocalSize.current
            ElbagorryWidgetContent(
                snapshot = snapshot,
                isWide = size.width >= 245.dp,
                isTall = size.height >= 160.dp
            )
        }
    }
}

private data class WidgetPalette(
    val bgColor: Color,
    val surfaceColor: Color,
    val primaryColor: Color,
    val onPrimaryColor: Color,
    val accentColor: Color,
    val expenseColor: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

private fun widgetPaletteFor(preset: ThemePreset): WidgetPalette = when (preset) {
    ThemePreset.HYDROGEN_NEXUS -> WidgetPalette(
        bgColor = Color(0xFF050816),
        surfaceColor = Color(0xFF101A36),
        primaryColor = Color(0xFF4F8CFF),
        onPrimaryColor = Color(0xFFFFFFFF),
        accentColor = Color(0xFF38BDF8),
        expenseColor = Color(0xFFF87171),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8)
    )
    ThemePreset.EMERALD_OBSIDIAN -> WidgetPalette(
        bgColor = Color(0xFF0A141E),
        surfaceColor = Color(0xFF142232),
        primaryColor = Color(0xFF10B981),
        onPrimaryColor = Color(0xFF022C22),
        accentColor = Color(0xFF34D399),
        expenseColor = Color(0xFFF87171),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8)
    )
    ThemePreset.ROYAL_SAPPHIRE -> WidgetPalette(
        bgColor = Color(0xFF091124),
        surfaceColor = Color(0xFF14203C),
        primaryColor = Color(0xFF3B82F6),
        onPrimaryColor = Color(0xFFFFFFFF),
        accentColor = Color(0xFFFCD34D),
        expenseColor = Color(0xFFF87171),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8)
    )
    ThemePreset.AMETHYST_ONYX -> WidgetPalette(
        bgColor = Color(0xFF110D1E),
        surfaceColor = Color(0xFF1F1836),
        primaryColor = Color(0xFFA855F7),
        onPrimaryColor = Color(0xFFFFFFFF),
        accentColor = Color(0xFF22D3EE),
        expenseColor = Color(0xFFF87171),
        textPrimary = Color(0xFFFAF5FF),
        textSecondary = Color(0xFFA69BC2)
    )
    ThemePreset.DESERT_AMBER -> WidgetPalette(
        bgColor = Color(0xFF16100B),
        surfaceColor = Color(0xFF261D14),
        primaryColor = Color(0xFFF59E0B),
        onPrimaryColor = Color(0xFF291800),
        accentColor = Color(0xFF38BDF8),
        expenseColor = Color(0xFFFB7185),
        textPrimary = Color(0xFFFAF6F0),
        textSecondary = Color(0xFFB8A99A)
    )
}

@Composable
fun ElbagorryWidgetContent(
    snapshot: WidgetSnapshot,
    isWide: Boolean,
    isTall: Boolean
) {
    val context = LocalContext.current
    val lang = snapshot.language
    val palette = widgetPaletteFor(snapshot.themePreset)

    val openDashboardIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationHelper.EXTRA_NAV_DESTINATION, NotificationHelper.DEST_QUICK_ADD)
    }
    val smartReaderIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationHelper.EXTRA_NAV_DESTINATION, NotificationHelper.DEST_SMART_READER)
    }

    val balanceText = MoneyFormatter.formatPiasters(
        piasters = snapshot.netBalancePiasters,
        currency = snapshot.currency,
        language = lang,
        hideAmounts = snapshot.hideAmounts
    )
    val todayExpenseText = MoneyFormatter.formatPiasters(
        piasters = snapshot.todayExpensePiasters,
        currency = snapshot.currency,
        language = lang,
        hideAmounts = snapshot.hideAmounts
    )
    val monthIncomeText = MoneyFormatter.formatPiasters(
        piasters = snapshot.monthIncomePiasters,
        currency = snapshot.currency,
        language = lang,
        hideAmounts = snapshot.hideAmounts
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.bgColor))
            .cornerRadius(R.dimen.widget_background_radius)
            .clickable(actionStartActivity(openDashboardIntent))
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        // Header Row: Brand + Pending Pill + Privacy Toggle
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "ELBAGORRY",
                style = TextStyle(
                    color = ColorProvider(palette.accentColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight().semantics { testTag = "widget_brand_title" }
            )

            if (snapshot.pendingCount > 0 && isWide) {
                Box(
                    modifier = GlanceModifier
                        .background(ColorProvider(palette.surfaceColor))
                        .cornerRadius(R.dimen.widget_inner_radius)
                        .clickable(actionStartActivity(smartReaderIntent))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { testTag = "widget_pending_badge" }
                ) {
                    Text(
                        text = if (lang == AppLanguage.AR) "${snapshot.pendingCount} معلقة" else "${snapshot.pendingCount} pending",
                        style = TextStyle(
                            color = ColorProvider(palette.accentColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
            }

            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(palette.surfaceColor))
                    .cornerRadius(R.dimen.widget_inner_radius)
                    .clickable(actionRunCallback<ToggleWidgetPrivacyAction>())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { testTag = "widget_privacy_toggle" }
            ) {
                Text(
                    text = if (snapshot.hideAmounts) {
                        if (lang == AppLanguage.AR) "إظهار" else "Show"
                    } else {
                        if (lang == AppLanguage.AR) "إخفاء" else "Hide"
                    },
                    style = TextStyle(
                        color = ColorProvider(palette.textSecondary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Main Net Balance
        Text(
            text = if (lang == AppLanguage.AR) "صافي الرصيد" else "Net Balance",
            style = TextStyle(
                color = ColorProvider(palette.textSecondary),
                fontSize = 11.sp
            )
        )
        Text(
            text = balanceText,
            style = TextStyle(
                color = ColorProvider(palette.textPrimary),
                fontSize = if (isWide) 20.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1,
            modifier = GlanceModifier.semantics { testTag = "widget_net_balance" }
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Bottom Row: Today's Expense (+ Month Income if tall) + Quick Add CTA
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = if (lang == AppLanguage.AR) "مصروف اليوم" else "Today's Spend",
                    style = TextStyle(
                        color = ColorProvider(palette.textSecondary),
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = todayExpenseText,
                    style = TextStyle(
                        color = ColorProvider(palette.expenseColor),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.semantics { testTag = "widget_today_expense" }
                )

                if (isTall) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = if (lang == AppLanguage.AR) "دخل الشهر: $monthIncomeText" else "Month Income: $monthIncomeText",
                        style = TextStyle(
                            color = ColorProvider(palette.accentColor),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.semantics { testTag = "widget_month_income" }
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Button(
                text = if (lang == AppLanguage.AR) "+ إضافة سريعة" else "+ Quick Add",
                onClick = actionStartActivity(quickAddIntent),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorProvider(palette.primaryColor),
                    contentColor = ColorProvider(palette.onPrimaryColor)
                ),
                modifier = GlanceModifier
                    .cornerRadius(R.dimen.widget_inner_radius)
                    .semantics { testTag = "widget_quick_add_btn" }
            )
        }
    }
}

class ToggleWidgetPrivacyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        UserPreferencesRepository(context.applicationContext).toggleHideAmounts()
        ElbagorryWidgetUpdater.updateAll(context.applicationContext)
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        ElbagorryWidgetUpdater.updateAll(context.applicationContext)
    }
}
