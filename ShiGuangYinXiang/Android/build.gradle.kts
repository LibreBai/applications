// AGP 9 内置 Kotlin：不再需要 apply org.jetbrains.kotlin.android。
// 此处仅声明插件版本，具体在 app 模块按需应用。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
