package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Workspace
import com.example.domain.WorkspaceRole
import com.example.ui.AppUiState
import com.example.ui.NavDestination
import com.example.ui.NavGroup
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentIndigoTint
import com.example.ui.theme.BgPrimary
import com.example.ui.theme.BgSecondary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LedgerLabMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(TextPrimary),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            drawRect(color = Color.White, topLeft = Offset(w * 0.14f, h * 0.15f), size = Size(w * 0.22f, h * 0.70f))
            drawRect(color = Color.White, topLeft = Offset(w * 0.14f, h * 0.65f), size = Size(w * 0.56f, h * 0.20f))
            drawRect(color = AccentIndigo, topLeft = Offset(w * 0.48f, h * 0.15f), size = Size(w * 0.22f, h * 0.44f))
            drawRect(color = AccentIndigo, topLeft = Offset(w * 0.48f, h * 0.15f), size = Size(w * 0.38f, h * 0.18f))
        }
    }
}

private fun NavDestination.icon(): ImageVector = when (this) {
    NavDestination.OVERVIEW -> Icons.Outlined.Dashboard
    NavDestination.ACCOUNTS -> Icons.Outlined.AccountBalanceWallet
    NavDestination.CUSTOMERS -> Icons.Outlined.Group
    NavDestination.TRANSACTIONS -> Icons.AutoMirrored.Outlined.ReceiptLong
    NavDestination.TRANSFERS -> Icons.Outlined.SwapHoriz
    NavDestination.LEDGER -> Icons.Outlined.AccountBalance
    NavDestination.RECONCILIATION -> Icons.Outlined.Balance
    NavDestination.RISK_FRAUD -> Icons.Outlined.Security
    NavDestination.API_PLAYGROUND -> Icons.Outlined.Code
    NavDestination.API_DOCS -> Icons.Outlined.Description
    NavDestination.WEBHOOKS -> Icons.Outlined.Webhook
    NavDestination.API_KEYS -> Icons.Outlined.Key
    NavDestination.AUDIT_LOGS -> Icons.Outlined.History
    NavDestination.SETTINGS -> Icons.Outlined.Settings
}

