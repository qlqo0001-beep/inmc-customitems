package com.inmc.customitems

import com.inmc.customitems.pack.ModelNumbers
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelNumbersTest {

    private fun files(vararg pairs: Pair<String, String>) = pairs.associate { it.first to it.second.toByteArray() }

    @Test
    fun `낡은 overrides 와 1_21_4 range_dispatch 를 둘 다 읽는다`() {
        val legacy = files("assets/minecraft/models/item/paper.json" to """
            {"parent":"item/generated","overrides":[
              {"predicate":{"custom_model_data":10001},"model":"ia:item/coin"},
              {"predicate":{"pulling":1},"model":"x"}
            ]}
        """.trimIndent())
        val modern = files("assets/minecraft/items/rabbit_hide.json" to """
            {"model":{"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data","fallback":{"type":"model","model":"item/rabbit_hide"},
              "entries":[{"threshold":7,"model":{"type":"minecraft:model","model":"inmc:item/steel"}},
                         {"threshold":8.0,"model":{"type":"minecraft:condition","property":"x","on_true":{"type":"model","model":"a"},"on_false":{"type":"model","model":"b"}}}]}}
        """.trimIndent())

        assertEquals(listOf(ModelNumbers.Entry("paper", 10001, "ia:item/coin", "A")), ModelNumbers.scan("A", legacy))
        val found = ModelNumbers.scan("B", modern)
        assertEquals(listOf(7, 8), found.map { it.number })
        assertEquals("inmc:item/steel", found[0].model)
        assertEquals("rabbit_hide", found[0].material)
    }

    @Test
    fun `다른 팩이 같은 재질·번호를 쓰면 겹침이다`() {
        val a = ModelNumbers.Entry("paper", 1, "a", "A.zip")
        val b = ModelNumbers.Entry("paper", 1, "b", "B.zip")
        val c = ModelNumbers.Entry("paper", 2, "c", "A.zip")
        assertEquals(setOf("paper" to 1), ModelNumbers.conflicts(listOf(a, b, c)))
    }

    @Test
    fun `모델 폴더 밖의 json 과 하위 폴더는 보지 않는다`() {
        val other = files(
            "assets/minecraft/models/block/stone.json" to """{"overrides":[{"predicate":{"custom_model_data":1},"model":"x"}]}""",
            "assets/minecraft/models/item/sub/paper.json" to """{"overrides":[{"predicate":{"custom_model_data":1},"model":"x"}]}""",
        )
        assertEquals(emptyList(), ModelNumbers.scan("A", other))
    }
}
