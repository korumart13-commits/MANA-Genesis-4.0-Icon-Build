plugins {
    kotlin("jvm") version "2.0.21"
}
group = "com.mana"
version = "4.0.0-core"
repositories { mavenCentral() }
dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }
