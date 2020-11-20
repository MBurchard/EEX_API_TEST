import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("com.github.ben-manes.versions") version Versions.BEN_MANES_VERSIONS
	id("org.springframework.boot") version Versions.SPRING_BOOT
	id("io.spring.dependency-management") version Versions.SPRING_DEPENDENCY_MANAGEMENT
	kotlin("jvm") version Versions.KOTLIN
	kotlin("plugin.spring") version Versions.KOTLIN
	kotlin("kapt") version Versions.KOTLIN
	`java-library`
	idea
}

val javaVersion = JavaVersion.VERSION_11

group = "de.mbur"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = javaVersion
java.targetCompatibility = javaVersion

repositories {
	mavenCentral()
}

idea {
	project {
		jdkName = javaVersion.toString()
		languageLevel = IdeaLanguageLevel(javaVersion)
	}
	module {
		isDownloadJavadoc = true
		isDownloadSources = true
	}
}

//Comment next to lines before initial Import into IntelliJ
//val currentJavaVersion = JavaVersion.current()
//if (currentJavaVersion != javaVersion) throw GradleException("This build must be run with java $javaVersion. Current version is $currentJavaVersion")

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

dependencies {
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	kapt("org.springframework.boot:spring-boot-configuration-processor")

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation(group = "io.github.microutils", name = "kotlin-logging-jvm", version = Versions.KOTLIN_LOGGING)
	implementation(group = "org.apache.commons", name = "commons-csv", version = Versions.APACHE_CSV)

	// Not needed for CommandLineRunner
	// developmentOnly("org.springframework.boot:spring-boot-devtools")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

fun isNonStable(version: String): Boolean {
	val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
	val regex = "^[0-9,.v-]+(-r)?$".toRegex()
	val isStable = stableKeyword || regex.matches(version)
	return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
	gradleReleaseChannel = "current"

	rejectVersionIf {
		isNonStable(candidate.version)
	}
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = javaVersion.toString()
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Wrapper>("wrapper") {
	distributionType = Wrapper.DistributionType.ALL
	gradleVersion = "6.7.1"
}
