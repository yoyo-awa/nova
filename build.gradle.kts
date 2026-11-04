plugins {
    java
    kotlin("jvm") version "2.4.20"
    application
    id("com.gradleup.shadow") version "9.4.3"
}

group = "nova"
version = "1.0.0-alpha"

repositories {
    // 阿里云镜像，同步 Maven Central 的内容，国内速度飞快
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    // 保留官方作为兜底（镜像没有的包会走这里）
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.json:json:20240303")
    implementation("org.tomlj:tomlj:1.1.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("nova.EntryKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8"
    )
}

tasks.shadowJar {
    archiveFileName.set("nova.jar")
    manifest {
        attributes["Main-Class"] = "nova.EntryKt"
    }
}