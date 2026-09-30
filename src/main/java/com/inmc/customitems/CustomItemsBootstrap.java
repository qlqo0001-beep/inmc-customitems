package com.inmc.customitems;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

/**
 * 서버가 켜지기 전 — 레지스트리를 고칠 수 있는 유일한 때 — 에 "빠른 메뉴" 창을 등록하고, 순정 <b>빠른 동작 키</b>(기본 G, 1.21.6+,
 * 조작 설정의 "Quick Actions")의 태그 {@code #minecraft:quick_actions} 에 넣는다(사용자 결정 2026-09-30 — 모드 없는 배낭 단축키).
 * 태그에 창이 하나면 키를 누르는 즉시 그 창이 뜨고, 여럿이면 순정 목록 창이 먼저 뜬다.
 *
 * <p><b>자바로 쓴다.</b> bootstrap 도 플러그인과 같은 클래스로더에서 돌지만, 이 단계의 클래스로더는 {@code dependencies.bootstrap}
 * 에 적은 플러그인만 본다(Paper {@code BootstrapMetaDependencyTree} — 26.2 에서 확인). 우리 Kotlin 런타임은 jar 에 없고(0바이트)
 * {@code dependencies.server} 의 inmc-core 에서 오므로 여기서는 안 보인다 — Kotlin 으로 쓰면 {@code NoClassDefFoundError} 로
 * Paper 가 "Failed to run bootstrapper … This plugin will not be loaded" 를 남기고 <b>이 플러그인을 통째로 안 켠다</b>.
 * 런타임을 jar 에 넣거나 PluginLoader 로 붙이는 것도 안 된다 — 그러면 Kotlin 이 둘이 되어 core 에 넘기는 람다({@code Function1})가
 * 서로 다른 클래스가 된다({@code LinkageError}).
 *
 * <p>버튼은 서버로 돌아오는 custom click({@link #OPEN_BACKPACK}) — {@code EquipmentListener.onQuickAction} 이 받는다. 이 창은
 * 누구에게나 같은(정적인) 데이터라 사람마다 다른 목록은 담지 못한다 — 그래서 [배낭] 은 <b>응답을 기다리고</b>(WAIT_FOR_RESPONSE), 서버가
 * 그 사람이 맨 배낭 수만큼 버튼이 있는 창을 그때 만들어 보낸다(하나면 곧바로 연다, 사용자 결정 2026-09-30).
 */
public final class CustomItemsBootstrap implements PluginBootstrap {

    /** 빠른 메뉴 창. */
    public static final Key QUICK_MENU = Key.key("inmc", "quick_menu");

    /** [배낭 열기] 버튼이 서버로 보내는 열쇠. */
    public static final Key OPEN_BACKPACK = Key.key("inmc", "backpack");

    @Override
    public void bootstrap(BootstrapContext context) {
        TypedKey<Dialog> menu = DialogKeys.create(QUICK_MENU);
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event ->
            event.registry().register(menu, builder -> builder
                .base(DialogBase.builder(Component.text("빠른 메뉴"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                    .body(List.of(DialogBody.plainMessage(
                        Component.text("빠른 동작 키(기본 G)로 여는 메뉴입니다. 키는 조작 설정에서 바꿀 수 있습니다.", NamedTextColor.GRAY), 300)))
                    .build())
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(Component.text("배낭", NamedTextColor.GOLD))
                            .tooltip(Component.text("장착 칸(/장비)에 끼운 배낭 — 여럿이면 고르는 창이 뜹니다"))
                            .width(160)
                            .action(DialogAction.customClick(OPEN_BACKPACK, null))
                            .build()))
                    .columns(1)
                    .build())));
        // 태그를 못 고쳐도 플러그인은 켜져야 한다(여기서 던지면 Paper 가 이 플러그인을 안 켠다) — 창과 /배낭 은 그대로 되고 G 키만 안 된다.
        try {
            context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event ->
                event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, List.of(menu)));
        } catch (RuntimeException e) {
            context.getLogger().warn("빠른 동작 키(G)에 빠른 메뉴를 걸지 못했습니다: " + e.getMessage());
        }
    }
}
