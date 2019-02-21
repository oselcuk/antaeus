plugins {
    kotlin("jvm")
}

dataLibs()

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    compile(project(":pleo-antaeus-models"))
}