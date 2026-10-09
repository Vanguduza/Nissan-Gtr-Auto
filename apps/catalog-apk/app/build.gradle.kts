plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.plugin.compose")

    id("org.jetbrains.kotlin.plugin.serialization")

    id("com.google.devtools.ksp")

    id("com.chaquo.python")

}



android {

    namespace = "co.zw.nissangtr.catalogapk"

    compileSdk = 34



    defaultConfig {

        applicationId = "co.zw.nissangtr.catalogapk"

        minSdk = 26

        targetSdk = 34

        versionCode = 3

        versionName = "0.3.0-embedded"

        buildConfigField("String", "SUPABASE_URL", "\"\"")

        buildConfigField("String", "SUPABASE_ANON_KEY", "\"\"")

        ndk {

            abiFilters += listOf("arm64-v8a", "x86_64")

        }

    }



    buildTypes {

        release {

            isMinifyEnabled = false

            proguardFiles(

                getDefaultProguardFile("proguard-android-optimize.txt"),

                "proguard-rules.pro",

            )

        }

    }



    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17

        targetCompatibility = JavaVersion.VERSION_17

    }

    kotlinOptions {

        jvmTarget = "17"

    }

    buildFeatures {

        compose = true

        buildConfig = true

    }

    packaging {

        resources {

            excludes += "/META-INF/{AL2.0,LGPL2.1}"

        }

    }

}



chaquopy {

    defaultConfig {

        version = "3.12"

        pip {

            install("-r", "requirements-apk.txt")

        }

        pyc {

            src = false

        }

    }

}



// Sync monorepo pipeline modules + config into src/main/python for zero-host embeds.

val pipelineRoot = rootProject.projectDir.resolve("../../data-pipeline")

val vendorPython = tasks.register<Copy>("vendorPipelinePython") {

    from(pipelineRoot.resolve("data_pipeline")) {

        include("__init__.py")

        include("flaresolverr_transport.py")

        include("megazip_catalog_orchestrator.py")

        include("megazip_crawl_worker.py")

        include("partsouq_catalog_orchestrator.py")

        include("parse_partsouq_html.py")

        include("bundle_filter.py")

        include("bundle_quality_gate.py")

        include("import_hierarchy_catalog.py")

        include("import_catalog.py")

        include("hierarchy.py")

        include("validate.py")

        include("priority_chassis.py")

        include("chassis_discovery.py")

        include("chassis_catalog_registry.py")

        include("amayama_catalog_auto.py")

        include("scrape_etiquette.py")

        include("catalogue_watchdog.py")

        include("cache_parse_worker.py")

        include("parse_fast.py")

        include("megazip/**")

    }

    into(layout.projectDirectory.dir("src/main/python/data_pipeline"))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

}

val vendorConfig = tasks.register<Copy>("vendorPipelineConfig") {

    from(pipelineRoot.resolve("config")) {

        include("megazip_makers.json")

        include("priority_chassis.json")

        include("megazip_chassis_map.json")

        include("epc_to_pcdb.json")

        include("scrape.json")

        include("partsouq*.json")

    }

    into(layout.projectDirectory.dir("src/main/python/config"))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

}

tasks.named("preBuild").configure {
    dependsOn(vendorPython, vendorConfig)
}

afterEvaluate {
    tasks.matching { it.name.startsWith("merge") && it.name.contains("PythonSources") }.configureEach {
        dependsOn(vendorPython, vendorConfig)
    }
    tasks.matching { it.name.contains("generateDebugPython") || it.name.contains("generateReleasePython") }.configureEach {
        dependsOn(vendorPython, vendorConfig)
    }
}



dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")

    implementation(composeBom)

    androidTestImplementation(composeBom)



    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")

    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.compose.material3:material3")

    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")



    implementation("androidx.room:room-runtime:2.6.1")

    implementation("androidx.room:room-ktx:2.6.1")

    ksp("androidx.room:room-compiler:2.6.1")



    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.work:work-multiprocess:2.9.1")



    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")



    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jsoup:jsoup:1.18.1")



    implementation("androidx.browser:browser:1.8.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")



    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.0.3")

    implementation(supabaseBom)

    implementation("io.github.jan-tennert.supabase:auth-kt")

    implementation("io.github.jan-tennert.supabase:postgrest-kt")

    implementation("io.github.jan-tennert.supabase:storage-kt")

    implementation("io.github.jan-tennert.supabase:functions-kt")

    implementation("io.ktor:ktor-client-okhttp:3.0.1")



    debugImplementation("androidx.compose.ui:ui-tooling")

}


