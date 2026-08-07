{
  description = "Dev shell for the legacy-custom-sky Fabric mod (Stonecutter multi-version: 1.21.11 needs JDK 21, 26.1/26.2 need JDK 25)";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      forAllSystems = nixpkgs.lib.genAttrs [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; };
        in
        {
          default = pkgs.mkShell {
            # jdk25 runs the Gradle daemon/wrapper itself (and builds the
            # 26.1/26.2 nodes); jdk21 is here explicitly for the 1.21.11 node's
            # own Java toolchain, rather than relying on Gradle's
            # foojay-resolver-convention to auto-download it - keeps the dev
            # shell hermetic and avoids depending on network access being
            # available from inside the Nix sandbox.
            packages = [ pkgs.jdk25 pkgs.jdk21 ];

            # Loom/Gradle need JAVA_HOME to point at a JDK 25 - the host's
            # default `java` (often 8 or 21) is too old to run this project's
            # Gradle wrapper/toolchain. The 1.21.11 node's own compile/run
            # tasks still get JDK 21 via Gradle's toolchain resolution, which
            # discovers jdk21 on PATH independent of JAVA_HOME.
            JAVA_HOME = "${pkgs.jdk25}";

            shellHook = ''
              export PATH="${pkgs.jdk25}/bin:${pkgs.jdk21}/bin:$PATH"
              echo "JDK 25 ready ($(java -version 2>&1 | head -n1))."
              echo "JDK 21 also on PATH for the 1.21.11 Stonecutter node's toolchain."

              # This JDK is dynamically linked against Nix's own glibc, so on
              # a non-NixOS host it has no way to find your system's NVIDIA
              # driver libraries - LWJGL's dlopen() of GLX/EGL then fails
              # with "[LWJGL] Failed to load a library". `nixGL` is the usual
              # fix, but its driver-version auto-detection doesn't understand
              # this driver's version string, so instead: symlink just the
              # NVIDIA/GLVND libraries themselves (not all of /usr/lib - that
              # shadows Nix's own glibc for every tool in this shell,
              # including bash, and breaks everything) into a scratch dir and
              # point LD_LIBRARY_PATH at only that.
              GLLIBDIR="$PWD/.nix-gl-libs"
              mkdir -p "$GLLIBDIR"
              find "$GLLIBDIR" -maxdepth 1 -name '*.so*' -delete
              for f in /usr/lib/libnvidia-*.so* /usr/lib/libGLX_nvidia.so* /usr/lib/libEGL_nvidia.so* \
                       /usr/lib/libGLX.so* /usr/lib/libEGL.so* /usr/lib/libOpenGL.so* /usr/lib/libGL.so*; do
                [ -e "$f" ] && ln -sf "$f" "$GLLIBDIR/$(basename "$f")"
              done
              export LD_LIBRARY_PATH="$GLLIBDIR''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
              export __GLX_VENDOR_LIBRARY_NAME=nvidia
              [ -f /usr/share/glvnd/egl_vendor.d/10_nvidia.json ] && \
                export __EGL_VENDOR_LIBRARY_FILENAMES=/usr/share/glvnd/egl_vendor.d/10_nvidia.json

              echo "Run: ./gradlew runClient"
            '';
          };
        });
    };
}
