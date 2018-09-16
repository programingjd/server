import com.jfrog.bintray.gradle.BintrayExtension
import org.cyberneko.html.parsers.DOMParser
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.FileInputStream
import java.io.FileWriter
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

buildscript {
  repositories {
    jcenter()
  }
}

plugins {
  kotlin("jvm") version "1.2.70"
  `maven-publish`
  id("org.jetbrains.dokka") version "0.9.17"
  id("com.jfrog.bintray") version "1.8.4"
}

group = "info.jdavid.asynk"
version = "0.0.0.13.0"

repositories {
  jcenter()
}

dependencies {
  compile(kotlin("stdlib-jdk8"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.26.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-nio:0.26.0")
  implementation("org.slf4j:slf4j-api:1.7.25")
  implementation("info.jdavid.asynk:http:0.0.0.13")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.0")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.3.0")
  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.3.0")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.9.6")
  testImplementation("org.apache.httpcomponents:httpclient:4.5.6")
  testImplementation("info.jdavid.asynk:mysql:0.0.0.13")
  testRuntime("org.slf4j:slf4j-jdk14:1.7.25")
//  testRuntime("org.slf4j:slf4j-nop:1.7.25")
}

kotlin {
  experimental.coroutines = Coroutines.ENABLE
}

val jarAll by tasks.creating(Jar::class) {
  baseName = "${project.name}-all"
  manifest {
    attributes["Main-Class"] = "info.jdavid.asynk.server.http.FileHandler"
  }
  from(configurations.runtime.map { if (it.isDirectory) it as Any else zipTree(it) })
}

val dokkaJavadoc by tasks.creating(DokkaTask::class) {
  outputFormat = "javadoc"
  includeNonPublic = false
  skipEmptyPackages = true
  impliedPlatforms = mutableListOf("JVM")
  jdkVersion = 8
  outputDirectory = "${buildDir}/javadoc"
}

val sourcesJar by tasks.creating(Jar::class) {
  classifier = "sources"
  from(sourceSets["main"].allSource)
}

val javadocJar by tasks.creating(Jar::class) {
  classifier = "javadoc"
  from("${buildDir}/javadoc")
  dependsOn("javadoc")
}

configure<JavaPluginConvention> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType(KotlinJvmCompile::class.java).all {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events("passed", "skipped", "failed")
  }
}

val javadoc: Javadoc by tasks
val jar: Jar by tasks
jar.apply {
  manifest {
    attributes["Sealed"] = true
  }
}

tasks {
  "javadocJar" {
    dependsOn(javadoc)
  }
  "jar" {
    dependsOn(jarAll)
  }
}

publishing {
  repositories {
    maven {
      url = uri("${buildDir}/repo")
    }
  }
  publications {
    register("mavenJava", MavenPublication::class.java) {
      from(components["java"])
      artifact(sourcesJar)
      artifact(javadocJar)
    }
  }
}

bintray {
  user = "programingjd"
  key = {
    "bintrayApiKey".let { key: String ->
      File("local.properties").readLines().findLast {
        it.startsWith("${key}=")
      }?.substring(key.length + 1)
    }
  }()
  //dryRun = true
  publish = true
  setPublications("mavenJava")
  pkg(delegateClosureOf<BintrayExtension.PackageConfig>{
    repo = "maven"
    name = "${project.group}.${project.name}"
    websiteUrl = "https://github.com/programingjd/asynk_server"
    issueTrackerUrl = "https://github.com/programingjd/asynk_server/issues"
    vcsUrl = "https://github.com/programingjd/asynk_server.git"
    githubRepo = "programingjd/asynk_server"
    githubReleaseNotesFile = "README.md"
    setLicenses("Apache-2.0")
    setLabels("asynk", "server", "http", "java", "kotlin", "async", "coroutines", "suspend")
    publicDownloadNumbers = true
    version(delegateClosureOf<BintrayExtension.VersionConfig> {
      name = "${project.version}"
      mavenCentralSync(delegateClosureOf<BintrayExtension.MavenCentralSyncConfig> {
        sync = false
      })
    })
  })
}

tasks {
  "test" {
    val test = this as Test
    doLast {
      DOMParser().let {
        it.parse(InputSource(FileInputStream(test.reports.html.entryPoint)))
        XPathFactory.newInstance().newXPath().apply {
          val total =
            (
              evaluate("DIV", it.document.getElementById("tests"), XPathConstants.NODE) as Node
            ).textContent.toInt()
          val failed =
            (
              evaluate("DIV", it.document.getElementById("failures"), XPathConstants.NODE) as Node
            ).textContent.toInt()
          val badge = { label: String, text: String, color: String ->
            "https://img.shields.io/badge/_${label}_-${text}-${color}.png?style=flat"
          }
          val color = if (failed == 0) "green" else if (failed < 3) "yellow" else "red"
          File("README.md").apply {
            readLines().mapIndexed { i, line ->
              when (i) {
                0 -> "![jcenter](${badge("jcenter", "${project.version}", "6688ff")}) &#x2003; " +
                     "![jcenter](${badge("Tests", "${total-failed}/${total}", color)})"
                14 -> "[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/asynk/server/${project.version}/server-${project.version}.jar) the latest jar."
                24 -> "  <version>${project.version}</version>"
                37 -> "  compile 'info.jdavid.asynk:server:${project.version}'"
                else -> line
              }
            }.joinToString("\n").let {
              FileWriter(this).apply {
                write(it)
                close()
              }
            }
          }
        }
      }
    }
  }
  "bintrayUpload" {
    dependsOn("check")
  }
  "javadoc" {
    dependsOn("dokkaJavadoc")
  }
}
