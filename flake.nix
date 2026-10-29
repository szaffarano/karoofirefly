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

        buildToolsVersion = "37.0.0";
        platformVersion = "37";

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "latest";
          buildToolsVersions = ["36.0.0" buildToolsVersion];
          platformVersions = [platformVersion];
          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
        };

        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
        aapt2 = "${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

        # Shared environment so every shell uses the same SDK/JDK and the
        # NixOS aapt2 override (AGP's bundled aapt2 won't run on NixOS).
        commonEnv = {
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = "${pkgs.temurin-bin-17}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";
        };
      in {
        devShells.default = pkgs.mkShell (commonEnv
          // {
            packages = [
              pkgs.temurin-bin-17
              androidSdk
            ];
          });

        # Heavier shell that also provides Android Studio, launched with the
        # same env: nix develop .#studio --command android-studio .
        devShells.studio = pkgs.mkShell (commonEnv
          // {
            packages = [
              pkgs.temurin-bin-17
              androidSdk
              pkgs.android-studio
            ];
          });
      }
    );
}
