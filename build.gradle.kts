plugins {
    id("inmc.paper-plugin")
}

group = "com.inmc.customitems"
version = "1.0.0"

inmc {
    paper = "26.2"
    pluginName = "inmc-customitems"
}

dependencies {
    implementation(libs.bstats.bukkit)

    // 리소스팩 병합이 json 을 **내용으로** 섞어야 한다 — 덮으면 두 팩 중 하나가 통째로
    // 사라진다. paper-api 는 gson 을 노출하지 않고, 서버에 있다고 기대하는 것은 버전을
    // 넘어 보장되지 않으므로 들고 간다.
    implementation(libs.gson)

    // 다른 커스텀아이템 플러그인(ItemsAdder·Nexo·Oraxen)은 건드리지 않는다 —
    // 이 플러그인은 그것들의 경쟁자이지 소비자가 아니고, 연동은 core 가 이미 갖고 있다.
}

tasks.shadowJar {
    // bStats 는 relocate 가 필수다 (가이드 함정 4).
    relocate("org.bstats", "com.inmc.customitems.lib.bstats")
    relocate("com.google.gson", "com.inmc.customitems.lib.gson")
}
