plugins {
    `java-library`
}

group = "org.glavo"
version = "96ccd6d"

dependencies {
    api(libs.monet.fx)
    compileOnlyApi(libs.jetbrains.annotations)
}

tasks.processResources {
    from("LICENSE") { into("META-INF") }
    from("NOTICE") { into("META-INF") }
}

tasks.withType<JavaCompile> {
    options.release = 17
    options.encoding = "UTF-8"
}
