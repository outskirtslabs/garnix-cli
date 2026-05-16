{
  lib,
  stdenv,
  makeWrapper,
  babashka,
  git,
}:

stdenv.mkDerivation rec {
  pname = "garnix-cli";
  version = "0.1.0";
  src = ./.;

  nativeBuildInputs = [ makeWrapper ];
  dontBuild = true;

  installPhase = ''
    runHook preInstall

    mkdir -p $out/bin
    cp garnix-cli $out/bin/garnix-cli
    chmod +x $out/bin/garnix-cli
    wrapProgram $out/bin/garnix-cli \
      --prefix PATH : ${
        lib.makeBinPath [
          babashka
          git
        ]
      }

    runHook postInstall
  '';

  meta = with lib; {
    description = "Inspect Garnix build runs from the terminal";
    homepage = "https://github.com/outskirtslabs/garnix-cli";
    license = licenses.eupl12;
    maintainers = with maintainers; [ ramblurr ];
    platforms = babashka.meta.platforms;
    mainProgram = "garnix-cli";
  };
}