@Composable
fun AppShell(
    uiState: AppUiState,
    workspaces: List<Workspace>,
    currentRole: WorkspaceRole?,
    pendingReviewCount: Int,
    openAlertsCount: Int,
    onNavigate: (NavDestination) -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onToggleSidebar: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSwitchDemoActor: (String) -> Unit,
    onLogout: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
    ) {
        val isWide = maxWidth >= 840.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                SidebarContent(
                    activeDestination = uiState.activeDestination,
                    collapsed = uiState.sidebarCollapsed,
                    onToggleCollapse = onToggleSidebar,
                    onSelectDestination = onNavigate,
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    TopNavBar(
                        isWide = true,
                        uiState = uiState,
                        workspaces = workspaces,
                        currentRole = currentRole,
                        pendingReviewCount = pendingReviewCount,
                        openAlertsCount = openAlertsCount,
                        onOpenMenu = { onToggleSidebar() },
                        onSelectWorkspace = onSelectWorkspace,
                        onSearchChange = onSearchChange,
                        onNavigate = onNavigate,
                        onSwitchDemoActor = onSwitchDemoActor,
                        onLogout = onLogout
                    )
                    content(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = BgPrimary,
                        modifier = Modifier.width(272.dp)
                    ) {
                        SidebarContent(
                            activeDestination = uiState.activeDestination,
                            collapsed = false,
                            onToggleCollapse = { scope.launch { drawerState.close() } },
                            onSelectDestination = { dest ->
                                onNavigate(dest)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                }
            ) {
                Scaffold(
                    containerColor = BgSecondary,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        TopNavBar(
                            isWide = false,
                            uiState = uiState,
                            workspaces = workspaces,
                            currentRole = currentRole,
                            pendingReviewCount = pendingReviewCount,
                            openAlertsCount = openAlertsCount,
                            onOpenMenu = { scope.launch { drawerState.open() } },
                            onSelectWorkspace = onSelectWorkspace,
                            onSearchChange = onSearchChange,
                            onNavigate = onNavigate,
                            onSwitchDemoActor = onSwitchDemoActor,
                            onLogout = onLogout
                        )
                    }
                ) { innerPadding ->
                    content(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarContent(
    activeDestination: NavDestination,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onSelectDestination: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val width = if (collapsed) 72.dp else 248.dp
    Surface(
        modifier = modifier.width(width),
        color = BgPrimary,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Brand area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LedgerLabMark()
                    if (!collapsed) {
                        Column {
                            Text(
                                text = "LedgerLab",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "DEVELOPER PLATFORM",
                                fontSize = 9.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )
                        }
                    }
                }
                if (!collapsed) {
                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                            contentDescription = "Collapse sidebar",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NavGroup.entries.forEach { group ->
                    if (!collapsed) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp)
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                    NavDestination.entries.filter { it.group == group }.forEach { dest ->
                        val selected = dest == activeDestination
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) AccentIndigoTint else Color.Transparent)
                                .clickable { onSelectDestination(dest) }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                .testTag("nav_${dest.route}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start
                        ) {
                            Icon(
                                imageVector = dest.icon(),
                                contentDescription = dest.label,
                                tint = if (selected) AccentIndigo else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            if (!collapsed) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = dest.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) AccentIndigo else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopNavBar(
    isWide: Boolean,
    uiState: AppUiState,
    workspaces: List<Workspace>,
    currentRole: WorkspaceRole?,
    pendingReviewCount: Int,
    openAlertsCount: Int,
    onOpenMenu: () -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onSwitchDemoActor: (String) -> Unit,
    onLogout: () -> Unit
) {
    var wsMenuExpanded by remember { mutableStateOf(false) }
    var notifExpanded by remember { mutableStateOf(false) }
    var userMenuExpanded by remember { mutableStateOf(false) }

    val activeWs = workspaces.firstOrNull { it.id == uiState.activeWorkspaceId }

    Surface(
        color = BgPrimary,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isWide || uiState.sidebarCollapsed) {
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation", tint = TextPrimary)
                        }
                    }

                    // Workspace Selector
                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BgSecondary,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .clickable { wsMenuExpanded = true }
                                .testTag("workspace_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AccentIndigo)
                                )
                                Text(
                                    text = activeWs?.name ?: "Workspace",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Outlined.ExpandMore,
                                    contentDescription = "Switch workspace",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = wsMenuExpanded,
                            onDismissRequest = { wsMenuExpanded = false },
                            containerColor = BgPrimary
                        ) {
                            workspaces.forEach { ws ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(ws.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(ws.id, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        }
                                    },
                                    onClick = {
                                        wsMenuExpanded = false
                                        onSelectWorkspace(ws.id)
                                    }
                                )
                            }
                        }
                    }

                    if (isWide) {
                        OutlinedTextField(
                            value = uiState.globalSearch,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Search transactions, accounts, customers...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentIndigo,
                                unfocusedBorderColor = BorderSubtle,
                                focusedContainerColor = BgSecondary,
                                unfocusedContainerColor = BgSecondary
                            )
                        )
                    }
                }

                // Right actions: Docs shortcut, Notifications, User Profile
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onNavigate(NavDestination.API_DOCS) },
                        modifier = Modifier.testTag("top_docs_shortcut")
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = "API Documentation",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { notifExpanded = true },
                            modifier = Modifier.testTag("notifications_button")
                        ) {
                            Box {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (pendingReviewCount + openAlertsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(StatusError)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = notifExpanded,
                            onDismissRequest = { notifExpanded = false },
                            containerColor = BgPrimary
                        ) {
                            DropdownMenuItem(
                                text = { Text("$pendingReviewCount transactions awaiting review", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    notifExpanded = false
                                    onNavigate(NavDestination.TRANSACTIONS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("$openAlertsCount open risk & fraud alerts", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    notifExpanded = false
                                    onNavigate(NavDestination.RISK_FRAUD)
                                }
                            )
                        }
                    }

                    Box {
                        val initials = (uiState.currentUser?.name ?: "LL")
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                            .joinToString("")
                        Surface(
                            shape = CircleShape,
                            color = AccentIndigoTint,
                            border = BorderStroke(1.dp, AccentIndigo.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { userMenuExpanded = true }
                                .testTag("user_profile_menu")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AccentIndigo
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = userMenuExpanded,
                            onDismissRequest = { userMenuExpanded = false },
                            containerColor = BgPrimary
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(uiState.currentUser?.name ?: "", style = MaterialTheme.typography.titleSmall)
                                Text(uiState.currentUser?.email ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                StatusPill(
                                    text = "Role: ${currentRole?.label ?: "Member"}",
                                    textColor = AccentIndigo,
                                    backgroundColor = AccentIndigoTint
                                )
                            }
                            HorizontalDivider(color = BorderSubtle)
                            DropdownMenuItem(
                                text = { Text("Switch Actor: Aarav (Owner)", style = MaterialTheme.typography.bodySmall) },
                                onClick = { userMenuExpanded = false; onSwitchDemoActor("usr_demo_owner") }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Actor: Priya (Admin)", style = MaterialTheme.typography.bodySmall) },
                                onClick = { userMenuExpanded = false; onSwitchDemoActor("usr_demo_admin") }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Actor: Rohan (Developer)", style = MaterialTheme.typography.bodySmall) },
                                onClick = { userMenuExpanded = false; onSwitchDemoActor("usr_demo_dev") }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Actor: Neha (Viewer)", style = MaterialTheme.typography.bodySmall) },
                                onClick = { userMenuExpanded = false; onSwitchDemoActor("usr_demo_viewer") }
                            )
                            HorizontalDivider(color = BorderSubtle)
                            DropdownMenuItem(
                                text = { Text("Workspace Settings", style = MaterialTheme.typography.bodyMedium) },
                                onClick = { userMenuExpanded = false; onNavigate(NavDestination.SETTINGS) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out", style = MaterialTheme.typography.bodyMedium, color = StatusError) },
                                onClick = { userMenuExpanded = false; onLogout() }
                            )
                        }
                    }
                }
            }
        }
    }
}
