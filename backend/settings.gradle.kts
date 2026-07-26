plugins {
	// Auto-provisions the Java 21 toolchain (via api.foojay.io) when it isn't already
	// installed, so the build works regardless of the developer's default JDK.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "railreserve"
