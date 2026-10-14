package com.inmc.customitems.block

import kr.inmc.core.integration.PluginClasses
import org.bukkit.entity.Entity
import java.lang.reflect.Method
import java.util.UUID
import java.util.logging.Logger

/**
 * 엔티티 방식 블록의 **BetterModel 모델** — 방벽 자리의 표시 엔티티(ItemDisplay)에 모델을 붙이고 뗀다(2026-10-09, Nexo 가구 옮기기).
 *
 * 리플렉션으로만 닿는다(`ContractTest`) — BetterModel 이 없는 서버에서도 우리 클래스가 다 올라와야 한다. `bettermodel-3.4.1-paper.jar` 로 확인한 길:
 * `BetterModel.modelOrNull(id)` → `ModelRenderer.getOrCreate(PlatformEntity, TrackerModifier)`, 떼기는 `BetterModel.registryOrNull(uuid).close()`.
 *
 * **다시 붙이지 않는다.** BetterModel 이 붙인 모델을 그 엔티티의 PDC 에 적어 두고, 엔티티가 다시 월드에 올라올 때(청크·재시작) 스스로
 * 붙인다(`EntityAddToWorldEvent` → `registryOrNull` → 적힌 것이 있으면 만든다). 그래서 표시 엔티티는 저장되는 것이어야 한다.
 * 애니메이션 `idle` 도 BetterModel 이 늘 튼다.
 *
 * 시야 검사(`sight-trace`)는 끈다 — 모델 기준점이 제 방벽 안에 있어 가려진 것으로 보고 안 그린다.
 */
class BlockModels(private val logger: Logger) {

    private class Api(
        val modelOrNull: Method,
        val modelKeys: Method,
        val registryOrNull: Method,
        val adapt: Method,
        val getOrCreate: Method,
        val close: Method,
        val modifier: Any,
    )

    /** 처음 쓸 때 찾는다 — 모델 블록은 사람이 놓을 때만 붙으니 그때는 BetterModel 이 이미 켜져 있다. 없거나 판이 다르면 null. */
    private val api: Api? by lazy { load() }

    val available: Boolean get() = api != null

    /** [entity] 에 [modelId] 를 붙인다. 붙였으면 true — 아니면 부르는 쪽이 아이템 모양으로 대신 보인다. */
    fun attach(entity: Entity, modelId: String): Boolean {
        val api = api ?: return false
        return runCatching {
            val renderer = api.modelOrNull.invoke(null, modelId) ?: run {
                logger.warning("BetterModel 모델을 찾을 수 없습니다: $modelId — plugins/BetterModel/models 에 $modelId.bbmodel 이 있고 /bettermodel reload 를 했는지 보세요")
                return false
            }
            api.getOrCreate.invoke(renderer, api.adapt.invoke(null, entity), api.modifier) != null
        }.getOrElse {
            logger.warning("BetterModel 모델을 붙이지 못했습니다($modelId): ${it.cause?.message ?: it.message}")
            false
        }
    }

    /** [entity] 의 모델을 뗀다. 엔티티를 지우기 전에 — 안 떼면 BetterModel 이 그 엔티티의 모델을 쥐고 있다. */
    fun detach(entity: Entity) {
        val api = api ?: return
        runCatching { api.registryOrNull.invoke(null, entity.uniqueId)?.let { api.close.invoke(it) } }
    }

    /** BetterModel 이 아는 모델 이름. 화면의 안내용 — 없거나 못 읽으면 비어 있다. */
    @Suppress("UNCHECKED_CAST")
    fun known(): Set<String> = api?.let { runCatching { it.modelKeys.invoke(null) as Set<String> }.getOrNull() }.orEmpty()

    private fun load(): Api? {
        if (!PluginClasses.isEnabled(PLUGIN)) return null
        return try {
            val main = PluginClasses.require(PLUGIN, "kr.toxicity.model.api.BetterModel")
            val platformEntity = PluginClasses.require(PLUGIN, "kr.toxicity.model.api.platform.PlatformEntity")
            val modifierClass = PluginClasses.require(PLUGIN, "kr.toxicity.model.api.tracker.TrackerModifier")
            val builder = modifierClass.getMethod("builder").invoke(null)
            builder.javaClass.getMethod("sightTrace", Boolean::class.javaPrimitiveType).invoke(builder, false)
            Api(
                modelOrNull = main.getMethod("modelOrNull", String::class.java),
                modelKeys = main.getMethod("modelKeys"),
                registryOrNull = main.getMethod("registryOrNull", UUID::class.java),
                adapt = PluginClasses.require(PLUGIN, "kr.toxicity.model.api.bukkit.platform.BukkitAdapter").getMethod("adapt", Entity::class.java),
                getOrCreate = PluginClasses.require(PLUGIN, "kr.toxicity.model.api.data.renderer.ModelRenderer")
                    .getMethod("getOrCreate", platformEntity, modifierClass),
                close = PluginClasses.require(PLUGIN, "kr.toxicity.model.api.tracker.EntityTrackerRegistry").getMethod("close"),
                modifier = builder.javaClass.getMethod("build").invoke(builder),
            )
        } catch (t: Throwable) {
            logger.warning("BetterModel 연동 실패(판이 다를 수 있습니다 — 3.4.1 로 확인함): ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private companion object {
        const val PLUGIN = "BetterModel"
    }
}
