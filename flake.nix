{
  description = "KarooFireFly Android dev shell (SDK + JDK 17)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = {
    nixpkgs,
    flake-utils,
    ...
  }:
    flake-utils.lib.eachDefaultSystem (
      system: let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        buildToolsVersion = "35.0.0";
        platformVersion = "35";

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "latest";
          buildToolsVersions = ["34.0.0" buildToolsVersion];
          platformVersions = [platformVersion];
          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
        };

        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
        aapt2 = "${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";
      in {
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.temurin-bin-17
            androidSdk
          ];

          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = "${pkgs.temurin-bin-17}";

          # AGP's bundled aapt2 is a dynamically-linked binary that won't run on
          # NixOS; point Gradle at the nix-provided aapt2 instead.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";
        };
      }
    );
}
