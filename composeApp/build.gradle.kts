group = "io.github.stevekk11"
version = "1.0.1"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.comVanniktechMavenPublish)
    id("signing")
}

kotlin {

    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}


mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "JecnaSupl", version.toString())

    pom {
        name = "JecnaSupl"
        description = "A library for fetching substitutions from the spsejecna.cz website."
        inceptionYear = "2026"
        url = "https://github.com/Stevekk11/JecnaSupl"
        licenses {
            license {
                name = "GNU General Public License v3.0"
                url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "Stevekk11"
                name = "Stevekk11"
                url = "https://github.com/Stevekk11"
            }
        }
        scm {
            url = "https://github.com/Stevekk11/JecnaSupl"
            connection = "scm:git:git://github.com/Stevekk11/JecnaSupl.git"
            developerConnection = "scm:git:ssh://git@github.com/Stevekk11/JecnaSupl.git"
        }

    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

