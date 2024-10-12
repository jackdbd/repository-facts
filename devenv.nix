{pkgs, ...}: {
  enterShell = ''
    versions
  '';

  enterTest = ''
    echo "Running tests"
    git --version | grep --color=auto "${pkgs.git.version}"
  '';

  env = {
    # GIT_NOTES_NAMESPACE = "foo"; # custom namespace for git notes
    # GIT_NOTES_REF = "refs/notes/commits"; # default
    GIT_NOTES_REF = "refs/notes/foo"; # custom namespace for git notes
    # GIT_NOTES_REF = "refs/notes/bar"; # custom namespace for git notes
    GREET = "devenv";
  };

  languages = {
    clojure.enable = true;
    nix.enable = true;
  };

  packages = [
    pkgs.babashka
    pkgs.git
    pkgs.nodejs
  ];

  pre-commit.hooks = {
    alejandra.enable = true;
    cljfmt.enable = true;
    deadnix.enable = true;
    shellcheck.enable = true;
    statix.enable = true;
  };

  scripts = {
    versions.exec = ''
      echo "=== Versions ==="
      bb --version
      dot --version
      git --version
      java --version
      echo "Node.js $(node --version)"
      echo "=== === ==="
    '';
  };
}
