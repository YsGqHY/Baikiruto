package org.tabooproject.baikiruto.legacyapi.event

import org.tabooproject.baikiruto.core.item.event.ItemActionTriggerEvent
import org.tabooproject.baikiruto.core.item.event.ItemBuildPostEvent
import org.tabooproject.baikiruto.core.item.event.ItemBuildPreEvent
import org.tabooproject.baikiruto.core.item.event.ItemCheckUpdateEvent
import org.tabooproject.baikiruto.core.item.event.ItemInventoryClickActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseDisplayBuildEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseFinalEvent
import org.tabooproject.baikiruto.core.item.event.ItemSelectDisplayEvent

typealias ZapItemBuildEvent = ItemBuildPreEvent
typealias ZapItemPostBuildEvent = ItemBuildPostEvent
typealias ZapItemCheckUpdateEvent = ItemCheckUpdateEvent
typealias ZapItemGenerateEvent = ItemReleaseEvent
typealias ZapItemPostGenerateEvent = ItemReleaseFinalEvent
typealias ZapDisplaySelectEvent = ItemSelectDisplayEvent
typealias ZapDisplayGenerateEvent = ItemReleaseDisplayBuildEvent
typealias ZapInventoryClickEvent = ItemInventoryClickActionEvent
typealias ZapItemActionEvent = ItemActionTriggerEvent
