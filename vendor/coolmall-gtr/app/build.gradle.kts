import java.util.Properties

plugins {
    alias(libs.plugins.coolmall.android.application.compose)
    alias(libs.plugins.coolmall.hilt)
}

fun localProp(name: String): String {
    val local = Properties()
    // Prefer app-local, then CoolMall root (vendor/coolmall-gtr), then monorepo root.
    val candidates = listOf(
        file("local.properties"),
        rootProject.file("local.properties"),
        rootProject.file("../../local.properties"),
    )
    for (f in candidates) {
        if (f.exists()) {
            f.inputStream().use { local.load(it) }
            break
        }
    }
    return local.getProperty(name)
        ?: (project.findProperty(name) as? String)
        ?: ""
}

android {
    defaultConfig {
        // 仅包括中文和英文必要的语言资源
        androidResources {
            localeFilters += listOf("zh", "en")
        }
        // GTR Supabase inject — never commit real keys (see local.properties.example).
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField(
            "boolean",
            "RPC_FORCE_FAKE",
            localProp("rpc.forceFake").equals("true", ignoreCase = true).toString(),
        )
    }

    // ABI 分包配置 - 一次性打包多个架构版本
    splits {
        abi {
            // 启用 ABI 分包
            isEnable = true
            // 重置默认列表
            reset()
            // 包含的架构：32位和64位 ARM
            include("armeabi-v7a", "arm64-v8a")
            // 是否生成通用 APK（包含所有架构）
            // 设置为 true 会额外生成一个包含所有架构的 APK
            isUniversalApk = false
        }
    }

    signingConfigs {
        // Optional — upstream demo keystore may be absent in the vendor tree.
        // When missing, debug/release use the Android default debug keystore.
        val keystoreFile = file("joker_open_key.keystore")
        if (keystoreFile.exists()) {
            create("common") {
                storeFile = keystoreFile
                keyAlias = "joker_open_key"
                keyPassword = "joker123456"
                storePassword = "joker123456"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    // 构建类型配置
    buildTypes {
        debug {
            signingConfigs.findByName("common")?.let { signingConfig = it }
            // debug 模式下包名后缀
            applicationIdSuffix = ".debug"
        }

        release {
            signingConfigs.findByName("common")?.let { signingConfig = it }
            // 是否启用代码压缩
            isMinifyEnabled = true
            // 资源压缩
            isShrinkResources = true
            // 配置ProGuard规则文件
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(projects.gtradapter)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.util)
    implementation(projects.core.data)
    implementation(projects.core.common)

    // 导航
    implementation(projects.core.navigation)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // 网络相关依赖
    implementation(libs.okhttp3)
    implementation(libs.retrofit)
    // 首页模块
    implementation(projects.feature.main)
    // 商品模块
    implementation(projects.feature.goods)
    // 登录(认证)模块
    implementation(projects.feature.auth)
    // 用户模块
    implementation(projects.feature.user)
    // 订单模块
    implementation(projects.feature.order)
    // 客服模块
    implementation(projects.feature.cs)
    // 通用模块
    implementation(projects.feature.common)
    // 营销模块
    implementation(projects.feature.market)
    // 反馈模块
    implementation(projects.feature.feedback)
    // 启动流程模块
    implementation(projects.feature.launch)

    // 依赖注入
    // https://developer.android.google.cn/training/dependency-injection/hilt-android?hl=zh-cn
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)

    compileOnly(libs.ksp.gradlePlugin)

    // 启动页
    implementation(libs.androidx.core.splashscreen)

    // LeakCanary - 内存泄漏检测工具（仅在debug构建中使用）
    // https://github.com/square/leakcanary
    debugImplementation(libs.leakcanary.android)

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // QQ SDK 依赖
    implementation(files("../core/common/libs/open_sdk_lite.jar"))
}
