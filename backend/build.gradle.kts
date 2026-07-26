plugins {
	java
	jacoco
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.railreserve"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	// Exposes the custom Micrometer metrics for Prometheus scraping at /actuator/prometheus.
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	// Stands in for the external payment provider so the saga's failure paths (timeout, 5xx,
	// decline, duplicate charge) can be tested deterministically over real HTTP. The standalone
	// (shaded) build avoids clashing with the app's own Jetty/servlet stack.
	testImplementation("org.wiremock:wiremock-standalone:3.9.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Only the executable Spring Boot jar is produced (no extra "-plain" jar), so the Docker image can
// copy a single artifact unambiguously.
tasks.named<Jar>("jar") {
	enabled = false
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

// The booking + payment engine is the heart of this project; hold it to a high coverage bar.
// (The mock provider, DTOs, config, and the app entrypoint are excluded as non-logic.)
tasks.jacocoTestCoverageVerification {
	violationRules {
		rule {
			element = "PACKAGE"
			includes = listOf(
				"com.railreserve.booking.service",
				"com.railreserve.booking.lock",
				"com.railreserve.booking.domain",
				"com.railreserve.booking.refund",
			)
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}
