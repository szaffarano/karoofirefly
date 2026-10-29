{
  description = "KarooFireFly Android dev shell";

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

        buildToolsVersions = ["36.0.0"];
        platformVersions = ["37"];

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "latest";
          buildToolsVersions = buildToolsVersions;
          platformVersions = platformVersions;
          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
        };

        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
        aapt2 = "${sdkRoot}/build-tools/${nixpkgs.lib.lists.last buildToolsVersions}/aapt2";

        # Shared environment so every shell uses the same SDK/JDK and the
        # NixOS aapt2 override (AGP's bundled aapt2 won't run on NixOS).
        commonEnv = {
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = "${pkgs.temurin-bin-25}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2} --enable-native-access=ALL-UNNAMED";
        };

        # Android Studio persists its SDK location in its own config and
        # writes it into local.properties on every sync, ignoring
        # $ANDROID_HOME. A stale Nix store path there strands the build, so
        # rewrite those config files to the current $ANDROID_HOME before
        # launching Studio. Self-heals across flake updates.
        studioWrapped = pkgs.writeShellScriptBin "android-studio" ''
          shopt -s nullglob
          for f in "$HOME"/.config/Google/AndroidStudio*/options/android.sdk.path.xml \
                   "$HOME"/.config/Google/AndroidStudio*/options/jdk.table.xml; do
            ${pkgs.gnused}/bin/sed -i -E \
              "s#/nix/store/[a-z0-9]{32}-androidsdk/libexec/android-sdk#$ANDROID_HOME#g" "$f"
          done
          exec ${pkgs.android-studio}/bin/android-studio "$@"
        '';
      in {
        devShells.default = pkgs.mkShell (commonEnv
          // {
            packages = [
              pkgs.temurin-bin-25
              androidSdk
            ];
          });

        # Heavier shell that also provides Android Studio, launched with the
        # same env: nix develop .#studio --command android-studio .
        devShells.studio = pkgs.mkShell (commonEnv
          // {
            packages = [
              pkgs.temurin-bin-25
              studioWrapped
            ];
          });
      }
    );
}
