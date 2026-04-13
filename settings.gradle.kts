pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Trust Wallet publishes wallet-core to GitHub Packages, not Maven
        // Central. Authentication is mandatory even for public packages —
        // provide a token via ~/.gradle/gradle.properties (gpr.user /
        // gpr.key) or the GITHUB_USER / GITHUB_TOKEN env vars. The token
        // only needs `read:packages` scope. CI sets it through the
        // repository secrets; locally developers use a personal PAT.
        maven {
            name = "WalletCoreGithubPackages"
            url = uri("https://maven.pkg.github.com/trustwallet/wallet-core")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_USER") ?: ""
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
            // Scope the lookup so public artifacts (AGP, AndroidX, …)
            // still resolve from google() / mavenCentral() first.
            content {
                includeGroup("com.trustwallet")
            }
        }
    }
}

rootProject.name = "okaiwa-android"
include(":app")
