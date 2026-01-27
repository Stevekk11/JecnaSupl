plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    // Temporarily disabled Android plugins due to version incompatibility
    // alias(libs.plugins.androidApplication) apply false
    // alias(libs.plugins.androidLibrary) apply false
    // Temporarily disabled Compose plugins due to network access issues with dl.google.com
    // alias(libs.plugins.composeHotReload) apply false
    // alias(libs.plugins.composeMultiplatform) apply false
    // alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.comVanniktechMavenPublish) apply false
}